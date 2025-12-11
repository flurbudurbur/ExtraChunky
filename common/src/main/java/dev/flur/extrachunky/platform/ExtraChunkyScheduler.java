package dev.flur.extrachunky.platform;

/**
 * Platform abstraction for task scheduling.
 * Each platform implements this to handle async tasks and timers.
 */
public interface ExtraChunkyScheduler {

    /**
     * Runs a task on the main/server thread.
     *
     * @param task The task to run
     */
    void runTask(Runnable task);

    /**
     * Runs a task asynchronously.
     *
     * @param task The task to run
     */
    void runTaskAsync(Runnable task);

    /**
     * Runs a task later on the main thread.
     *
     * @param task       The task to run
     * @param delayTicks Delay in server ticks (20 ticks = 1 second)
     * @return A cancellable task handle
     */
    ExtraChunkyTask runTaskLater(Runnable task, long delayTicks);

    /**
     * Runs a task later asynchronously.
     *
     * @param task       The task to run
     * @param delayTicks Delay in server ticks (20 ticks = 1 second)
     * @return A cancellable task handle
     */
    ExtraChunkyTask runTaskLaterAsync(Runnable task, long delayTicks);

    /**
     * Runs a repeating task on the main thread.
     *
     * @param task        The task to run
     * @param delayTicks  Initial delay in ticks
     * @param periodTicks Period between runs in ticks
     * @return A cancellable task handle
     */
    ExtraChunkyTask runTaskTimer(Runnable task, long delayTicks, long periodTicks);

    /**
     * Runs a repeating task asynchronously.
     *
     * @param task        The task to run
     * @param delayTicks  Initial delay in ticks
     * @param periodTicks Period between runs in ticks
     * @return A cancellable task handle
     */
    ExtraChunkyTask runTaskTimerAsync(Runnable task, long delayTicks, long periodTicks);
}
