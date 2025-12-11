package dev.flur.extrachunky.platform;

import dev.flur.extrachunky.transfer.SshConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class BukkitConfig implements ExtraChunkyConfig {
    private final JavaPlugin plugin;
    private int hostPort;
    private boolean hostParticipates;
    private boolean manualStart;
    private SshConfig sshConfig;

    public BukkitConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    @Override
    public void saveDefaultConfig() {
        plugin.saveDefaultConfig();
    }

    @Override
    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.hostPort = config.getInt("host-port", DEFAULT_HOST_PORT);
        this.hostParticipates = config.getBoolean("host-participates", true);
        this.manualStart = config.getBoolean("manual-start", false);
        this.sshConfig = loadSshConfig(config);

        validate();
    }

    private SshConfig loadSshConfig(FileConfiguration config) {
        ConfigurationSection sftp = config.getConfigurationSection("sftp");
        if (sftp == null) {
            return SshConfig.disabled();
        }

        String authMethodStr = sftp.getString("auth-method", "key");
        SshConfig.AuthMethod authMethod = "password".equalsIgnoreCase(authMethodStr)
                ? SshConfig.AuthMethod.PASSWORD
                : SshConfig.AuthMethod.PUBLIC_KEY;

        return SshConfig.builder()
                .enabled(sftp.getBoolean("enabled", false))
                .hostname(sftp.getString("host", ""))
                .port(sftp.getInt("port", DEFAULT_SSH_PORT))
                .username(sftp.getString("username", ""))
                .authMethod(authMethod)
                .password(sftp.getString("password", ""))
                .privateKeyPath(sftp.getString("private-key-path", ""))
                .privateKeyPassphrase(sftp.getString("private-key-passphrase", ""))
                .remotePath(sftp.getString("remote-path", ""))
                .autoTransfer(sftp.getBoolean("auto-transfer", true))
                .retryCount(sftp.getInt("retry-count", DEFAULT_RETRY_COUNT))
                .compressionLevel(sftp.getInt("compression-level", DEFAULT_COMPRESSION_LEVEL))
                .build();
    }

    private void validate() {
        if (hostPort < 1 || hostPort > 65535) {
            plugin.getLogger().warning("Invalid host-port: " + hostPort + ". Must be between 1 and 65535");
            hostPort = DEFAULT_HOST_PORT;
        }

        if (sshConfig.enabled() && !sshConfig.isValid()) {
            plugin.getLogger().warning("SFTP is enabled but configuration is invalid. Check sftp settings in config.yml");
        }
    }

    @Override
    public int getHostPort() {
        return hostPort;
    }

    @Override
    public boolean isHostParticipates() {
        return hostParticipates;
    }

    @Override
    public SshConfig getSshConfig() {
        return sshConfig;
    }

    @Override
    public boolean isManualStart() {
        return manualStart;
    }
}
