package dev.flur.extrachunky.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FabricLogger implements ExtraChunkyLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger("ExtraChunky");

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
