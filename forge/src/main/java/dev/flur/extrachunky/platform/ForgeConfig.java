package dev.flur.extrachunky.platform;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.flur.extrachunky.transfer.SshConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ForgeConfig implements ExtraChunkyConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configPath;
    private int hostPort = DEFAULT_HOST_PORT;
    private boolean hostParticipates = true;
    private boolean manualStart = false;
    private SshConfig sshConfig = SshConfig.disabled();

    public ForgeConfig() {
        this.configPath = FMLPaths.CONFIGDIR.get().resolve("extrachunky.json");
        reload();
    }

    @Override
    public void saveDefaultConfig() {
        if (!Files.exists(configPath)) {
            save();
        }
    }

    @Override
    public void reload() {
        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                ConfigData data = GSON.fromJson(reader, ConfigData.class);
                if (data != null) {
                    this.hostPort = data.hostPort;
                    this.hostParticipates = data.hostParticipates;
                    this.manualStart = data.manualStart;
                    this.sshConfig = data.toSshConfig();
                }
            } catch (IOException e) {
                // Use defaults
            }
        } else {
            saveDefaultConfig();
        }
        validate();
    }

    private void save() {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                ConfigData data = new ConfigData();
                data.hostPort = this.hostPort;
                data.hostParticipates = this.hostParticipates;
                data.manualStart = this.manualStart;
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    private void validate() {
        if (hostPort < 1 || hostPort > 65535) {
            hostPort = DEFAULT_HOST_PORT;
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

    private static class ConfigData {
        int hostPort = DEFAULT_HOST_PORT;
        boolean hostParticipates = true;
        boolean manualStart = false;
        SftpConfigData sftp = new SftpConfigData();

        SshConfig toSshConfig() {
            if (sftp == null) {
                return SshConfig.disabled();
            }
            return sftp.toSshConfig();
        }
    }

    private static class SftpConfigData {
        boolean enabled = false;
        String host = "";
        int port = DEFAULT_SSH_PORT;
        String username = "";
        String authMethod = "key";
        String password = "";
        String privateKeyPath = "";
        String privateKeyPassphrase = "";
        String remotePath = "";
        boolean autoTransfer = true;
        int retryCount = DEFAULT_RETRY_COUNT;
        int compressionLevel = DEFAULT_COMPRESSION_LEVEL;

        SshConfig toSshConfig() {
            SshConfig.AuthMethod auth = "password".equalsIgnoreCase(authMethod)
                    ? SshConfig.AuthMethod.PASSWORD
                    : SshConfig.AuthMethod.PUBLIC_KEY;

            return SshConfig.builder()
                    .enabled(enabled)
                    .hostname(host)
                    .port(port)
                    .username(username)
                    .authMethod(auth)
                    .password(password)
                    .privateKeyPath(privateKeyPath)
                    .privateKeyPassphrase(privateKeyPassphrase)
                    .remotePath(remotePath)
                    .autoTransfer(autoTransfer)
                    .retryCount(retryCount)
                    .compressionLevel(compressionLevel)
                    .build();
        }
    }
}
