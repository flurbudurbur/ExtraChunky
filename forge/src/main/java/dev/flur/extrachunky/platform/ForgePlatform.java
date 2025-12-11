package dev.flur.extrachunky.platform;

import dev.flur.extrachunky.ExtraChunkyForge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLPaths;
import org.popcraft.chunky.Chunky;
import org.popcraft.chunky.ChunkyProvider;
import org.popcraft.chunky.api.ChunkyAPI;

import java.nio.file.Path;
import java.util.Optional;

public class ForgePlatform implements ExtraChunkyPlatform {
    private final ExtraChunkyForge mod;
    private final MinecraftServer server;
    private final ForgeScheduler scheduler;
    private final ForgeLogger logger;
    private final ForgeConfig config;

    public ForgePlatform(ExtraChunkyForge mod, MinecraftServer server) {
        this.mod = mod;
        this.server = server;
        this.scheduler = new ForgeScheduler(server);
        this.logger = new ForgeLogger();
        this.config = new ForgeConfig();
    }

    @Override
    public Optional<Chunky> getChunky() {
        try {
            return Optional.ofNullable(ChunkyProvider.get());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ChunkyAPI> getChunkyApi() {
        return getChunky().map(Chunky::getApi);
    }

    @Override
    public ExtraChunkySender getConsoleSender() {
        return new ForgeSender(server.createCommandSourceStack());
    }

    @Override
    public Path getDataDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve("extrachunky");
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
        for (ServerLevel level : server.getAllLevels()) {
            String dimensionName = level.dimension().location().toString();
            if (dimensionName.equals(worldName) ||
                    level.dimension().location().getPath().equals(worldName)) {
                Path worldPath = DimensionType.getStorageFolder(level.dimension(), server.getWorldPath(LevelResource.ROOT)).normalize();
                return Optional.of(worldPath);
            }
        }
        ServerLevel overworld = server.overworld();
        if (overworld != null) {
            Path worldPath = DimensionType.getStorageFolder(overworld.dimension(), server.getWorldPath(LevelResource.ROOT)).normalize();
            return Optional.of(worldPath);
        }
        return Optional.empty();
    }
}
