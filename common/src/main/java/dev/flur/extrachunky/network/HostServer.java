package dev.flur.extrachunky.network;

import dev.flur.extrachunky.platform.ExtraChunkyConfig;
import dev.flur.extrachunky.platform.ExtraChunkyLogger;
import org.popcraft.chunky.Selection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Host server that accepts worker connections and coordinates chunk generation.
 * Manages worker registration, assignment distribution, and progress aggregation.
 */
public class HostServer {
    private static final int SOCKET_TIMEOUT_MS = 1000;
    private static final long STALE_THRESHOLD_MS = 60_000;

    private final ExtraChunkyLogger logger;
    private final ExtraChunkyConfig config;
    private final int port;

    private final Map<Integer, WorkerConnection> workers = new ConcurrentHashMap<>();
    private final AtomicInteger nextWorkerId = new AtomicInteger(1);

    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private volatile boolean running = false;
    private volatile boolean generationActive = false;

    // Current selection for generation (set when starting)
    private volatile Selection currentSelection;

    // Host's own progress (if participating as worker 0)
    private volatile long hostChunksGenerated = 0;
    private volatile long hostTotalChunks = 0;
    private volatile float hostPercentComplete = 0;
    private volatile float hostChunksPerSecond = 0;
    private volatile long hostLastUpdate = 0;

    public HostServer(ExtraChunkyLogger logger, ExtraChunkyConfig config) {
        this.logger = logger;
        this.config = config;
        this.port = config.getHostPort();
    }

    /**
     * Starts the host server.
     */
    public void start() {
        if (running) {
            return;
        }

        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(SOCKET_TIMEOUT_MS);
            running = true;
            executorService = Executors.newCachedThreadPool();

            executorService.submit(this::acceptLoop);

            logger.info("Host server started on port " + port);
        } catch (IOException e) {
            logger.severe("Failed to start host server on port " + port, e);
        }
    }

    /**
     * Stops the host server.
     */
    public void stop() {
        running = false;

        // Close all worker connections
        for (WorkerConnection worker : workers.values()) {
            worker.close();
        }
        workers.clear();

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.warning("Error closing server socket");
            }
        }

        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        logger.info("Host server stopped");
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                executorService.submit(() -> handleNewConnection(clientSocket));
            } catch (SocketTimeoutException e) {
                // Expected timeout, continue loop to check running flag
            } catch (IOException e) {
                if (running) {
                    logger.warning("Error accepting connection");
                }
            }
        }
    }

    private void handleNewConnection(Socket clientSocket) {
        try {
            // Read the first message (should be REGISTER)
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            String line = reader.readLine();

            if (line == null) {
                clientSocket.close();
                return;
            }

            NetworkMessage message = NetworkMessage.fromJson(line);
            if (message.getType() != NetworkMessage.Type.REGISTER) {
                logger.warning("Expected REGISTER message, got: " + message.getType());
                clientSocket.close();
                return;
            }

            String hostname = message.getString("hostname");
            int assignedId = nextWorkerId.getAndIncrement();

            WorkerConnection connection = new WorkerConnection(clientSocket, logger, assignedId, hostname);
            connection.setDisconnectHandler(() -> handleWorkerDisconnect(assignedId));

            workers.put(assignedId, connection);
            connection.start();

            logger.info("Worker registered: ID=" + assignedId + ", hostname=" + hostname +
                    ", address=" + clientSocket.getInetAddress().getHostAddress());

            // Send REGISTERED response
            int totalWorkers = getTotalWorkerCount();
            connection.send(NetworkMessage.registered(assignedId, totalWorkers));

            // Send assignment and redistribute to all workers
            redistributeAssignments();

        } catch (IOException e) {
            logger.warning("Error handling new connection");
            try {
                clientSocket.close();
            } catch (IOException ex) {
                // Ignore
            }
        }
    }

    private void handleWorkerDisconnect(int workerId) {
        WorkerConnection removed = workers.remove(workerId);
        if (removed != null) {
            logger.info("Worker disconnected: ID=" + workerId + ", hostname=" + removed.getHostname());
            redistributeAssignments();
        }
    }

    /**
     * Redistributes chunk assignments to all connected workers.
     * Called when workers join or leave, but only if we have a selection set.
     */
    public void redistributeAssignments() {
        // Only redistribute if we have a selection (set when generation starts)
        if (currentSelection == null) {
            logger.info("Workers connected: " + workers.size() + " (assignments will be sent when generation starts)");
            return;
        }

        int totalWorkers = getTotalWorkerCount();
        if (totalWorkers == 0) {
            return;
        }

        NetworkMessage.Type assignmentType = generationActive ?
                NetworkMessage.Type.REASSIGN : NetworkMessage.Type.ASSIGNMENT;

        // If host participates, it gets instance ID 0
        int instanceOffset = config.isHostParticipates() ? 1 : 0;

        String world = currentSelection.world().getName();
        double centerX = currentSelection.centerX();
        double centerZ = currentSelection.centerZ();
        double radius = currentSelection.radiusX();
        String shape = currentSelection.shape();

        // Send assignment to each worker
        for (WorkerConnection worker : workers.values()) {
            int instanceId = instanceOffset + getWorkerInstanceIndex(worker.getAssignedId());
            NetworkMessage assignment = assignmentType == NetworkMessage.Type.ASSIGNMENT ?
                    NetworkMessage.assignment(instanceId, totalWorkers, world, centerX, centerZ, radius, shape) :
                    NetworkMessage.reassign(instanceId, totalWorkers, world, centerX, centerZ, radius, shape);
            worker.send(assignment);
        }

        logger.info("Redistributed assignments: " + totalWorkers + " total workers" +
                (config.isHostParticipates() ? " (including host)" : ""));
    }

    /**
     * Gets the instance index for a worker based on the current worker list.
     * Workers are indexed by their position in the sorted worker ID list.
     */
    private int getWorkerInstanceIndex(int workerId) {
        List<Integer> sortedIds = new ArrayList<>(workers.keySet());
        Collections.sort(sortedIds);
        return sortedIds.indexOf(workerId);
    }

    /**
     * Gets the total number of workers including the host if it participates.
     */
    public int getTotalWorkerCount() {
        int count = workers.size();
        if (config.isHostParticipates()) {
            count++;
        }
        return count;
    }

    /**
     * Gets the host's instance ID (0 if participating, -1 if not).
     */
    public int getHostInstanceId() {
        return config.isHostParticipates() ? 0 : -1;
    }

    /**
     * Broadcasts START command to all workers with the given selection.
     *
     * @param selection The Chunky selection to use for generation
     */
    public void broadcastStart(Selection selection) {
        this.currentSelection = selection;
        generationActive = true;

        // First, send/update assignments with the current selection
        redistributeAssignments();

        // Then broadcast START to all workers
        NetworkMessage startMsg = NetworkMessage.start();
        for (WorkerConnection worker : workers.values()) {
            worker.send(startMsg);
        }
        logger.info("Broadcast START to " + workers.size() + " workers");
    }

    /**
     * Broadcasts STOP command to all workers.
     */
    public void broadcastStop() {
        generationActive = false;
        NetworkMessage stopMsg = NetworkMessage.stop();
        for (WorkerConnection worker : workers.values()) {
            worker.send(stopMsg);
        }
        logger.info("Broadcast STOP to " + workers.size() + " workers");
    }

    /**
     * Updates the host's own progress (when host participates as worker).
     */
    public void updateHostProgress(long chunksGenerated, long totalChunks, float percentComplete, float chunksPerSecond) {
        this.hostChunksGenerated = chunksGenerated;
        this.hostTotalChunks = totalChunks;
        this.hostPercentComplete = percentComplete;
        this.hostChunksPerSecond = chunksPerSecond;
        this.hostLastUpdate = System.currentTimeMillis();
    }

    /**
     * Gets aggregated progress from all workers and the host.
     */
    public AggregatedProgress getAggregatedProgress() {
        List<WorkerProgress> allProgress = new ArrayList<>();
        long now = System.currentTimeMillis();

        // Add host progress if participating
        if (config.isHostParticipates() && hostLastUpdate > 0) {
            boolean active = (now - hostLastUpdate) < STALE_THRESHOLD_MS;
            allProgress.add(new WorkerProgress(0, "host (local)", hostChunksGenerated,
                    hostTotalChunks, hostPercentComplete, hostChunksPerSecond, hostLastUpdate, active,
                    false, 0, 0, false, null)); // Host doesn't transfer to itself
        }

        // Add worker progress
        for (WorkerConnection worker : workers.values()) {
            boolean active = worker.isConnected() && (now - worker.getLastProgressUpdate()) < STALE_THRESHOLD_MS;
            allProgress.add(new WorkerProgress(
                    worker.getAssignedId(),
                    worker.getHostname(),
                    worker.getChunksGenerated(),
                    worker.getTotalChunks(),
                    worker.getPercentComplete(),
                    worker.getChunksPerSecond(),
                    worker.getLastProgressUpdate(),
                    active,
                    worker.isGenerationComplete(),
                    worker.getTransferCompleted(),
                    worker.getTransferTotal(),
                    worker.isTransferComplete(),
                    worker.getTransferError()
            ));
        }

        // Calculate totals
        long totalGenerated = 0;
        long totalChunks = 0;
        int activeCount = 0;

        for (WorkerProgress progress : allProgress) {
            totalGenerated += progress.chunksGenerated();
            totalChunks += progress.totalChunks();
            if (progress.active()) {
                activeCount++;
            }
        }

        float overallPercent = totalChunks > 0 ? (float) totalGenerated / totalChunks * 100f : 0f;

        return new AggregatedProgress(totalGenerated, totalChunks, overallPercent, activeCount, allProgress);
    }

    /**
     * Gets a read-only view of connected workers.
     */
    public Collection<WorkerConnection> getWorkers() {
        return Collections.unmodifiableCollection(workers.values());
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isGenerationActive() {
        return generationActive;
    }

    public record WorkerProgress(
            int instanceId,
            String hostname,
            long chunksGenerated,
            long totalChunks,
            float percentComplete,
            float chunksPerSecond,
            long lastUpdate,
            boolean active,
            // Transfer state
            boolean generationComplete,
            int transferCompleted,
            int transferTotal,
            boolean transferComplete,
            String transferError
    ) {}

    public record AggregatedProgress(
            long totalChunksGenerated,
            long totalChunks,
            float overallPercent,
            int activeWorkers,
            List<WorkerProgress> workers
    ) {}
}
