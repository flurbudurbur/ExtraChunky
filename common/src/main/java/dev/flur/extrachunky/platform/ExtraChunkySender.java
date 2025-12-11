package dev.flur.extrachunky.platform;

/**
 * Platform abstraction for sending messages.
 * Represents either a player or the console.
 */
public interface ExtraChunkySender {

    /**
     * Sends a plain message to this sender.
     *
     * @param message The message to send
     */
    void sendMessage(String message);

    /**
     * Gets the name of this sender.
     *
     * @return The sender's name (player name or "Console")
     */
    String getName();

    /**
     * Checks if this sender has a permission.
     *
     * @param permission The permission to check
     * @return true if the sender has the permission
     */
    boolean hasPermission(String permission);

    /**
     * Checks if this sender is a player.
     *
     * @return true if this is a player, false if console
     */
    boolean isPlayer();
}
