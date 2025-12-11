package dev.flur.extrachunky.command;

import dev.flur.extrachunky.ExtraChunkyCore;
import dev.flur.extrachunky.network.HostServer;
import dev.flur.extrachunky.network.WorkerConnection;
import dev.flur.extrachunky.platform.ExtraChunkySender;

import static dev.flur.extrachunky.platform.MessageFormatter.*;

/**
 * Command to list connected workers (host only).
 * Usage: /extrachunky workers
 */
public class WorkersCommand implements ExtraChunkyCommand {
    private static final long STALE_THRESHOLD_MS = 60_000;

    private final ExtraChunkyCore core;

    public WorkersCommand(ExtraChunkyCore core) {
        this.core = core;
    }

    @Override
    public boolean execute(ExtraChunkySender sender, String[] args) {
        HostServer hostServer = core.getHostServer();

        if (hostServer == null || !hostServer.isRunning()) {
            sender.sendMessage(prefix("Not hosting. Use " + highlight("/extrachunky host") + " to start hosting."));
            return true;
        }

        var workers = hostServer.getWorkers();
        int totalWorkers = hostServer.getTotalWorkerCount();

        sender.sendMessage(header("ExtraChunky Workers"));
        sender.sendMessage(labelValue("Total workers", totalWorkers +
                (core.getConfig().isHostParticipates() ? " (including host)" : "")));

        // Show host if participating
        if (core.getConfig().isHostParticipates()) {
            sender.sendMessage(NORMAL + "  [" + HIGHLIGHT + "0" + NORMAL + "] " + HIGHLIGHT + "host" + NORMAL + " (local) - participating");
        }

        if (workers.isEmpty()) {
            sender.sendMessage(NORMAL + "  No remote workers connected.");
        } else {
            long now = System.currentTimeMillis();
            for (WorkerConnection worker : workers) {
                String status;
                if (!worker.isConnected()) {
                    status = "&cdisconnected" + NORMAL;
                } else if (now - worker.getLastProgressUpdate() > STALE_THRESHOLD_MS) {
                    status = "idle";
                } else {
                    status = HIGHLIGHT + String.format("%.1f%%", worker.getPercentComplete()) + NORMAL +
                            " (" + HIGHLIGHT + String.format("%.1f", worker.getChunksPerSecond()) + NORMAL + " chunks/s)";
                }

                sender.sendMessage(NORMAL + "  [" + HIGHLIGHT + worker.getAssignedId() + NORMAL + "] " +
                        HIGHLIGHT + worker.getHostname() + NORMAL + " (" + worker.getRemoteAddress() + ") - " + status);
            }
        }

        sender.sendMessage(labelValue("Generation", hostServer.isGenerationActive() ? "ACTIVE" : "IDLE"));

        return true;
    }
}
