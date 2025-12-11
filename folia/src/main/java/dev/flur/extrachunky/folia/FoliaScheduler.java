package dev.flur.extrachunky.folia;

import dev.flur.extrachunky.platform.ExtraChunkyTask;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

/**
 * Folia-compatible scheduler implementation using the global region scheduler.
 */
public final class FoliaScheduler {

    private FoliaScheduler() {
    }

    public static void runTask(Plugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    public static void runTaskAsync(Plugin plugin, Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
    }

    public static ExtraChunkyTask runTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        ScheduledTask scheduledTask = Bukkit.getGlobalRegionScheduler()
                .runDelayed(plugin, t -> task.run(), delayTicks);
        return new FoliaTaskWrapper(scheduledTask);
    }

    public static ExtraChunkyTask runTaskLaterAsync(Plugin plugin, Runnable task, long delayTicks) {
        long delayMs = delayTicks * 50L; // Convert ticks to milliseconds
        ScheduledTask scheduledTask = Bukkit.getAsyncScheduler()
                .runDelayed(plugin, t -> task.run(), delayMs, TimeUnit.MILLISECONDS);
        return new FoliaTaskWrapper(scheduledTask);
    }

    public static ExtraChunkyTask runTaskTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        ScheduledTask scheduledTask = Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin, t -> task.run(), delayTicks, periodTicks);
        return new FoliaTaskWrapper(scheduledTask);
    }

    public static ExtraChunkyTask runTaskTimerAsync(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        long delayMs = delayTicks * 50L;
        long periodMs = periodTicks * 50L;
        ScheduledTask scheduledTask = Bukkit.getAsyncScheduler()
                .runAtFixedRate(plugin, t -> task.run(), delayMs, periodMs, TimeUnit.MILLISECONDS);
        return new FoliaTaskWrapper(scheduledTask);
    }

    private static class FoliaTaskWrapper implements ExtraChunkyTask {
        private final ScheduledTask task;

        FoliaTaskWrapper(ScheduledTask task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            task.cancel();
        }

        @Override
        public boolean isCancelled() {
            return task.isCancelled();
        }
    }
}
