package dev.flur.extrachunky.command;

import dev.flur.extrachunky.ExtraChunkyCore;
import dev.flur.extrachunky.network.WorkerClient;
import dev.flur.extrachunky.platform.ExtraChunkySender;

import static dev.flur.extrachunky.platform.MessageFormatter.*;

/**
 * Command to register with a host server as a worker.
 * Usage: /extrachunky register <host:port>
 *        /extrachunky unregister
 */
public class RegisterCommand implements ExtraChunkyCommand {
    private static final int DEFAULT_PORT = 25580;

    private final ExtraChunkyCore core;

    public RegisterCommand(ExtraChunkyCore core) {
        this.core = core;
    }

    @Override
    public boolean execute(ExtraChunkySender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(prefix("Usage: " + highlight("/extrachunky register <host:port>")));
            sender.sendMessage(prefix("Example: " + highlight("/extrachunky register 192.168.1.100:25580")));
            return true;
        }

        // Check if hosting
        if (core.getHostServer() != null && core.getHostServer().isRunning()) {
            sender.sendMessage(prefix("Cannot register as worker while hosting. Use " + highlight("/extrachunky host stop") + " first."));
            return true;
        }

        // Parse host:port
        String hostArg = args[1];
        String hostAddress;
        int hostPort;

        if (hostArg.contains(":")) {
            String[] parts = hostArg.split(":", 2);
            hostAddress = parts[0];
            try {
                hostPort = Integer.parseInt(parts[1]);
                if (hostPort < 1 || hostPort > 65535) {
                    sender.sendMessage(prefix("Invalid port number: " + highlight(parts[1])));
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(prefix("Invalid port number: " + highlight(parts[1])));
                return true;
            }
        } else {
            hostAddress = hostArg;
            hostPort = DEFAULT_PORT;
        }

        // Check if already connected
        WorkerClient existingClient = core.getWorkerClient();
        if (existingClient != null && existingClient.isRunning()) {
            if (existingClient.getHostAddress().equals(hostAddress) && existingClient.getHostPort() == hostPort) {
                sender.sendMessage(prefix("Already connected to " + highlight(hostAddress + ":" + hostPort)));
                if (existingClient.isConnected()) {
                    sender.sendMessage(prefix("Status: Connected, ID=" + highlight(String.valueOf(existingClient.getAssignedId()))));
                } else {
                    sender.sendMessage(prefix("Status: Reconnecting..."));
                }
                return true;
            }
            // Disconnect from current host first
            core.stopWorkerClient();
        }

        sender.sendMessage(prefix("Connecting to host at " + highlight(hostAddress + ":" + hostPort) + "..."));

        // Start the worker client
        core.startWorkerClient(hostAddress, hostPort);
        WorkerClient client = core.getWorkerClient();

        if (client != null && client.isConnected()) {
            sender.sendMessage(prefix("Connected! Assigned ID: " + highlight(String.valueOf(client.getAssignedId()))));
            WorkerClient.ChunkAssignment assignment = client.getCurrentAssignment();
            if (assignment != null) {
                sender.sendMessage(prefix("Assignment: instance " + highlight(assignment.instanceId() +
                        "/" + assignment.totalInstances())));
            }
        } else if (client != null && client.isRunning()) {
            sender.sendMessage(prefix("Connection initiated. Will auto-reconnect if host is unavailable."));
        } else {
            sender.sendMessage(prefix("Failed to connect. Check the host address and try again."));
        }

        return true;
    }

    public boolean executeUnregister(ExtraChunkySender sender) {
        WorkerClient client = core.getWorkerClient();
        if (client == null || !client.isRunning()) {
            sender.sendMessage(prefix("Not currently registered with any host."));
            return true;
        }

        String hostInfo = client.getHostAddress() + ":" + client.getHostPort();
        core.stopWorkerClient();

        sender.sendMessage(prefix("Unregistered from " + highlight(hostInfo)));
        return true;
    }
}
