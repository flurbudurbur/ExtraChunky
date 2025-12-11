package dev.flur.extrachunky.transfer;

/**
 * Tracks the state of a region file transfer.
 */
public record TransferState(
        RegionCoord region,
        Status status,
        int attemptCount,
        long bytesTransferred,
        long totalBytes,
        String errorMessage,
        long lastAttemptTime
) {
    /**
     * Transfer status states.
     */
    public enum Status {
        /** Waiting to be processed */
        PENDING,
        /** Currently being compressed */
        COMPRESSING,
        /** Currently being uploaded */
        UPLOADING,
        /** Successfully transferred */
        COMPLETED,
        /** Failed after all retries */
        FAILED
    }

    /**
     * Creates an initial pending state for a region.
     */
    public static TransferState pending(RegionCoord region) {
        return new TransferState(region, Status.PENDING, 0, 0, 0, null, 0);
    }

    /**
     * Creates a new state with updated status.
     */
    public TransferState withStatus(Status newStatus) {
        return new TransferState(region, newStatus, attemptCount, bytesTransferred, totalBytes, errorMessage, lastAttemptTime);
    }

    /**
     * Creates a new state marked as compressing.
     */
    public TransferState compressing() {
        return new TransferState(region, Status.COMPRESSING, attemptCount, 0, 0, null, System.currentTimeMillis());
    }

    /**
     * Creates a new state marked as uploading with total size.
     */
    public TransferState uploading(long totalBytes) {
        return new TransferState(region, Status.UPLOADING, attemptCount, 0, totalBytes, null, System.currentTimeMillis());
    }

    /**
     * Creates a new state with updated progress.
     */
    public TransferState withProgress(long bytesTransferred) {
        return new TransferState(region, status, attemptCount, bytesTransferred, totalBytes, errorMessage, lastAttemptTime);
    }

    /**
     * Creates a new state marked as completed.
     */
    public TransferState completed() {
        return new TransferState(region, Status.COMPLETED, attemptCount, totalBytes, totalBytes, null, System.currentTimeMillis());
    }

    /**
     * Creates a new state marked as failed with error message.
     */
    public TransferState failed(String error) {
        return new TransferState(region, Status.FAILED, attemptCount + 1, bytesTransferred, totalBytes, error, System.currentTimeMillis());
    }

    /**
     * Creates a new state ready for retry.
     */
    public TransferState retry() {
        return new TransferState(region, Status.PENDING, attemptCount + 1, 0, 0, null, System.currentTimeMillis());
    }

    /**
     * Gets the progress percentage.
     */
    public float getProgressPercent() {
        if (totalBytes <= 0) return 0;
        return (float) bytesTransferred / totalBytes * 100;
    }

    /**
     * Checks if this transfer can be retried.
     */
    public boolean canRetry(int maxAttempts) {
        return status == Status.FAILED && attemptCount < maxAttempts;
    }
}
