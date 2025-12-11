package dev.flur.extrachunky;

import dev.flur.extrachunky.command.*;
import dev.flur.extrachunky.network.HostServer;
import dev.flur.extrachunky.network.WorkerClient;
import dev.flur.extrachunky.platform.*;
import dev.flur.extrachunky.transfer.RegionTransferManager;
import dev.flur.extrachunky.transfer.TransferSummary;
import org.popcraft.chunky.Chunky;
import org.popcraft.chunky.Selection;
import org.popcraft.chunky.api.ChunkyAPI;
import org.popcraft.chunky.api.event.task.GenerationStartEvent;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Core ExtraChunky coordinator - platform independent.
 * Each platform creates an instance of this and wires up the platform-specific implementations.
 */
public class ExtraChunkyCore {
    private final ExtraChunkyPlatform platform;
    private final Map<String, ExtraChunkyCommand> commands;

    private Chunky chunky;
    private ChunkyAPI chunkyApi;

    // Network components (mutually exclusive - either host or worker, not both)
    private HostServer hostServer;
    private WorkerClient workerClient;

    // Progress reporting task
    private ExtraChunkyTask progressTask;
    private int currentInstanceId;
    private int currentTotalInstances;

    // Command handler reference (for START callback)
    private StartCommand startCommand;

    // Transfer manager for SFTP file transfers
    private RegionTransferManager transferManager;

    // Whether the Chunky API has the GenerationStartEvent
    private boolean hasStartEvent = false;

    public ExtraChunkyCore(ExtraChunkyPlatform platform) {
        this.platform = platform;
        this.commands = new HashMap<>();
    }

    /**
     * Initializes ExtraChunky. Must be called after construction.
     *
     * @return true if initialization was successful
     */
    public boolean initialize() {
        var chunkyOpt = platform.getChunky();
        var apiOpt = platform.getChunkyApi();

        if (chunkyOpt.isEmpty()) {
            platform.getLogger().severe("Chunky not found! ExtraChunky requires Chunky to be installed.");
            platform.getLogger().severe("Please install Chunky from: https://github.com/pop4959/Chunky");
            return false;
        }

        this.chunky = chunkyOpt.get();
        this.chunkyApi = apiOpt.orElse(null);

        if (this.chunkyApi == null) {
            platform.getLogger().severe("Chunky API not available! Please update Chunky to the latest version.");
            return false;
        }

        // Load configuration
        platform.getConfig().saveDefaultConfig();

        // Register commands
        registerCommands();

        // Try to register start event listener (requires Chunky with GenerationStartEvent)
        registerStartEventListener();

        platform.getLogger().info("ExtraChunky enabled");
        Selection selection = getSelection();
        platform.getLogger().info("Using Chunky selection - World: " + selection.world().getName() +
                ", Center: (" + selection.centerX() + ", " + selection.centerZ() + ")" +
                ", Radius: " + selection.radiusX() +
                ", Shape: " + selection.shape());
        platform.getLogger().info("Configure generation area with /chunky commands (world, center, radius, shape)");
        platform.getLogger().info("Use /extrachunky host to start hosting, or /extrachunky register <host:port> to connect as worker");

        return true;
    }

    private void registerCommands() {
        this.startCommand = new StartCommand(this);
        commands.put("start", startCommand);
        commands.put("status", new StatusCommand(this));
        commands.put("merge", new MergeCommand(this));
        commands.put("host", new HostCommand(this));
        commands.put("register", new RegisterCommand(this));
        commands.put("workers", new WorkersCommand(this));
        commands.put("transfer", new TransferCommand(this));
        commands.put("ssh", new SshCommand(this));
    }

    /**
     * Attempts to register a listener for Chunky's GenerationStartEvent.
     * This allows ExtraChunky to automatically intercept /chunky start.
     */
    private void registerStartEventListener() {
        // Skip if manual-start is enabled
        if (platform.getConfig().isManualStart()) {
            platform.getLogger().info("Manual start mode enabled. Use /extrachunky start to begin generation.");
            return;
        }

        try {
            chunkyApi.onGenerationStart(this::handleGenerationStart);
            hasStartEvent = true;
            platform.getLogger().info("Automatic /chunky start interception enabled.");
        } catch (NoSuchMethodError | AbstractMethodError e) {
            hasStartEvent = false;
            platform.getLogger().warning("Chunky version does not support GenerationStartEvent.");
            platform.getLogger().warning("Use /extrachunky start for distributed generation, or update Chunky.");
        }
    }

    /**
     * Handles the GenerationStartEvent from Chunky.
     * Cancels the default behavior and triggers ExtraChunky's distributed generation.
     */
    private void handleGenerationStart(GenerationStartEvent event) {
        // Cancel the default Chunky behavior
        event.setCancelled(true);

        // Run on main thread
        platform.getScheduler().runTask(() -> {
            ExtraChunkySender console = platform.getConsoleSender();

            if (isHost()) {
                // Host mode: broadcast to workers and optionally participate
                startCommand.executeAsHost(console);
            } else if (isWorker()) {
                // Worker mode: should wait for host to start
                console.sendMessage(MessageFormatter.prefix("As a worker, wait for host to start generation."));
                console.sendMessage(MessageFormatter.prefix("The host will coordinate chunk assignments."));
            } else {
                // Standalone mode: single server generation
                startCommand.executeStandalone(console);
            }
        });
    }

    /**
     * Shuts down ExtraChunky. Called when the plugin/mod is disabled.
     */
    public void disable() {
        stopHostServer();
        stopWorkerClient();
        stopProgressTask();
        if (transferManager != null && transferManager.isRunning()) {
            transferManager.cancel();
        }
        platform.getLogger().info("ExtraChunky disabled");
    }

    /**
     * Initializes the transfer manager for a specific world.
     * Should be called when starting generation as a worker.
     *
     * @param worldPath Path to the world directory
     */
    public void initializeTransferManager(Path worldPath) {
        if (transferManager != null && transferManager.isRunning()) {
            transferManager.cancel();
        }

        transferManager = new RegionTransferManager(
                platform.getScheduler(),
                platform.getLogger(),
                platform.getConfig().getSshConfig(),
                platform.getDataDirectory(),
                worldPath
        );

        transferManager.loadQueue();

        // Set up progress callback to notify host
        transferManager.setProgressCallback(this::onTransferProgress);
    }

    private void onTransferProgress(TransferSummary summary) {
        if (workerClient != null && workerClient.isConnected()) {
            workerClient.sendTransferProgress(
                    summary.completed(),
                    summary.totalRegions(),
                    summary.bytesTransferred(),
                    summary.totalBytes()
            );

            if (summary.isComplete()) {
                if (summary.failed() > 0) {
                    workerClient.sendTransferFailed(
                            summary.failed() + " transfers failed",
                            summary.failed()
                    );
                } else {
                    workerClient.sendTransferComplete(summary.completed());
                }
            }
        }
    }

    /**
     * Starts the host server to accept worker connections.
     */
    public void startHostServer() {
        if (hostServer != null && hostServer.isRunning()) {
            return;
        }

        hostServer = new HostServer(platform.getLogger(), platform.getConfig());
        hostServer.start();
    }

    /**
     * Stops the host server.
     */
    public void stopHostServer() {
        if (hostServer != null) {
            hostServer.stop();
            hostServer = null;
        }
    }

    /**
     * Starts the worker client and connects to a host.
     */
    public void startWorkerClient(String hostAddress, int hostPort) {
        if (workerClient != null && workerClient.isRunning()) {
            workerClient.disconnect();
        }

        workerClient = new WorkerClient(platform.getLogger(), platform.getScheduler(), hostAddress, hostPort);

        // Setup handlers for START command from host
        workerClient.setStartHandler(msg -> {
            WorkerClient.ChunkAssignment assignment = workerClient.getCurrentAssignment();
            if (assignment != null && startCommand != null) {
                // Run on main thread
                platform.getScheduler().runTask(() -> startCommand.startFromHost(assignment));
            }
        });

        // Setup handler for STOP command from host
        workerClient.setStopHandler(msg -> {
            String world = getSelection().world().getName();
            if (chunkyApi != null && chunkyApi.isRunning(world)) {
                platform.getScheduler().runTask(() -> {
                    chunkyApi.cancelTask(world);
                    platform.getLogger().info("Generation stopped by host command");
                });
            }
        });

        workerClient.connect();
    }

    /**
     * Stops the worker client.
     */
    public void stopWorkerClient() {
        if (workerClient != null) {
            workerClient.disconnect();
            workerClient = null;
        }
    }

    /**
     * Sets up progress reporting based on current mode.
     */
    public void setupProgressReporting(ChunkyAPI api, String worldName, long totalChunks, int instanceId, int totalInstances) {
        stopProgressTask();

        this.currentInstanceId = instanceId;
        this.currentTotalInstances = totalInstances;

        // Listen to Chunky progress events
        api.onGenerationProgress(event -> {
            if (!event.world().equals(worldName)) {
                return;
            }

            // Report to host if connected as worker
            if (workerClient != null && workerClient.isConnected()) {
                workerClient.sendProgress(
                        event.chunks(),
                        totalChunks,
                        event.progress(),
                        (float) event.rate()
                );
            }

            // Update host's own progress if hosting and participating
            if (hostServer != null && hostServer.isRunning() && platform.getConfig().isHostParticipates()) {
                hostServer.updateHostProgress(
                        event.chunks(),
                        totalChunks,
                        event.progress(),
                        (float) event.rate()
                );
            }
        });
    }

    private void stopProgressTask() {
        if (progressTask != null) {
            progressTask.cancel();
            progressTask = null;
        }
    }

    // Getters

    public ExtraChunkyPlatform getPlatform() {
        return platform;
    }

    public Chunky getChunky() {
        return chunky;
    }

    public ChunkyAPI getChunkyApi() {
        return chunkyApi;
    }

    /**
     * Gets the current Chunky selection (world, center, radius, shape).
     */
    public Selection getSelection() {
        return chunky.getSelection().build();
    }

    public ExtraChunkyConfig getConfig() {
        return platform.getConfig();
    }

    public HostServer getHostServer() {
        return hostServer;
    }

    public WorkerClient getWorkerClient() {
        return workerClient;
    }

    public Map<String, ExtraChunkyCommand> getCommands() {
        return commands;
    }

    public StartCommand getStartCommand() {
        return startCommand;
    }

    public RegionTransferManager getTransferManager() {
        return transferManager;
    }

    public ExtraChunkyScheduler getScheduler() {
        return platform.getScheduler();
    }

    public ExtraChunkyLogger getLogger() {
        return platform.getLogger();
    }

    /**
     * Checks if this server is currently acting as a host.
     */
    public boolean isHost() {
        return hostServer != null && hostServer.isRunning();
    }

    /**
     * Checks if this server is currently connected as a worker.
     */
    public boolean isWorker() {
        return workerClient != null && workerClient.isConnected();
    }

    /**
     * Checks if Chunky has the GenerationStartEvent and it's being intercepted.
     */
    public boolean hasStartEvent() {
        return hasStartEvent;
    }

    /**
     * Gets the Chunky configuration directory where CSV files should be placed.
     */
    public Path getChunkyConfigDir() {
        return chunky.getConfig().getDirectory();
    }
}
