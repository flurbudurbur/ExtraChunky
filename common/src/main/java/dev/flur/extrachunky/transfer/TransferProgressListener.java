package dev.flur.extrachunky.transfer;

/**
 * Listener for file transfer progress updates.
 */
@FunctionalInterface
public interface TransferProgressListener {
    /**
     * Called when transfer progress updates.
     *
     * @param bytesTransferred Bytes transferred so far
     * @param totalBytes       Total bytes to transfer
     */
    void onProgress(long bytesTransferred, long totalBytes);
}
