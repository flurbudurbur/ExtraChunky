package dev.flur.extrachunky.transfer;

/**
 * Summary of the current transfer queue status.
 */
public record TransferSummary(
        int totalRegions,
        int pending,
        int inProgress,
        int completed,
        int failed,
        long bytesTransferred,
        long totalBytes
) {
    /**
     * Gets the percentage of completed transfers.
     *
     * @return Percentage (0-100)
     */
    public float getPercentComplete() {
        if (totalRegions <= 0) return 0;
        return (float) completed / totalRegions * 100;
    }

    /**
     * Gets the byte transfer percentage.
     *
     * @return Percentage (0-100)
     */
    public float getBytesPercentComplete() {
        if (totalBytes <= 0) return 0;
        return (float) bytesTransferred / totalBytes * 100;
    }

    /**
     * Checks if all transfers are complete.
     */
    public boolean isComplete() {
        return pending == 0 && inProgress == 0;
    }

    /**
     * Checks if transfers are actively running.
     */
    public boolean isActive() {
        return inProgress > 0;
    }

    /**
     * Gets a human-readable summary.
     */
    public String toStatusString() {
        if (totalRegions == 0) {
            return "No transfers queued";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Transfer: %d/%d regions (%.1f%%)",
                completed, totalRegions, getPercentComplete()));

        if (inProgress > 0) {
            sb.append(String.format(" [%d active]", inProgress));
        }
        if (failed > 0) {
            sb.append(String.format(" [%d failed]", failed));
        }

        return sb.toString();
    }
}
