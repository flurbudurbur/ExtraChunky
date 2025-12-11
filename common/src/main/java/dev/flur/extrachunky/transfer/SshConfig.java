package dev.flur.extrachunky.transfer;

/**
 * SSH connection configuration for SFTP transfers.
 * Workers use this to connect to the host server.
 */
public record SshConfig(
        String hostname,
        int port,
        String username,
        AuthMethod authMethod,
        String password,
        String privateKeyPath,
        String privateKeyPassphrase,
        String remotePath,
        boolean enabled,
        boolean autoTransfer,
        int retryCount,
        int compressionLevel
) {
    /**
     * Authentication method for SSH connection.
     */
    public enum AuthMethod {
        PASSWORD,
        PUBLIC_KEY
    }

    /**
     * Default SSH configuration (disabled).
     */
    public static SshConfig disabled() {
        return new SshConfig(
                "",
                22,
                "",
                AuthMethod.PUBLIC_KEY,
                "",
                "",
                "",
                "",
                false,
                true,
                3,
                3
        );
    }

    /**
     * Creates a builder for SshConfig.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Gets the remote path with world placeholder replaced.
     *
     * @param worldName The world name to substitute
     * @return The remote path with {world} replaced
     */
    public String getRemotePathForWorld(String worldName) {
        return remotePath.replace("{world}", worldName);
    }

    /**
     * Validates the configuration.
     *
     * @return true if the configuration is valid for use
     */
    public boolean isValid() {
        if (!enabled) {
            return true; // Disabled config is always valid
        }

        if (hostname == null || hostname.isBlank()) {
            return false;
        }
        if (port < 1 || port > 65535) {
            return false;
        }
        if (username == null || username.isBlank()) {
            return false;
        }
        if (authMethod == AuthMethod.PASSWORD && (password == null || password.isBlank())) {
            return false;
        }
        if (authMethod == AuthMethod.PUBLIC_KEY && (privateKeyPath == null || privateKeyPath.isBlank())) {
            return false;
        }
        if (remotePath == null || remotePath.isBlank()) {
            return false;
        }

        return true;
    }

    /**
     * Builder for SshConfig.
     */
    public static class Builder {
        private String hostname = "";
        private int port = 22;
        private String username = "";
        private AuthMethod authMethod = AuthMethod.PUBLIC_KEY;
        private String password = "";
        private String privateKeyPath = "";
        private String privateKeyPassphrase = "";
        private String remotePath = "";
        private boolean enabled = false;
        private boolean autoTransfer = true;
        private int retryCount = 3;
        private int compressionLevel = 3;

        public Builder hostname(String hostname) {
            this.hostname = hostname;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder authMethod(AuthMethod authMethod) {
            this.authMethod = authMethod;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder privateKeyPath(String privateKeyPath) {
            this.privateKeyPath = privateKeyPath;
            return this;
        }

        public Builder privateKeyPassphrase(String privateKeyPassphrase) {
            this.privateKeyPassphrase = privateKeyPassphrase;
            return this;
        }

        public Builder remotePath(String remotePath) {
            this.remotePath = remotePath;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder autoTransfer(boolean autoTransfer) {
            this.autoTransfer = autoTransfer;
            return this;
        }

        public Builder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public Builder compressionLevel(int compressionLevel) {
            this.compressionLevel = compressionLevel;
            return this;
        }

        public SshConfig build() {
            return new SshConfig(
                    hostname,
                    port,
                    username,
                    authMethod,
                    password,
                    privateKeyPath,
                    privateKeyPassphrase,
                    remotePath,
                    enabled,
                    autoTransfer,
                    retryCount,
                    compressionLevel
            );
        }
    }
}
