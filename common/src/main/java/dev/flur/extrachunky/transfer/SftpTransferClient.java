package dev.flur.extrachunky.transfer;

import dev.flur.extrachunky.platform.ExtraChunkyLogger;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.StreamCopier;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.sftp.SFTPException;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import net.schmizz.sshj.xfer.FileSystemFile;
import net.schmizz.sshj.xfer.TransferListener;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SFTP client for uploading compressed region files to the host server.
 * Uses SSHJ library for SSH/SFTP operations.
 */
public class SftpTransferClient implements Closeable {
    private final SshConfig config;
    private final ExtraChunkyLogger logger;

    private SSHClient sshClient;
    private SFTPClient sftpClient;

    /**
     * Creates a new SFTP transfer client.
     *
     * @param config SSH configuration
     * @param logger Logger for status messages
     */
    public SftpTransferClient(SshConfig config, ExtraChunkyLogger logger) {
        this.config = config;
        this.logger = logger;
    }

    /**
     * Establishes connection to the SSH server.
     *
     * @throws IOException if connection fails
     */
    public void connect() throws IOException {
        if (isConnected()) {
            return;
        }

        sshClient = new SSHClient();

        // Accept any host key (for Minecraft servers, strict key verification is often impractical)
        // Users can configure their known_hosts if needed
        sshClient.addHostKeyVerifier(new PromiscuousVerifier());

        logger.info("Connecting to " + config.hostname() + ":" + config.port() + "...");
        sshClient.connect(config.hostname(), config.port());

        // Authenticate based on config method
        if (config.authMethod() == SshConfig.AuthMethod.PASSWORD) {
            sshClient.authPassword(config.username(), config.password());
        } else {
            authenticateWithKey();
        }

        sftpClient = sshClient.newSFTPClient();
        logger.info("Connected to " + config.hostname());
    }

    private void authenticateWithKey() throws IOException {
        String keyPath = config.privateKeyPath();
        String passphrase = config.privateKeyPassphrase();

        KeyProvider keyProvider;
        if (passphrase != null && !passphrase.isEmpty()) {
            keyProvider = sshClient.loadKeys(keyPath, passphrase);
        } else {
            keyProvider = sshClient.loadKeys(keyPath);
        }

        sshClient.authPublickey(config.username(), keyProvider);
    }

    /**
     * Disconnects from the SSH server.
     */
    public void disconnect() {
        try {
            if (sftpClient != null) {
                sftpClient.close();
                sftpClient = null;
            }
            if (sshClient != null && sshClient.isConnected()) {
                sshClient.disconnect();
                sshClient = null;
            }
            logger.info("Disconnected from " + config.hostname());
        } catch (IOException e) {
            logger.warning("Error disconnecting: " + e.getMessage());
        }
    }

    /**
     * Checks if currently connected.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return sshClient != null && sshClient.isConnected() && sftpClient != null;
    }

    /**
     * Uploads a file to the remote server.
     *
     * @param localFile  Local file to upload
     * @param remotePath Remote destination path
     * @return Transfer result with statistics
     * @throws IOException if transfer fails
     */
    public TransferResult uploadFile(Path localFile, String remotePath) throws IOException {
        return uploadFile(localFile, remotePath, null);
    }

    /**
     * Uploads a file to the remote server with progress callback.
     *
     * @param localFile Local file to upload
     * @param remotePath Remote destination path
     * @param listener  Progress listener (can be null)
     * @return Transfer result with statistics
     * @throws IOException if transfer fails
     */
    public TransferResult uploadFile(Path localFile, String remotePath, TransferProgressListener listener)
            throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected to SSH server");
        }

        long startTime = System.currentTimeMillis();
        long fileSize = localFile.toFile().length();
        AtomicLong bytesTransferred = new AtomicLong(0);

        try {
            // Ensure parent directory exists
            String parentDir = getParentPath(remotePath);
            if (parentDir != null && !parentDir.isEmpty()) {
                createRemoteDirectories(parentDir);
            }

            // Set up progress tracking if listener provided
            if (listener != null) {
                sftpClient.getFileTransfer().setTransferListener(new TransferListener() {
                    @Override
                    public TransferListener directory(String name) {
                        return this;
                    }

                    @Override
                    public StreamCopier.Listener file(String name, long size) {
                        return new StreamCopier.Listener() {
                            @Override
                            public void reportProgress(long transferred) {
                                bytesTransferred.set(transferred);
                                listener.onProgress(transferred, size);
                            }
                        };
                    }
                });
            }

            sftpClient.put(new FileSystemFile(localFile.toFile()), remotePath);

            long duration = System.currentTimeMillis() - startTime;
            return new TransferResult(
                    localFile.toString(),
                    remotePath,
                    fileSize,
                    duration,
                    true,
                    null
            );
        } catch (IOException e) {
            long duration = System.currentTimeMillis() - startTime;
            return new TransferResult(
                    localFile.toString(),
                    remotePath,
                    bytesTransferred.get(),
                    duration,
                    false,
                    e.getMessage()
            );
        }
    }

    /**
     * Creates remote directories recursively.
     *
     * @param remotePath Remote directory path to create
     * @throws IOException if creation fails
     */
    public void createRemoteDirectory(String remotePath) throws IOException {
        createRemoteDirectories(remotePath);
    }

    private void createRemoteDirectories(String path) throws IOException {
        if (path == null || path.isEmpty()) {
            return;
        }

        // Check if already exists
        if (remotePathExists(path)) {
            return;
        }

        // Create parent first
        String parent = getParentPath(path);
        if (parent != null && !parent.isEmpty() && !parent.equals("/")) {
            createRemoteDirectories(parent);
        }

        // Create this directory
        try {
            sftpClient.mkdir(path);
        } catch (SFTPException e) {
            // Ignore if already exists (race condition)
            if (!remotePathExists(path)) {
                throw e;
            }
        }
    }

    /**
     * Checks if a remote file or directory exists.
     *
     * @param remotePath Path to check
     * @return true if the path exists
     * @throws IOException if check fails
     */
    public boolean remoteFileExists(String remotePath) throws IOException {
        return remotePathExists(remotePath);
    }

    private boolean remotePathExists(String path) throws IOException {
        try {
            FileAttributes attrs = sftpClient.stat(path);
            return attrs != null;
        } catch (SFTPException e) {
            return false;
        }
    }

    private String getParentPath(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        // Normalize path separators
        path = path.replace('\\', '/');

        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) {
            return lastSlash == 0 ? "/" : null;
        }
        return path.substring(0, lastSlash);
    }

    /**
     * Tests the SSH connection without transferring any files.
     *
     * @return null if successful, error message if failed
     */
    public String testConnection() {
        try {
            connect();
            // Try to stat the remote path
            String testPath = config.remotePath().replace("{world}", "test");
            try {
                sftpClient.stat("/");
            } catch (SFTPException e) {
                // Root stat failed, but connection is good
            }
            return null;
        } catch (IOException e) {
            return e.getMessage();
        } finally {
            disconnect();
        }
    }

    @Override
    public void close() {
        disconnect();
    }
}
