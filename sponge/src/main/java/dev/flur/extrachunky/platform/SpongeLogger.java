package dev.flur.extrachunky.platform;

import dev.flur.extrachunky.ExtraChunkySponge;
import org.apache.logging.log4j.Logger;

public class SpongeLogger implements ExtraChunkyLogger {
    private final Logger logger;

    public SpongeLogger(ExtraChunkySponge plugin) {
        this.logger = plugin.getLogger();
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warning(String message) {
        logger.warn(message);
    }

    @Override
    public void severe(String message) {
        logger.error(message);
    }

    @Override
    public void severe(String message, Throwable throwable) {
        logger.error(message, throwable);
    }
}
