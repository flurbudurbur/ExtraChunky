package dev.flur.extrachunky.platform;

import dev.flur.extrachunky.ExtraChunkySponge;
import org.popcraft.chunky.Chunky;
import org.popcraft.chunky.api.ChunkyAPI;
import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.world.server.ServerWorld;

import java.nio.file.Path;
import java.util.Optional;

public class SpongePlatform implements ExtraChunkyPlatform {
    private final ExtraChunkySponge plugin;
    private final SpongeScheduler scheduler;
    private final SpongeLogger logger;
    private final SpongeConfig config;

    public SpongePlatform(ExtraChunkySponge plugin) {
        this.plugin = plugin;
        this.scheduler = new SpongeScheduler(plugin);
        this.logger = new SpongeLogger(plugin);
        this.config = new SpongeConfig(plugin);
    }

    @Override
    public Optional<Chunky> getChunky() {
        try {
            return Sponge.serviceProvider().provide(Chunky.class);
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
        return new SpongeSender(plugin.getGame().systemSubject());
    }

    @Override
    public Path getDataDirectory() {
        return plugin.getConfigPath();
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
        try {
            Optional<ServerWorld> world = plugin.getGame().server().worldManager().world(ResourceKey.resolve(worldName));
            if (world.isPresent()) {
                return Optional.of(world.get().directory());
            }
        } catch (Exception e) {
            // Ignore
        }

        // Fallback to default world
        for (ServerWorld world : plugin.getGame().server().worldManager().worlds()) {
            return Optional.of(world.directory());
        }
        return Optional.empty();
    }
}
