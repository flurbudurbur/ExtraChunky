package dev.flur.extrachunky.command;

import dev.flur.extrachunky.platform.ExtraChunkySender;

import java.util.List;

/**
 * Interface for all ExtraChunky commands.
 */
public interface ExtraChunkyCommand {

    /**
     * Executes this command.
     *
     * @param sender The command sender
     * @param args   Command arguments (including subcommand name at index 0)
     * @return true if the command was handled
     */
    boolean execute(ExtraChunkySender sender, String[] args);

    /**
     * Provides tab completions for this command.
     *
     * @param sender The command sender
     * @param args   Current arguments
     * @return List of completions
     */
    default List<String> tabComplete(ExtraChunkySender sender, String[] args) {
        return List.of();
    }
}
