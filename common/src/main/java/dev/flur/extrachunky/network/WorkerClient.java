package dev.flur.extrachunky.network;

import dev.flur.extrachunky.platform.ExtraChunkyLogger;
import dev.flur.extrachunky.platform.ExtraChunkyScheduler;
import dev.flur.extrachunky.platform.ExtraChunkyTask;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Client that connects to a host server and receives chunk assignments.
 * Manages registration, auto-reconnection, and progress reporting.
 */
public class WorkerClient {
    private static final int RECONNECT_INTERVAL_SECONDS = 10;
    private static final int SOCKET_TIMEOUT_MS = 30000;

    private final ExtraChunkyLogger logger;
    private final ExtraChunkyScheduler scheduler;
    private final String hostAddress;
    private final int hostPort;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private final BlockingQueue<String> outgoingMessages = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean connected = new AtomicBoolean(false);

    private Thread readerThread;
    private Thread writerThread;
    private ExtraChunkyTask reconnectTask;

    // Assigned by host
    private volatile int assignedId = -1;
    private volatile int totalWorkers = 0;

    // Current assignment from host
    private volatile ChunkAssignment currentAssignment;

    // Handlers
    private Consumer<NetworkMessage> startHandler;
    private Consumer<NetworkMessage> stopHandler;
    private Consumer<ChunkAssignment> assignmentHandler;
    private Runnable disconnectHandler;
    private Runnable connectedHandler;

    public WorkerClient(ExtraChunkyLogger logger, ExtraChunkyScheduler scheduler, String hostAddress, int hostPort) {
        this.logger = logger;
        this.scheduler = scheduler;
        this.hostAddress = hostAddress;
        this.hostPort = hostPort;
    }

    /**
     * Sets the handler called when START command is received.
     */
    public void setStartHandler(Consumer<NetworkMessage> handler) {
        this.startHandler = handler;
    }

    /**
     * Sets the handler called when STOP command is received.
     */
    public void setStopHandler(Consumer<NetworkMessage> handler) {
        this.stopHandler = handler;
    }

    /**
     * Sets the handler called when assignment is received or changed.
     */
    public void setAssignmentHandler(Consumer<ChunkAssignment> handler) {
        this.assignmentHandler = handler;
    }

    /**
     * Sets the handler called when disconnected from host.
     */
    public void setDisconnectHandler(Runnable handler) {
        this.disconnectHandler = handler;
    }

    /**
     * Sets the handler called when successfully connected to host.
     */
    public void setConnectedHandler(Runnable handler) {
        this.connectedHandler = handler;
    }

    /**
     * Connects to the host server and registers.
     *
     * @return true if connection was successful
     */
    public boolean connect() {
        if (running.get()) {
            return connected.get();
        }

        running.set(true);

        try {
            socket = new Socket(hostAddress, hostPort);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            socket.setKeepAlive(true);

            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), false);

            // Send REGISTER message
            String hostname = getHostname();
            NetworkMessage registerMsg = NetworkMessage.register(hostname);
            writer.println(registerMsg.toJson());
            writer.flush();

            if (writer.checkError()) {
                throw new IOException("Failed to send REGISTER message");
            }

            // Start reader and writer threads
            readerThread = new Thread(this::readLoop, "WorkerClient-Reader");
            readerThread.setDaemon(true);
            readerThread.start();

            writerThread = new Thread(this::writeLoop, "WorkerClient-Writer");
            writerThread.setDaemon(true);
            writerThread.start();

            connected.set(true);
            logger.info("Connected to host at " + hostAddress + ":" + hostPort);

            if (connectedHandler != null) {
                connectedHandler.run();
            }

            return true;

        } catch (IOException e) {
            logger.warning("Failed to connect to host at " + hostAddress + ":" + hostPort);
            closeSocket();
            scheduleReconnect();
            return false;
        }
    }

    /**
     * Disconnects from the host server.
     */
    public void disconnect() {
        running.set(false);

        if (reconnectTask != null) {
            reconnectTask.cancel();
            reconnectTask = null;
        }

        closeSocket();

        if (readerThread != null) {
            readerThread.interrupt();
        }
        if (writerThread != null) {
            writerThread.interrupt();
        }

        assignedId = -1;
        totalWorkers = 0;
        currentAssignment = null;

        logger.info("Disconnected from host");
    }

    private void readLoop() {
        try {
            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                try {
                    NetworkMessage message = NetworkMessage.fromJson(line);
                    handleMessage(message);
                } catch (Exception e) {
                    logger.warning("Failed to parse message from host: " + line);
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                logger.warning("Connection lost to host");
            }
        } finally {
            handleDisconnect();
        }
    }

    private void writeLoop() {
        try {
            while (running.get()) {
                String message = outgoingMessages.take();
                writer.println(message);
                writer.flush();
                if (writer.checkError()) {
                    logger.warning("Error sending to host, connection may be lost");
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleMessage(NetworkMessage message) {
        switch (message.getType()) {
            case REGISTERED -> {
                assignedId = message.getInt("assignedId");
                totalWorkers = message.getInt("totalWorkers");
                logger.info("Registered with host: ID=" + assignedId + ", totalWorkers=" + totalWorkers);
            }
            case ASSIGNMENT, REASSIGN -> {
                ChunkAssignment assignment = new ChunkAssignment(
                        message.getInt("instanceId"),
                        message.getInt("totalInstances"),
                        message.getString("world"),
                        message.getDouble("centerX"),
                        message.getDouble("centerZ"),
                        message.getDouble("radius"),
                        message.getString("shape")
                );
                currentAssignment = assignment;
                logger.info("Received assignment: instance " + assignment.instanceId() +
                        "/" + assignment.totalInstances());

                if (assignmentHandler != null) {
                    assignmentHandler.accept(assignment);
                }
            }
            case START -> {
                logger.info("Received START command from host");
                if (startHandler != null) {
                    startHandler.accept(message);
                }
            }
            case STOP -> {
                logger.info("Received STOP command from host");
                if (stopHandler != null) {
                    stopHandler.accept(message);
                }
            }
            default -> logger.warning("Unexpected message type from host: " + message.getType());
        }
    }

    private void handleDisconnect() {
        boolean wasConnected = connected.getAndSet(false);
        closeSocket();

        if (wasConnected) {
            if (disconnectHandler != null) {
                disconnectHandler.run();
            }

            if (running.get()) {
                scheduleReconnect();
            }
        }
    }

    private void scheduleReconnect() {
        if (!running.get() || reconnectTask != null) {
            return;
        }

        logger.info("Will attempt to reconnect in " + RECONNECT_INTERVAL_SECONDS + " seconds...");

        reconnectTask = scheduler.runTaskLaterAsync(() -> {
            reconnectTask = null;
            if (running.get() && !connected.get()) {
                logger.info("Attempting to reconnect to host...");
                connect();
            }
        }, RECONNECT_INTERVAL_SECONDS * 20L);
    }

    private void closeSocket() {
        if (writer != null) {
            writer.close();
            writer = null;
        }
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                // Ignore
            }
            reader = null;
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore
            }
            socket = null;
        }
    }

    /**
     * Sends a progress update to the host.
     */
    public void sendProgress(long chunksGenerated, long totalChunks, float percentComplete, float chunksPerSecond) {
        if (!connected.get() || currentAssignment == null) {
            return;
        }

        NetworkMessage progress = NetworkMessage.progress(
                assignedId,
                currentAssignment.world(),
                chunksGenerated,
                totalChunks,
                percentComplete,
                chunksPerSecond,
                getHostname()
        );
        outgoingMessages.offer(progress.toJson());
    }

    /**
     * Notifies the host that chunk generation is complete and transfer is starting.
     */
    public void sendGenerationComplete(int regionCount) {
        if (!connected.get() || currentAssignment == null) {
            return;
        }

        NetworkMessage msg = NetworkMessage.generationComplete(
                assignedId,
                currentAssignment.world(),
                regionCount
        );
        outgoingMessages.offer(msg.toJson());
    }

    /**
     * Sends a transfer progress update to the host.
     */
    public void sendTransferProgress(int completed, int total, long bytesTransferred, long totalBytes) {
        if (!connected.get()) {
            return;
        }

        NetworkMessage msg = NetworkMessage.transferProgress(
                assignedId,
                completed,
                total,
                bytesTransferred,
                totalBytes
        );
        outgoingMessages.offer(msg.toJson());
    }

    /**
     * Notifies the host that all transfers are complete.
     */
    public void sendTransferComplete(int regionCount) {
        if (!connected.get() || currentAssignment == null) {
            return;
        }

        NetworkMessage msg = NetworkMessage.transferComplete(
                assignedId,
                currentAssignment.world(),
                regionCount
        );
        outgoingMessages.offer(msg.toJson());
    }

    /**
     * Notifies the host that transfers have failed.
     */
    public void sendTransferFailed(String error, int failedCount) {
        if (!connected.get() || currentAssignment == null) {
            return;
        }

        NetworkMessage msg = NetworkMessage.transferFailed(
                assignedId,
                currentAssignment.world(),
                error,
                failedCount
        );
        outgoingMessages.offer(msg.toJson());
    }

    private String getHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    public boolean isConnected() {
        return connected.get();
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getAssignedId() {
        return assignedId;
    }

    public int getTotalWorkers() {
        return totalWorkers;
    }

    public ChunkAssignment getCurrentAssignment() {
        return currentAssignment;
    }

    public String getHostAddress() {
        return hostAddress;
    }

    public int getHostPort() {
        return hostPort;
    }

    public record ChunkAssignment(
            int instanceId,
            int totalInstances,
            String world,
            double centerX,
            double centerZ,
            double radius,
            String shape
    ) {}
}
