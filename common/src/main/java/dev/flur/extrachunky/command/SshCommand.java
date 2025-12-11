package dev.flur.extrachunky.command;

import dev.flur.extrachunky.ExtraChunkyCore;
import dev.flur.extrachunky.platform.ExtraChunkySender;
import dev.flur.extrachunky.transfer.SftpTransferClient;
import dev.flur.extrachunky.transfer.SshConfig;

/**
 * Command for SSH/SFTP configuration testing.
 * Usage:
 *   /extrachunky ssh test - Test SSH connection to host
 *   /extrachunky ssh info - Show current SSH config (masked password)
 */
public class SshCommand implements ExtraChunkyCommand {
    private final ExtraChunkyCore core;

    public SshCommand(ExtraChunkyCore core) {
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
            case "test" -> testConnection(sender);
            case "info" -> showInfo(sender);
            default -> {
                showUsage(sender);
                yield true;
            }
        };
    }

    private boolean testConnection(ExtraChunkySender sender) {
        SshConfig config = core.getConfig().getSshConfig();

        if (!config.enabled()) {
            sender.sendMessage("SFTP is not enabled in config.");
            return true;
        }

        if (!config.isValid()) {
            sender.sendMessage("SFTP configuration is invalid:");
            validateConfig(sender, config);
            return true;
        }

        sender.sendMessage("Testing SSH connection to " + config.hostname() + ":" + config.port() + "...");

        // Run test async
        core.getScheduler().runTaskAsync(() -> {
            try (SftpTransferClient client = new SftpTransferClient(config, core.getLogger())) {
                String error = client.testConnection();
                if (error == null) {
                    sender.sendMessage("SSH connection successful!");
                    sender.sendMessage("Ready to transfer files to: " + config.remotePath());
                } else {
                    sender.sendMessage("SSH connection failed: " + error);
                }
            }
        });

        return true;
    }

    private boolean showInfo(ExtraChunkySender sender) {
        SshConfig config = core.getConfig().getSshConfig();

        sender.sendMessage("=== SSH/SFTP Configuration ===");
        sender.sendMessage("Enabled: " + config.enabled());

        if (!config.enabled()) {
            sender.sendMessage("");
            sender.sendMessage("Configure SFTP in config.yml to enable file transfers.");
            return true;
        }

        sender.sendMessage("Host: " + config.hostname());
        sender.sendMessage("Port: " + config.port());
        sender.sendMessage("Username: " + config.username());
        sender.sendMessage("Auth method: " + config.authMethod());

        if (config.authMethod() == SshConfig.AuthMethod.PASSWORD) {
            sender.sendMessage("Password: " + (config.password().isEmpty() ? "(not set)" : "****"));
        } else {
            sender.sendMessage("Private key: " + (config.privateKeyPath().isEmpty() ? "(not set)" : config.privateKeyPath()));
            sender.sendMessage("Key passphrase: " + (config.privateKeyPassphrase().isEmpty() ? "(not set)" : "****"));
        }

        sender.sendMessage("Remote path: " + config.remotePath());
        sender.sendMessage("");
        sender.sendMessage("Auto-transfer: " + config.autoTransfer());
        sender.sendMessage("Retry count: " + config.retryCount());
        sender.sendMessage("Compression level: " + config.compressionLevel());
        sender.sendMessage("");

        // Validation status
        if (config.isValid()) {
            sender.sendMessage("Status: Configuration valid");
        } else {
            sender.sendMessage("Status: Configuration INVALID");
            validateConfig(sender, config);
        }

        return true;
    }

    private void validateConfig(ExtraChunkySender sender, SshConfig config) {
        if (config.hostname() == null || config.hostname().isBlank()) {
            sender.sendMessage("  - Missing hostname");
        }
        if (config.port() < 1 || config.port() > 65535) {
            sender.sendMessage("  - Invalid port: " + config.port());
        }
        if (config.username() == null || config.username().isBlank()) {
            sender.sendMessage("  - Missing username");
        }
        if (config.authMethod() == SshConfig.AuthMethod.PASSWORD &&
                (config.password() == null || config.password().isBlank())) {
            sender.sendMessage("  - Password auth selected but password is empty");
        }
        if (config.authMethod() == SshConfig.AuthMethod.PUBLIC_KEY &&
                (config.privateKeyPath() == null || config.privateKeyPath().isBlank())) {
            sender.sendMessage("  - Public key auth selected but key path is empty");
        }
        if (config.remotePath() == null || config.remotePath().isBlank()) {
            sender.sendMessage("  - Missing remote path");
        }
    }

    private void showUsage(ExtraChunkySender sender) {
        sender.sendMessage("Usage: /extrachunky ssh <subcommand>");
        sender.sendMessage("  test - Test SSH connection to configured host");
        sender.sendMessage("  info - Show current SSH configuration");
    }
}
