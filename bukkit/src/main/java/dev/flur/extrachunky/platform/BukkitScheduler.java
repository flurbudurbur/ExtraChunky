package dev.flur.extrachunky.platform;

import dev.flur.extrachunky.folia.Folia;
import dev.flur.extrachunky.folia.FoliaScheduler;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class BukkitScheduler implements ExtraChunkyScheduler {
    private final Plugin plugin;
    private final boolean isFolia;

    public BukkitScheduler(Plugin plugin) {
        this.plugin = plugin;
        this.isFolia = Folia.isFolia();
    }

    @Override
    public void runTask(Runnable task) {
        if (isFolia) {
            FoliaScheduler.runTask(plugin, task);
        } else {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }

    @Override
    public void runTaskAsync(Runnable task) {
        if (isFolia) {
            FoliaScheduler.runTaskAsync(plugin, task);
        } else {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    @Override
    public ExtraChunkyTask runTaskLater(Runnable task, long delayTicks) {
        if (isFolia) {
            return FoliaScheduler.runTaskLater(plugin, task, delayTicks);
        }
        BukkitTask bukkitTask = plugin.getServer().getScheduler()
                .runTaskLater(plugin, task, delayTicks);
        return new BukkitTaskWrapper(bukkitTask);
    }

    @Override
    public ExtraChunkyTask runTaskLaterAsync(Runnable task, long delayTicks) {
        if (isFolia) {
            return FoliaScheduler.runTaskLaterAsync(plugin, task, delayTicks);
        }
        BukkitTask bukkitTask = plugin.getServer().getScheduler()
                .runTaskLaterAsynchronously(plugin, task, delayTicks);
        return new BukkitTaskWrapper(bukkitTask);
    }

    @Override
    public ExtraChunkyTask runTaskTimer(Runnable task, long delayTicks, long periodTicks) {
        if (isFolia) {
            return FoliaScheduler.runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
        BukkitTask bukkitTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, task, delayTicks, periodTicks);
        return new BukkitTaskWrapper(bukkitTask);
    }

    @Override
    public ExtraChunkyTask runTaskTimerAsync(Runnable task, long delayTicks, long periodTicks) {
        if (isFolia) {
            return FoliaScheduler.runTaskTimerAsync(plugin, task, delayTicks, periodTicks);
        }
        BukkitTask bukkitTask = plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
        return new BukkitTaskWrapper(bukkitTask);
    }

    private static class BukkitTaskWrapper implements ExtraChunkyTask {
        private final BukkitTask task;

        BukkitTaskWrapper(BukkitTask task) {
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
