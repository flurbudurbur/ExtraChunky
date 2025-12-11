package dev.flur.extrachunky.command;

import dev.flur.extrachunky.ChunkAssigner;
import dev.flur.extrachunky.CsvGenerator;
import dev.flur.extrachunky.ExtraChunkyCore;
import dev.flur.extrachunky.network.HostServer;
import dev.flur.extrachunky.network.WorkerClient;
import dev.flur.extrachunky.platform.ExtraChunkyConfig;
import dev.flur.extrachunky.platform.ExtraChunkySender;
import dev.flur.extrachunky.platform.MessageFormatter;
import org.popcraft.chunky.Selection;
import org.popcraft.chunky.api.ChunkyAPI;

import java.io.IOException;
import java.nio.file.Path;

import static dev.flur.extrachunky.platform.MessageFormatter.*;

public class StartCommand implements ExtraChunkyCommand {
    private final ExtraChunkyCore core;

    public StartCommand(ExtraChunkyCore core) {
        this.core = core;
    }

    @Override
    public boolean execute(ExtraChunkySender sender, String[] args) {
        // If /chunky start interception is enabled, redirect users
        if (core.hasStartEvent() && !core.getConfig().isManualStart()) {
            sender.sendMessage(prefix("Automatic interception is enabled."));
            sender.sendMessage(prefix("Use " + highlight("/chunky start") + " to begin distributed generation."));
            return true;
        }

        // Manual mode or no start event available - execute directly
        HostServer hostServer = core.getHostServer();
        WorkerClient workerClient = core.getWorkerClient();

        // If hosting, broadcast start to all workers
        if (hostServer != null && hostServer.isRunning()) {
            return executeAsHost(sender);
        }

        // If connected as worker, use assignment from host
        if (workerClient != null && workerClient.isConnected()) {
            return executeAsWorker(sender);
        }

        // Standalone mode (no network)
        return executeStandalone(sender);
    }

    /**
     * Executes the start command in host mode.
     * Broadcasts start to workers and optionally participates locally.
     */
    public boolean executeAsHost(ExtraChunkySender sender) {
        HostServer hostServer = core.getHostServer();
        ExtraChunkyConfig config = core.getConfig();
        Selection selection = core.getSelection();

        int totalWorkers = hostServer.getTotalWorkerCount();
        if (totalWorkers == 0) {
            sender.sendMessage(prefix("No workers available. Either:"));
            sender.sendMessage(NORMAL + "  - Enable " + highlight("host-participates") + " in config, or");
            sender.sendMessage(NORMAL + "  - Have workers connect with " + highlight("/extrachunky register"));
            return true;
        }

        sender.sendMessage(prefix("Starting generation across " + highlight(totalWorkers + " worker(s)") + "..."));
        sender.sendMessage(prefix("Using selection: " + highlight(selection.world().getName()) +
                " center=(" + selection.centerX() + ", " + selection.centerZ() + ")" +
                " radius=" + selection.radiusX() + " shape=" + selection.shape()));

        // Broadcast START to all workers (with current selection)
        hostServer.broadcastStart(selection);

        // If host participates, start local generation
        if (config.isHostParticipates()) {
            int instanceId = 0; // Host is always instance 0
            startLocalGeneration(sender, instanceId, totalWorkers, selection);
        }

        sender.sendMessage(prefix("Generation started. Use " + highlight("/extrachunky status") + " to monitor progress."));
        return true;
    }

    private boolean executeAsWorker(ExtraChunkySender sender) {
        WorkerClient client = core.getWorkerClient();
        WorkerClient.ChunkAssignment assignment = client.getCurrentAssignment();

        if (assignment == null) {
            sender.sendMessage(prefix("Waiting for assignment from host..."));
            return true;
        }

        sender.sendMessage(prefix("Starting as worker " + highlight(assignment.instanceId() +
                "/" + assignment.totalInstances())));

        startLocalGeneration(sender, assignment.instanceId(), assignment.totalInstances(),
                assignment.world(), assignment.centerX(), assignment.centerZ(),
                assignment.radius(), assignment.shape());

        return true;
    }

    /**
     * Executes the start command in standalone mode.
     * Single server generation without network coordination.
     */
    public boolean executeStandalone(ExtraChunkySender sender) {
        Selection selection = core.getSelection();

        sender.sendMessage(prefix("Starting distributed generation..."));
        sender.sendMessage(prefix("Tip: Use " + highlight("/extrachunky host") + " to coordinate multiple servers."));

        // Standalone uses instance 1/1
        startLocalGeneration(sender, 1, 1, selection);
        return true;
    }

    private void startLocalGeneration(ExtraChunkySender sender, int instanceId, int totalInstances, Selection selection) {
        startLocalGeneration(sender, instanceId, totalInstances,
                selection.world().getName(), selection.centerX(), selection.centerZ(),
                selection.radiusX(), selection.shape());
    }

    private void startLocalGeneration(ExtraChunkySender sender, int instanceId, int totalInstances,
                                      String world, double centerX, double centerZ,
                                      double radius, String shape) {
        ChunkyAPI api = core.getChunkyApi();

        if (api == null) {
            sender.sendMessage(prefix("Chunky API not available!"));
            return;
        }

        // Check if already running
        if (api.isRunning(world)) {
            sender.sendMessage(prefix("A generation task is already running for " + highlight(world)));
            return;
        }

        sender.sendMessage(prefix("Generating chunk list for instance " +
                highlight(instanceId + "/" + totalInstances) + "..."));

        // Create chunk assigner
        ChunkAssigner assigner = new ChunkAssigner(instanceId, totalInstances);

        // Generate CSV file
        CsvGenerator csvGenerator = new CsvGenerator(core.getPlatform().getLogger());
        Path chunkyConfigDir = core.getChunkyConfigDir();

        try {
            CsvGenerator.GenerationResult result = csvGenerator.generateChunkCsv(
                    world, centerX, centerZ, radius, shape, instanceId, assigner, chunkyConfigDir);

            sender.sendMessage(prefix("Created " + highlight(result.chunkCount() + "") + " chunk entries"));

            if (result.chunkCount() == 0) {
                sender.sendMessage(prefix("No chunks assigned to this instance."));
                return;
            }

            // Start Chunky with CSV pattern
            String pattern = "csv=" + result.fileName().replace(".csv", "");

            sender.sendMessage(prefix("Starting Chunky with pattern: " + highlight(pattern)));

            // Start the task using Chunky API
            boolean started = api.startTask(world, shape, centerX, centerZ, radius, radius, pattern);

            if (started) {
                sender.sendMessage(prefix("Generation started for " + highlight(world)));

                // Setup progress reporting
                core.setupProgressReporting(api, world, result.chunkCount(), instanceId, totalInstances);
            } else {
                sender.sendMessage(prefix("Failed to start Chunky task. Check Chunky logs for details."));
            }

        } catch (IOException e) {
            sender.sendMessage(prefix("Failed to generate chunk CSV: " + e.getMessage()));
            core.getPlatform().getLogger().severe("Failed to generate chunk CSV: " + e.getMessage());
        }
    }

    /**
     * Called by WorkerClient when it receives a START command from host.
     */
    public void startFromHost(WorkerClient.ChunkAssignment assignment) {
        core.getPlatform().getLogger().info("Received START command from host, starting generation...");
        startLocalGeneration(core.getPlatform().getConsoleSender(),
                assignment.instanceId(), assignment.totalInstances(),
                assignment.world(), assignment.centerX(), assignment.centerZ(),
                assignment.radius(), assignment.shape());
    }
}
