package dev.flur.extrachunky.platform;

/**
 * Utility class for formatting messages with colors.
 * Uses Minecraft color codes (&) that are translated by each platform's sender.
 */
public final class MessageFormatter {

    /**
     * Plugin prefix: dark aqua italic "Extra" + aqua "Chunky"
     */
    public static final String PREFIX = "&3&o&lExtra&bChunky&r";

    /**
     * Color for normal text (gray)
     */
    public static final String NORMAL = "&7";

    /**
     * Color for highlights (blue)
     */
    public static final String HIGHLIGHT = "&9";

    /**
     * Color for headers (aqua)
     */
    public static final String HEADER = "&b";

    /**
     * Reset formatting
     */
    public static final String RESET = "&r";

    private MessageFormatter() {}

    /**
     * Creates a message with the plugin prefix.
     *
     * @param message The message content
     * @return Formatted message with prefix
     */
    public static String prefix(String message) {
        return PREFIX + " " + NORMAL + message;
    }

    /**
     * Highlights a portion of text.
     *
     * @param text The text to highlight
     * @return Highlighted text that returns to normal after
     */
    public static String highlight(String text) {
        return HIGHLIGHT + text + NORMAL;
    }

    /**
     * Creates a header line.
     *
     * @param text The header text
     * @return Formatted header
     */
    public static String header(String text) {
        return HEADER + "=== " + text + " ===" + RESET;
    }

    /**
     * Creates a subheader/label with a value.
     *
     * @param label The label
     * @param value The value
     * @return Formatted label: value
     */
    public static String labelValue(String label, String value) {
        return NORMAL + label + ": " + HIGHLIGHT + value + NORMAL;
    }
}
