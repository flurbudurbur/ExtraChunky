package dev.flur.extrachunky.transfer;

import dev.flur.extrachunky.platform.ExtraChunkyLogger;
import dev.flur.extrachunky.platform.ExtraChunkyScheduler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Manages the complete transfer workflow for a worker:
 * 1. Identifies which regions were generated (from assignment)
 * 2. Compresses MCA files with ZSTD
 * 3. Uploads via SFTP
 * 4. Reports progress
 * 5. Handles retries on failure
 */
public class RegionTransferManager {
    private static final long RETRY_BASE_DELAY_MS = 1000;
    private static final String STAGING_DIR = "transfer-staging";

    private final ExtraChunkyScheduler scheduler;
    private final ExtraChunkyLogger logger;
    private final SshConfig sshConfig;
    private final Path dataDirectory;
    private final Path worldPath;
    private final TransferQueue queue;
    private final RegionFileCompressor compressor;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private Consumer<TransferSummary> progressCallback;

    /**
     * Creates a new transfer manager.
     *
     * @param scheduler     Scheduler for async tasks
     * @param logger        Logger for status messages
     * @param sshConfig     SSH configuration
     * @param dataDirectory Plugin data directory
     * @param worldPath     Path to the world folder
     */
    public RegionTransferManager(
            ExtraChunkyScheduler scheduler,
            ExtraChunkyLogger logger,
            SshConfig sshConfig,
            Path dataDirectory,
            Path worldPath
    ) {
        this.scheduler = scheduler;
        this.logger = logger;
        this.sshConfig = sshConfig;
        this.dataDirectory = dataDirectory;
        this.worldPath = worldPath;
        this.queue = new TransferQueue(dataDirectory, logger);
        this.compressor = new RegionFileCompressor(logger, sshConfig.compressionLevel());
    }

    /**
     * Loads any pending transfers from disk.
     */
    public void loadQueue() {
        queue.load();
    }

    /**
     * Sets the callback for progress updates.
     */
    public void setProgressCallback(Consumer<TransferSummary> callback) {
        this.progressCallback = callback;
    }

    /**
     * Called when generation completes to queue regions for transfer.
     *
     * @param worldName       Name of the generated world
     * @param assignedRegions Set of regions that were assigned to this worker
     */
    public void onGenerationComplete(String worldName, Set<RegionCoord> assignedRegions) {
        if (!sshConfig.enabled()) {
            logger.info("SFTP transfers disabled, skipping transfer");
            return;
        }

        logger.info("Generation complete. Queuing " + assignedRegions.size() + " regions for transfer...");

        queue.setWorldName(worldName);
        queue.addRegions(assignedRegions);
        queue.save();

        if (sshConfig.autoTransfer()) {
            startTransferAll();
        } else {
            logger.info("Auto-transfer disabled. Run /extrachunky transfer start to begin.");
        }
    }

    /**
     * Starts transferring all pending regions.
     */
    public void startTransferAll() {
        if (!sshConfig.enabled()) {
            logger.warning("SFTP transfers are not enabled in config");
            return;
        }

        if (running.getAndSet(true)) {
            logger.warning("Transfer already in progress");
            return;
        }

        cancelled.set(false);
        logger.info("Starting region file transfers...");

        scheduler.runTaskAsync(this::processQueue);
    }

    /**
     * Cancels any running transfers.
     */
    public void cancel() {
        if (running.get()) {
            cancelled.set(true);
            logger.info("Cancelling transfers...");
        }
    }

    /**
     * Retries all failed transfers.
     *
     * @return Number of transfers queued for retry
     */
    public int retryFailed() {
        int count = queue.retryFailed(sshConfig.retryCount());
        if (count > 0) {
            logger.info("Retrying " + count + " failed transfers");
            if (!running.get()) {
                startTransferAll();
            }
        }
        return count;
    }

    /**
     * Gets the current transfer status.
     */
    public TransferSummary getStatus() {
        return queue.getSummary();
    }

    /**
     * Checks if transfers are currently running.
     */
    public boolean isRunning() {
        return running.get();
    }

    private void processQueue() {
        Path stagingDir = dataDirectory.resolve(STAGING_DIR);

        try (SftpTransferClient client = new SftpTransferClient(sshConfig, logger)) {
            client.connect();

            RegionCoord next;
            while (!cancelled.get() && (next = queue.getNextPending()) != null) {
                processRegion(next, client, stagingDir);
                reportProgress();
            }

        } catch (IOException e) {
            logger.severe("Transfer failed: " + e.getMessage());
        } finally {
            running.set(false);
            cleanupStagingDir(stagingDir);

            TransferSummary summary = queue.getSummary();
            if (summary.isComplete()) {
                logger.info("All transfers complete: " + summary.completed() + " regions transferred");
                if (summary.failed() > 0) {
                    logger.warning(summary.failed() + " transfers failed. Use /extrachunky transfer retry to retry.");
                }
            } else if (cancelled.get()) {
                logger.info("Transfers cancelled. " + summary.pending() + " regions remaining.");
            }
        }
    }

    private void processRegion(RegionCoord region, SftpTransferClient client, Path stagingDir) {
        TransferState state = queue.getState(region);
        if (state == null) {
            state = TransferState.pending(region);
        }

        // Find the region file
        Path regionFile = worldPath.resolve(region.toRelativePath());
        if (!Files.exists(regionFile)) {
            logger.warning("Region file not found: " + regionFile);
            queue.updateState(state.failed("File not found"));
            return;
        }

        try {
            // Check file isn't being written
            if (!isFileSafe(regionFile)) {
                logger.warning("Region file may be in use, skipping: " + region);
                queue.updateState(state.failed("File in use"));
                return;
            }

            // Compress
            queue.updateState(state.compressing());
            Path compressedFile = compressor.compress(regionFile, stagingDir);

            // Upload
            long compressedSize = Files.size(compressedFile);
            queue.updateState(state.uploading(compressedSize));

            String remotePath = buildRemotePath(region);
            TransferResult result = client.uploadFile(compressedFile, remotePath,
                    (transferred, total) -> {
                        TransferState current = queue.getState(region);
                        if (current != null) {
                            queue.updateState(current.withProgress(transferred));
                        }
                    });

            // Clean up compressed file
            Files.deleteIfExists(compressedFile);

            if (result.success()) {
                queue.updateState(state.completed());
                logger.info("Transferred " + region + " - " + result.getSummary());
            } else {
                handleFailure(state, result.errorMessage());
            }

        } catch (IOException e) {
            handleFailure(state, e.getMessage());
        }
    }

    private void handleFailure(TransferState state, String error) {
        int maxAttempts = sshConfig.retryCount();

        if (state.attemptCount() < maxAttempts) {
            // Schedule retry with exponential backoff
            long delay = RETRY_BASE_DELAY_MS * (1L << state.attemptCount());
            logger.warning("Transfer failed for " + state.region() + ": " + error +
                    ". Retrying in " + delay + "ms (attempt " + (state.attemptCount() + 1) + "/" + maxAttempts + ")");

            TransferState retryState = state.retry();
            queue.updateState(retryState);
        } else {
            logger.severe("Transfer failed for " + state.region() + " after " + maxAttempts + " attempts: " + error);
            queue.updateState(state.failed(error));
        }
    }

    private String buildRemotePath(RegionCoord region) {
        String worldName = queue.getWorldName();
        String basePath = sshConfig.getRemotePathForWorld(worldName != null ? worldName : "world");

        // Include dimension folder structure
        String relativePath = region.toRelativePath();

        // Ensure proper path joining
        if (!basePath.endsWith("/")) {
            basePath += "/";
        }

        // Add .zst extension for compressed files
        return basePath + relativePath + ".zst";
    }

    private boolean isFileSafe(Path file) {
        try {
            // Check file size twice with delay
            long size1 = Files.size(file);
            Thread.sleep(500);
            long size2 = Files.size(file);
            return size1 == size2;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private void cleanupStagingDir(Path stagingDir) {
        try {
            if (Files.exists(stagingDir)) {
                Files.list(stagingDir).forEach(file -> {
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException ignored) {
                    }
                });
            }
        } catch (IOException ignored) {
        }
    }

    private void reportProgress() {
        if (progressCallback != null) {
            progressCallback.accept(queue.getSummary());
        }
    }

    /**
     * Clears completed and failed transfers from the queue.
     */
    public void clearCompleted() {
        queue.clearCompleted();
    }

    /**
     * Clears the entire transfer queue.
     */
    public void clearAll() {
        queue.clear();
    }
}
