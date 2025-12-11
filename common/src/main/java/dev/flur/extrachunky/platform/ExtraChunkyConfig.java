package dev.flur.extrachunky.platform;

import dev.flur.extrachunky.transfer.SshConfig;

/**
 * Platform abstraction for configuration.
 */
public interface ExtraChunkyConfig {

    /**
     * Default host port.
     */
    int DEFAULT_HOST_PORT = 25580;

    /**
     * Default SSH port.
     */
    int DEFAULT_SSH_PORT = 22;

    /**
     * Default ZSTD compression level (1-19, 3 is balanced).
     */
    int DEFAULT_COMPRESSION_LEVEL = 3;

    /**
     * Default retry count for failed transfers.
     */
    int DEFAULT_RETRY_COUNT = 3;

    /**
     * Saves the default config if it doesn't exist.
     */
    void saveDefaultConfig();

    /**
     * Reloads the configuration from disk.
     */
    void reload();

    /**
     * Gets the host port setting.
     *
     * @return The port to listen on when hosting
     */
    int getHostPort();

    /**
     * Whether the host participates as worker 0.
     *
     * @return true if the host should also generate chunks
     */
    boolean isHostParticipates();

    /**
     * Gets the SSH/SFTP configuration for file transfers.
     *
     * @return The SSH configuration
     */
    SshConfig getSshConfig();

    /**
     * Whether manual start mode is enabled.
     * When true, users must use /extrachunky start.
     * When false (default), /chunky start is automatically intercepted.
     *
     * @return true if manual start is required
     */
    boolean isManualStart();
}
