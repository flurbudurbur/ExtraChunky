package dev.flur.extrachunky.command;

import dev.flur.extrachunky.ExtraChunkyCore;
import dev.flur.extrachunky.platform.ExtraChunkySender;
import dev.flur.extrachunky.transfer.RegionTransferManager;
import dev.flur.extrachunky.transfer.SshConfig;
import dev.flur.extrachunky.transfer.TransferSummary;

import java.text.DecimalFormat;

/**
 * Command for managing SFTP file transfers.
 * Usage:
 *   /extrachunky transfer start  - Start/resume transfers
 *   /extrachunky transfer status - Show transfer status
 *   /extrachunky transfer cancel - Cancel ongoing transfers
 *   /extrachunky transfer retry  - Retry failed transfers
 *   /extrachunky transfer clear  - Clear completed/failed transfers
 */
public class TransferCommand implements ExtraChunkyCommand {
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,###");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.0");

    private final ExtraChunkyCore core;

    public TransferCommand(ExtraChunkyCore core) {
        this.core = core;
    }

    @Override
    public boolean execute(ExtraChunkySender sender, String[] args) {
        if (args.length == 0) {
            showUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        return switch (subCommand) {
            case "start" -> startTransfer(sender);
            case "status" -> showStatus(sender);
            case "cancel" -> cancelTransfer(sender);
            case "retry" -> retryTransfer(sender);
            case "clear" -> clearTransfer(sender);
            default -> {
                showUsage(sender);
                yield true;
            }
        };
    }

    private boolean startTransfer(ExtraChunkySender sender) {
        SshConfig sshConfig = core.getConfig().getSshConfig();

        if (!sshConfig.enabled()) {
            sender.sendMessage("SFTP transfers are not enabled.");
            sender.sendMessage("Configure 'sftp.enabled: true' in config.yml");
            return true;
        }

        if (!sshConfig.isValid()) {
            sender.sendMessage("SFTP configuration is invalid. Check config.yml");
            return true;
        }

        RegionTransferManager manager = core.getTransferManager();
        if (manager == null) {
            sender.sendMessage("Transfer manager not initialized.");
            return true;
        }

        if (manager.isRunning()) {
            sender.sendMessage("Transfers already in progress.");
            return true;
        }

        TransferSummary summary = manager.getStatus();
        if (summary.totalRegions() == 0) {
            sender.sendMessage("No regions queued for transfer.");
            sender.sendMessage("Transfers are queued automatically when generation completes.");
            return true;
        }

        sender.sendMessage("Starting transfer of " + summary.pending() + " regions...");
        manager.startTransferAll();
        return true;
    }

    private boolean showStatus(ExtraChunkySender sender) {
        SshConfig sshConfig = core.getConfig().getSshConfig();

        sender.sendMessage("=== Transfer Status ===");
        sender.sendMessage("SFTP: " + (sshConfig.enabled() ? "ENABLED" : "DISABLED"));

        if (!sshConfig.enabled()) {
            sender.sendMessage("");
            sender.sendMessage("Enable SFTP in config.yml to transfer region files.");
            return true;
        }

        sender.sendMessage("Host: " + sshConfig.hostname() + ":" + sshConfig.port());
        sender.sendMessage("Remote path: " + sshConfig.remotePath());
        sender.sendMessage("");

        RegionTransferManager manager = core.getTransferManager();
        if (manager == null) {
            sender.sendMessage("Transfer manager not initialized.");
            return true;
        }

        TransferSummary summary = manager.getStatus();

        if (summary.totalRegions() == 0) {
            sender.sendMessage("No transfers in queue.");
            return true;
        }

        sender.sendMessage("Status: " + (manager.isRunning() ? "RUNNING" : "IDLE"));
        sender.sendMessage("Progress: " + summary.completed() + "/" + summary.totalRegions() +
                " regions (" + PERCENT_FORMAT.format(summary.getPercentComplete()) + "%)");

        if (summary.inProgress() > 0) {
            sender.sendMessage("In progress: " + summary.inProgress());
            if (summary.totalBytes() > 0) {
                sender.sendMessage("Bytes: " + formatBytes(summary.bytesTransferred()) +
                        "/" + formatBytes(summary.totalBytes()));
            }
        }

        if (summary.pending() > 0) {
            sender.sendMessage("Pending: " + summary.pending());
        }

        if (summary.failed() > 0) {
            sender.sendMessage("Failed: " + summary.failed() + " (use /extrachunky transfer retry)");
        }

        return true;
    }

    private boolean cancelTransfer(ExtraChunkySender sender) {
        RegionTransferManager manager = core.getTransferManager();
        if (manager == null) {
            sender.sendMessage("Transfer manager not initialized.");
            return true;
        }

        if (!manager.isRunning()) {
            sender.sendMessage("No transfers are running.");
            return true;
        }

        manager.cancel();
        sender.sendMessage("Cancelling transfers...");
        return true;
    }

    private boolean retryTransfer(ExtraChunkySender sender) {
        RegionTransferManager manager = core.getTransferManager();
        if (manager == null) {
            sender.sendMessage("Transfer manager not initialized.");
            return true;
        }

        int count = manager.retryFailed();
        if (count > 0) {
            sender.sendMessage("Retrying " + count + " failed transfers...");
        } else {
            sender.sendMessage("No failed transfers to retry.");
        }
        return true;
    }

    private boolean clearTransfer(ExtraChunkySender sender) {
        RegionTransferManager manager = core.getTransferManager();
        if (manager == null) {
            sender.sendMessage("Transfer manager not initialized.");
            return true;
        }

        if (manager.isRunning()) {
            sender.sendMessage("Cannot clear while transfers are running. Cancel first.");
            return true;
        }

        manager.clearCompleted();
        sender.sendMessage("Cleared completed and failed transfers from queue.");
        return true;
    }

    private void showUsage(ExtraChunkySender sender) {
        sender.sendMessage("Usage: /extrachunky transfer <subcommand>");
        sender.sendMessage("  start  - Start/resume transfers");
        sender.sendMessage("  status - Show transfer status");
        sender.sendMessage("  cancel - Cancel ongoing transfers");
        sender.sendMessage("  retry  - Retry failed transfers");
        sender.sendMessage("  clear  - Clear completed/failed from queue");
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return DECIMAL_FORMAT.format(bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return DECIMAL_FORMAT.format(bytes / (1024 * 1024)) + " MB";
        return DECIMAL_FORMAT.format(bytes / (1024 * 1024 * 1024)) + " GB";
    }
}
