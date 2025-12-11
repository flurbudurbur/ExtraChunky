package dev.flur.extrachunky.platform;

import dev.flur.extrachunky.ExtraChunkySponge;
import org.spongepowered.api.scheduler.ScheduledTask;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.util.Ticks;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class SpongeScheduler implements ExtraChunkyScheduler {
    private final ExtraChunkySponge plugin;

    public SpongeScheduler(ExtraChunkySponge plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runTask(Runnable task) {
        Task spongeTask = Task.builder()
                .plugin(plugin.getContainer())
                .execute(task)
                .build();
        plugin.getGame().server().scheduler().submit(spongeTask);
    }

    @Override
    public void runTaskAsync(Runnable task) {
        Task spongeTask = Task.builder()
                .plugin(plugin.getContainer())
                .execute(task)
                .build();
        plugin.getGame().asyncScheduler().submit(spongeTask);
    }

    @Override
    public ExtraChunkyTask runTaskLater(Runnable task, long delayTicks) {
        Task spongeTask = Task.builder()
                .plugin(plugin.getContainer())
                .delay(Ticks.of(delayTicks))
                .execute(task)
                .build();
        ScheduledTask scheduled = plugin.getGame().server().scheduler().submit(spongeTask);
        return new SpongeTask(scheduled);
    }

    @Override
    public ExtraChunkyTask runTaskLaterAsync(Runnable task, long delayTicks) {
        Task spongeTask = Task.builder()
                .plugin(plugin.getContainer())
                .delay(Ticks.of(delayTicks))
                .execute(task)
                .build();
        ScheduledTask scheduled = plugin.getGame().asyncScheduler().submit(spongeTask);
        return new SpongeTask(scheduled);
    }

    @Override
    public ExtraChunkyTask runTaskTimer(Runnable task, long delayTicks, long periodTicks) {
        Task spongeTask = Task.builder()
                .plugin(plugin.getContainer())
                .delay(Ticks.of(delayTicks))
                .interval(Ticks.of(periodTicks))
                .execute(task)
                .build();
        ScheduledTask scheduled = plugin.getGame().server().scheduler().submit(spongeTask);
        return new SpongeTask(scheduled);
    }

    @Override
    public ExtraChunkyTask runTaskTimerAsync(Runnable task, long delayTicks, long periodTicks) {
        Task spongeTask = Task.builder()
                .plugin(plugin.getContainer())
                .delay(Ticks.of(delayTicks))
                .interval(Ticks.of(periodTicks))
                .execute(task)
                .build();
        ScheduledTask scheduled = plugin.getGame().asyncScheduler().submit(spongeTask);
        return new SpongeTask(scheduled);
    }

    private static class SpongeTask implements ExtraChunkyTask {
        private final ScheduledTask task;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        SpongeTask(ScheduledTask task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            cancelled.set(true);
            task.cancel();
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }
}
