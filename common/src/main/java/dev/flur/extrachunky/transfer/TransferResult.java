package dev.flur.extrachunky.transfer;

/**
 * Result of a file transfer operation.
 */
public record TransferResult(
        String localPath,
        String remotePath,
        long bytesTransferred,
        long durationMs,
        boolean success,
        String errorMessage
) {
    /**
     * Gets the transfer speed in bytes per second.
     *
     * @return Transfer speed, or 0 if duration is 0
     */
    public long getBytesPerSecond() {
        if (durationMs <= 0) return 0;
        return bytesTransferred * 1000 / durationMs;
    }

    /**
     * Gets a human-readable summary of the transfer.
     *
     * @return Summary string
     */
    public String getSummary() {
        if (success) {
            long kbps = getBytesPerSecond() / 1024;
            return String.format("Transferred %d KB in %d ms (%d KB/s)",
                    bytesTransferred / 1024, durationMs, kbps);
        } else {
            return "Failed: " + errorMessage;
        }
    }
}
