package dev.flur.extrachunky.platform;

import org.popcraft.chunky.Chunky;
import org.popcraft.chunky.api.ChunkyAPI;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Platform abstraction for server operations.
 * Each platform (Bukkit, Fabric, Forge, etc.) implements this interface.
 */
public interface ExtraChunkyPlatform {

    /**
     * Gets the Chunky instance from the platform's service manager.
     *
     * @return The Chunky instance, or empty if not found
     */
    Optional<Chunky> getChunky();

    /**
     * Gets the Chunky API instance.
     *
     * @return The ChunkyAPI instance, or empty if not available
     */
    default Optional<ChunkyAPI> getChunkyApi() {
        return getChunky().map(Chunky::getApi);
    }

    /**
     * Gets the console sender for broadcasting messages.
     *
     * @return The console sender
     */
    ExtraChunkySender getConsoleSender();

    /**
     * Gets the plugin/mod's data directory.
     *
     * @return Path to the data directory
     */
    Path getDataDirectory();

    /**
     * Gets the scheduler for this platform.
     *
     * @return The scheduler implementation
     */
    ExtraChunkyScheduler getScheduler();

    /**
     * Gets the logger for this platform.
     *
     * @return The logger implementation
     */
    ExtraChunkyLogger getLogger();

    /**
     * Gets the configuration for this platform.
     *
     * @return The config implementation
     */
    ExtraChunkyConfig getConfig();

    /**
     * Gets the path to a world's data folder.
     *
     * @param worldName The name of the world
     * @return Path to the world folder, or empty if world not found
     */
    Optional<Path> getWorldPath(String worldName);
}
