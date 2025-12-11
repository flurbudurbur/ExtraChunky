package dev.flur.extrachunky.platform;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ForgeLogger implements ExtraChunkyLogger {
    private static final Logger LOGGER = LogManager.getLogger("ExtraChunky");

    @Override
    public void info(String message) {
        LOGGER.info(message);
    }

    @Override
    public void warning(String message) {
        LOGGER.warn(message);
    }

    @Override
    public void severe(String message) {
        LOGGER.error(message);
    }

    @Override
    public void severe(String message, Throwable throwable) {
        LOGGER.error(message, throwable);
    }
}
