package dev.flur.extrachunky.platform;

import net.minecraft.server.MinecraftServer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class NeoForgeScheduler implements ExtraChunkyScheduler {
    private final MinecraftServer server;
    private final ScheduledExecutorService asyncExecutor;

    public NeoForgeScheduler(MinecraftServer server) {
        this.server = server;
        this.asyncExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "ExtraChunky-Async");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void runTask(Runnable task) {
        server.execute(task);
    }

    @Override
    public void runTaskAsync(Runnable task) {
        asyncExecutor.execute(task);
    }

    @Override
    public ExtraChunkyTask runTaskLater(Runnable task, long delayTicks) {
        long delayMs = delayTicks * 50L;
        ScheduledFuture<?> future = asyncExecutor.schedule(() -> server.execute(task), delayMs, TimeUnit.MILLISECONDS);
        return new NeoForgeTask(future);
    }

    @Override
    public ExtraChunkyTask runTaskLaterAsync(Runnable task, long delayTicks) {
        long delayMs = delayTicks * 50L;
        ScheduledFuture<?> future = asyncExecutor.schedule(task, delayMs, TimeUnit.MILLISECONDS);
        return new NeoForgeTask(future);
    }

    @Override
    public ExtraChunkyTask runTaskTimer(Runnable task, long delayTicks, long periodTicks) {
        long delayMs = delayTicks * 50L;
        long periodMs = periodTicks * 50L;
        ScheduledFuture<?> future = asyncExecutor.scheduleAtFixedRate(
                () -> server.execute(task), delayMs, periodMs, TimeUnit.MILLISECONDS);
        return new NeoForgeTask(future);
    }

    @Override
    public ExtraChunkyTask runTaskTimerAsync(Runnable task, long delayTicks, long periodTicks) {
        long delayMs = delayTicks * 50L;
        long periodMs = periodTicks * 50L;
        ScheduledFuture<?> future = asyncExecutor.scheduleAtFixedRate(task, delayMs, periodMs, TimeUnit.MILLISECONDS);
        return new NeoForgeTask(future);
    }

    public void shutdown() {
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static class NeoForgeTask implements ExtraChunkyTask {
        private final ScheduledFuture<?> future;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        NeoForgeTask(ScheduledFuture<?> future) {
            this.future = future;
        }

        @Override
        public void cancel() {
            cancelled.set(true);
            future.cancel(false);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get() || future.isCancelled();
        }
    }
}
