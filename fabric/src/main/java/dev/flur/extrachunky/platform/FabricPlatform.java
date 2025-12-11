package dev.flur.extrachunky.platform;

import dev.flur.extrachunky.ExtraChunkyFabric;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import org.popcraft.chunky.Chunky;
import org.popcraft.chunky.ChunkyProvider;
import org.popcraft.chunky.api.ChunkyAPI;

import java.nio.file.Path;
import java.util.Optional;

public class FabricPlatform implements ExtraChunkyPlatform {
    private final ExtraChunkyFabric mod;
    private final MinecraftServer server;
    private final FabricScheduler scheduler;
    private final FabricLogger logger;
    private final FabricConfig config;

    public FabricPlatform(ExtraChunkyFabric mod, MinecraftServer server) {
        this.mod = mod;
        this.server = server;
        this.scheduler = new FabricScheduler(server);
        this.logger = new FabricLogger();
        this.config = new FabricConfig();
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
        return new FabricSender(server.createCommandSourceStack());
    }

    @Override
    public Path getDataDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve("extrachunky");
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
            String dimensionName = level.dimension().identifier().toString();
            if (dimensionName.equals(worldName) ||
                    level.dimension().identifier().getPath().equals(worldName)) {
                Path worldPath = DimensionType.getStorageFolder(level.dimension(), server.getWorldPath(LevelResource.ROOT)).normalize();
                return Optional.of(worldPath);
            }
        }
        // Try default overworld
        ServerLevel overworld = server.overworld();
        if (overworld != null) {
            Path worldPath = DimensionType.getStorageFolder(overworld.dimension(), server.getWorldPath(LevelResource.ROOT)).normalize();
            return Optional.of(worldPath);
        }
        return Optional.empty();
    }
}
