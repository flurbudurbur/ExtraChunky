package dev.flur.extrachunky.command;

import dev.flur.extrachunky.ExtraChunkyCore;
import dev.flur.extrachunky.network.HostServer;
import dev.flur.extrachunky.network.WorkerClient;
import dev.flur.extrachunky.platform.ExtraChunkySender;
import dev.flur.extrachunky.platform.MessageFormatter;
import org.popcraft.chunky.Selection;
import org.popcraft.chunky.api.ChunkyAPI;

import java.text.DecimalFormat;

import static dev.flur.extrachunky.platform.MessageFormatter.*;

public class StatusCommand implements ExtraChunkyCommand {
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,###");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.0");

    private final ExtraChunkyCore core;

    public StatusCommand(ExtraChunkyCore core) {
        this.core = core;
    }

    @Override
    public boolean execute(ExtraChunkySender sender, String[] args) {
        ChunkyAPI api = core.getChunkyApi();
        HostServer hostServer = core.getHostServer();
        WorkerClient workerClient = core.getWorkerClient();
        Selection selection = core.getSelection();

        String worldName = selection.world().getName();

        // If hosting, show aggregated progress from all workers
        if (hostServer != null && hostServer.isRunning()) {
            displayHostStatus(sender, hostServer, api, worldName);
            return true;
        }

        // If connected as worker, show worker status
        if (workerClient != null && workerClient.isRunning()) {
            displayWorkerStatus(sender, workerClient, api, worldName);
            return true;
        }

        // Standalone mode
        displayStandaloneStatus(sender, api, worldName);
        return true;
    }

    private void displayHostStatus(ExtraChunkySender sender, HostServer hostServer, ChunkyAPI api, String worldName) {
        HostServer.AggregatedProgress progress = hostServer.getAggregatedProgress();

        sender.sendMessage(header("ExtraChunky Host Status"));
        sender.sendMessage(labelValue("Mode", "HOST (port " + core.getConfig().getHostPort() + ")"));
        sender.sendMessage(labelValue("Generation", hostServer.isGenerationActive() ? "ACTIVE" : "IDLE"));
        sender.sendMessage("");

        if (progress.totalChunks() > 0) {
            sender.sendMessage(NORMAL + "Total: " + HIGHLIGHT + DECIMAL_FORMAT.format(progress.totalChunksGenerated()) +
                    "/" + DECIMAL_FORMAT.format(progress.totalChunks()) +
                    NORMAL + " chunks (" + HIGHLIGHT + PERCENT_FORMAT.format(progress.overallPercent()) + "%" + NORMAL + ")");
        }
        sender.sendMessage(labelValue("Active workers", progress.activeWorkers() + "/" + hostServer.getTotalWorkerCount()));
        sender.sendMessage("");

        // Show each worker's progress
        if (!progress.workers().isEmpty()) {
            for (HostServer.WorkerProgress worker : progress.workers()) {
                String status = formatWorkerProgress(worker);
                sender.sendMessage(status);
            }
        } else {
            sender.sendMessage(NORMAL + "  No workers connected yet.");
            sender.sendMessage(NORMAL + "  Workers can connect with: " + highlight("/extrachunky register <this-ip>:" +
                    core.getConfig().getHostPort()));
        }
    }

    private void displayWorkerStatus(ExtraChunkySender sender, WorkerClient client, ChunkyAPI api, String worldName) {
        sender.sendMessage(header("ExtraChunky Worker Status"));
        sender.sendMessage(labelValue("Mode", "WORKER"));
        sender.sendMessage(labelValue("Host", client.getHostAddress() + ":" + client.getHostPort()));
        sender.sendMessage(labelValue("Connection", client.isConnected() ? "CONNECTED" : "RECONNECTING..."));

        if (client.isConnected()) {
            sender.sendMessage(labelValue("Assigned ID", String.valueOf(client.getAssignedId())));
            sender.sendMessage(labelValue("Total workers", String.valueOf(client.getTotalWorkers())));

            WorkerClient.ChunkAssignment assignment = client.getCurrentAssignment();
            if (assignment != null) {
                sender.sendMessage(labelValue("Assignment", "instance " + assignment.instanceId() +
                        "/" + assignment.totalInstances()));
            }
        }

        sender.sendMessage("");

        // Show local generation status
        if (api != null && api.isRunning(worldName)) {
            sender.sendMessage(labelValue("Local generation", "RUNNING for " + worldName));
            sender.sendMessage(NORMAL + "Use " + highlight("/chunky progress") + " for detailed status");
        } else {
            sender.sendMessage(labelValue("Local generation", "IDLE"));
        }
    }

    private void displayStandaloneStatus(ExtraChunkySender sender, ChunkyAPI api, String worldName) {
        sender.sendMessage(header("ExtraChunky Status"));
        sender.sendMessage(labelValue("Mode", "STANDALONE"));
        sender.sendMessage("");

        if (api != null && api.isRunning(worldName)) {
            sender.sendMessage(labelValue("Generation", "RUNNING for " + worldName));
            sender.sendMessage(NORMAL + "Use " + highlight("/chunky progress") + " for detailed status");
        } else {
            sender.sendMessage(labelValue("Generation", "IDLE"));
        }

        sender.sendMessage("");
        sender.sendMessage(NORMAL + "Tip: Use " + highlight("/extrachunky host") + " to coordinate multiple servers");
    }

    private String formatWorkerProgress(HostServer.WorkerProgress worker) {
        StringBuilder sb = new StringBuilder();
        sb.append(NORMAL).append("  [").append(HIGHLIGHT).append(worker.instanceId()).append(NORMAL).append("] ");
        sb.append(HIGHLIGHT).append(worker.hostname()).append(NORMAL).append(": ");

        if (worker.totalChunks() > 0) {
            sb.append(HIGHLIGHT).append(DECIMAL_FORMAT.format(worker.chunksGenerated())).append(NORMAL).append(" chunks ");
            sb.append("(").append(HIGHLIGHT).append(PERCENT_FORMAT.format(worker.percentComplete())).append("%").append(NORMAL).append(") - ");
            sb.append(HIGHLIGHT).append(PERCENT_FORMAT.format(worker.chunksPerSecond())).append(NORMAL).append(" c/s");
        } else {
            sb.append("waiting");
        }

        if (!worker.active()) {
            sb.append(" &c[STALE]").append(NORMAL);
        }

        // Add transfer status if generation is complete
        if (worker.generationComplete()) {
            sb.append(" | Transfer: ");
            if (worker.transferComplete()) {
                sb.append("&aDONE").append(NORMAL);
            } else if (worker.transferError() != null) {
                sb.append("&cFAILED").append(NORMAL);
            } else if (worker.transferTotal() > 0) {
                sb.append(HIGHLIGHT).append(worker.transferCompleted()).append("/").append(worker.transferTotal()).append(NORMAL);
            } else {
                sb.append("starting");
            }
        }

        return sb.toString();
    }
}
