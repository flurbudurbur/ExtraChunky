package dev.flur.extrachunky.network;

import dev.flur.extrachunky.platform.ExtraChunkyLogger;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Represents a connected worker from the host's perspective.
 * Manages bidirectional communication with a single worker.
 */
public class WorkerConnection {
    private final Socket socket;
    private final ExtraChunkyLogger logger;
    private final int assignedId;
    private final String hostname;
    private final BufferedReader reader;
    private final PrintWriter writer;
    private final BlockingQueue<String> outgoingMessages = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(true);

    private Thread readerThread;
    private Thread writerThread;

    private volatile long lastProgressUpdate = System.currentTimeMillis();
    private volatile long chunksGenerated = 0;
    private volatile long totalChunks = 0;
    private volatile float percentComplete = 0;
    private volatile float chunksPerSecond = 0;
    private volatile String world = "";

    // Transfer tracking
    private volatile boolean generationComplete = false;
    private volatile int transferRegionCount = 0;
    private volatile int transferCompleted = 0;
    private volatile int transferTotal = 0;
    private volatile long transferBytesTransferred = 0;
    private volatile long transferTotalBytes = 0;
    private volatile boolean transferComplete = false;
    private volatile String transferError = null;

    private Consumer<NetworkMessage> messageHandler;
    private Runnable disconnectHandler;

    public WorkerConnection(Socket socket, ExtraChunkyLogger logger, int assignedId, String hostname) throws IOException {
        this.socket = socket;
        this.logger = logger;
        this.assignedId = assignedId;
        this.hostname = hostname;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), false);
    }

    /**
     * Sets the handler for incoming messages from this worker.
     */
    public void setMessageHandler(Consumer<NetworkMessage> handler) {
        this.messageHandler = handler;
    }

    /**
     * Sets the handler called when this worker disconnects.
     */
    public void setDisconnectHandler(Runnable handler) {
        this.disconnectHandler = handler;
    }

    /**
     * Starts the reader and writer threads for this connection.
     */
    public void start() {
        readerThread = new Thread(this::readLoop, "WorkerConnection-Reader-" + assignedId);
        readerThread.setDaemon(true);
        readerThread.start();

        writerThread = new Thread(this::writeLoop, "WorkerConnection-Writer-" + assignedId);
        writerThread.setDaemon(true);
        writerThread.start();
    }

    private void readLoop() {
        try {
            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                try {
                    NetworkMessage message = NetworkMessage.fromJson(line);
                    handleMessage(message);
                } catch (Exception e) {
                    logger.warning("Failed to parse message from worker " + assignedId + ": " + line);
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                logger.warning("Connection lost to worker " + assignedId);
            }
        } finally {
            close();
        }
    }

    private void writeLoop() {
        try {
            while (running.get()) {
                String message = outgoingMessages.take();
                writer.println(message);
                writer.flush();
                if (writer.checkError()) {
                    logger.warning("Error sending to worker " + assignedId + ", closing connection");
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            close();
        }
    }

    private void handleMessage(NetworkMessage message) {
        switch (message.getType()) {
            case PROGRESS -> {
                // Update progress tracking
                this.chunksGenerated = message.getLong("chunksGenerated");
                this.totalChunks = message.getLong("totalChunks");
                this.percentComplete = message.getFloat("percentComplete");
                this.chunksPerSecond = message.getFloat("chunksPerSecond");
                this.world = message.getString("world");
                this.lastProgressUpdate = System.currentTimeMillis();
            }
            case GENERATION_COMPLETE -> {
                this.generationComplete = true;
                this.transferRegionCount = message.getInt("regionCount");
                this.transferTotal = this.transferRegionCount;
                this.lastProgressUpdate = System.currentTimeMillis();
            }
            case TRANSFER_PROGRESS -> {
                this.transferCompleted = message.getInt("completed");
                this.transferTotal = message.getInt("total");
                this.transferBytesTransferred = message.getLong("bytesTransferred");
                this.transferTotalBytes = message.getLong("totalBytes");
                this.lastProgressUpdate = System.currentTimeMillis();
            }
            case TRANSFER_COMPLETE -> {
                this.transferComplete = true;
                this.transferCompleted = this.transferTotal;
                this.lastProgressUpdate = System.currentTimeMillis();
            }
            case TRANSFER_FAILED -> {
                this.transferError = message.getString("error");
                this.lastProgressUpdate = System.currentTimeMillis();
            }
            default -> {}
        }

        if (messageHandler != null) {
            messageHandler.accept(message);
        }
    }

    /**
     * Sends a message to this worker.
     */
    public void send(NetworkMessage message) {
        if (running.get()) {
            outgoingMessages.offer(message.toJson());
        }
    }

    /**
     * Closes this connection.
     */
    public void close() {
        if (running.compareAndSet(true, false)) {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore close errors
            }

            if (readerThread != null) {
                readerThread.interrupt();
            }
            if (writerThread != null) {
                writerThread.interrupt();
            }

            if (disconnectHandler != null) {
                disconnectHandler.run();
            }
        }
    }

    public boolean isConnected() {
        return running.get() && socket.isConnected() && !socket.isClosed();
    }

    public int getAssignedId() {
        return assignedId;
    }

    public String getHostname() {
        return hostname;
    }

    public long getLastProgressUpdate() {
        return lastProgressUpdate;
    }

    public long getChunksGenerated() {
        return chunksGenerated;
    }

    public long getTotalChunks() {
        return totalChunks;
    }

    public float getPercentComplete() {
        return percentComplete;
    }

    public float getChunksPerSecond() {
        return chunksPerSecond;
    }

    public String getWorld() {
        return world;
    }

    public String getRemoteAddress() {
        return socket.getInetAddress().getHostAddress();
    }

    // Transfer state getters

    public boolean isGenerationComplete() {
        return generationComplete;
    }

    public int getTransferRegionCount() {
        return transferRegionCount;
    }

    public int getTransferCompleted() {
        return transferCompleted;
    }

    public int getTransferTotal() {
        return transferTotal;
    }

    public long getTransferBytesTransferred() {
        return transferBytesTransferred;
    }

    public long getTransferTotalBytes() {
        return transferTotalBytes;
    }

    public boolean isTransferComplete() {
        return transferComplete;
    }

    public String getTransferError() {
        return transferError;
    }

    public float getTransferPercentComplete() {
        if (transferTotal <= 0) return 0;
        return (float) transferCompleted / transferTotal * 100;
    }
}
