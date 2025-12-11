package dev.flur.extrachunky.platform;

import dev.flur.extrachunky.ExtraChunkyBukkit;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.popcraft.chunky.Chunky;
import org.popcraft.chunky.api.ChunkyAPI;

import java.nio.file.Path;
import java.util.Optional;

public class BukkitPlatform implements ExtraChunkyPlatform {
    private final ExtraChunkyBukkit plugin;
    private final BukkitScheduler scheduler;
    private final BukkitLogger logger;
    private final BukkitConfig config;

    public BukkitPlatform(ExtraChunkyBukkit plugin) {
        this.plugin = plugin;
        this.scheduler = new BukkitScheduler(plugin);
        this.logger = new BukkitLogger(plugin.getLogger());
        this.config = new BukkitConfig(plugin);
    }

    @Override
    public Optional<Chunky> getChunky() {
        RegisteredServiceProvider<Chunky> provider =
                plugin.getServer().getServicesManager().getRegistration(Chunky.class);
        return Optional.ofNullable(provider).map(RegisteredServiceProvider::getProvider);
    }

    @Override
    public Optional<ChunkyAPI> getChunkyApi() {
        return getChunky().map(Chunky::getApi);
    }

    @Override
    public ExtraChunkySender getConsoleSender() {
        return new BukkitSender(plugin.getServer().getConsoleSender());
    }

    @Override
    public Path getDataDirectory() {
        return plugin.getDataFolder().toPath();
    }

    @Override
    public ExtraChunkyScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public ExtraChunkyLogger getLogger() {
        return logger;
    }

    @Override
    public ExtraChunkyConfig getConfig() {
        return config;
    }

    @Override
    public Optional<Path> getWorldPath(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return Optional.empty();
        }
        return Optional.of(world.getWorldFolder().toPath());
    }
}
