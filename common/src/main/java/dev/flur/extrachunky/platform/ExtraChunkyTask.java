package dev.flur.extrachunky.platform;

/**
 * Handle for a scheduled task that can be cancelled.
 */
public interface ExtraChunkyTask {

    /**
     * Cancels this task.
     */
    void cancel();

    /**
     * Checks if this task has been cancelled.
     *
     * @return true if cancelled
     */
    boolean isCancelled();
}
