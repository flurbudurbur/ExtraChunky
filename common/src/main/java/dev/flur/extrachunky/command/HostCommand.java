package dev.flur.extrachunky.command;

import dev.flur.extrachunky.ExtraChunkyCore;
import dev.flur.extrachunky.network.HostServer;
import dev.flur.extrachunky.platform.ExtraChunkySender;

import static dev.flur.extrachunky.platform.MessageFormatter.*;

/**
 * Command to start/stop hosting and allow workers to connect.
 * Usage: /extrachunky host [stop]
 */
public class HostCommand implements ExtraChunkyCommand {
    private final ExtraChunkyCore core;

    public HostCommand(ExtraChunkyCore core) {
        this.core = core;
    }

    @Override
    public boolean execute(ExtraChunkySender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("stop")) {
            return stopHost(sender);
        }
        return startHost(sender);
    }

    private boolean startHost(ExtraChunkySender sender) {
        // Check if already connected as a worker
        if (core.getWorkerClient() != null && core.getWorkerClient().isRunning()) {
            sender.sendMessage(prefix("Cannot host while connected as a worker. Use " + highlight("/extrachunky unregister") + " first."));
            return true;
        }

        HostServer hostServer = core.getHostServer();
        if (hostServer != null && hostServer.isRunning()) {
            sender.sendMessage(prefix("Host is already running on port " + highlight(String.valueOf(core.getConfig().getHostPort()))));
            sender.sendMessage(prefix("Workers: " + highlight(hostServer.getWorkers().size() + " connected")));
            return true;
        }

        // Start the host server
        core.startHostServer();
        hostServer = core.getHostServer();

        if (hostServer != null && hostServer.isRunning()) {
            sender.sendMessage(prefix("Now hosting on port " + highlight(String.valueOf(core.getConfig().getHostPort()))));
            sender.sendMessage(prefix("Workers can connect using: " + highlight("/extrachunky register <this-server-ip>:" +
                    core.getConfig().getHostPort())));
            if (core.getConfig().isHostParticipates()) {
                sender.sendMessage(prefix("This server will also participate in chunk generation."));
            }
        } else {
            sender.sendMessage(prefix("Failed to start host server. Check logs for details."));
        }

        return true;
    }

    private boolean stopHost(ExtraChunkySender sender) {
        HostServer hostServer = core.getHostServer();
        if (hostServer == null || !hostServer.isRunning()) {
            sender.sendMessage(prefix("Host is not running."));
            return true;
        }

        int workerCount = hostServer.getWorkers().size();
        core.stopHostServer();

        sender.sendMessage(prefix("Host stopped. " + highlight(workerCount + " worker(s)") + " disconnected."));
        return true;
    }
}
