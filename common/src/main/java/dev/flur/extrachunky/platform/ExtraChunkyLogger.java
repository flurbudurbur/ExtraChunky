package dev.flur.extrachunky.platform;

/**
 * Platform abstraction for logging.
 */
public interface ExtraChunkyLogger {

    /**
     * Logs an info message.
     *
     * @param message The message to log
     */
    void info(String message);

    /**
     * Logs a warning message.
     *
     * @param message The message to log
     */
    void warning(String message);

    /**
     * Logs a severe/error message.
     *
     * @param message The message to log
     */
    void severe(String message);

    /**
     * Logs a severe/error message with an exception.
     *
     * @param message   The message to log
     * @param throwable The exception to log
     */
    void severe(String message, Throwable throwable);
}
