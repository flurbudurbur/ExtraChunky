package dev.flur.extrachunky.command;

import dev.flur.extrachunky.ExtraChunkyCore;
import dev.flur.extrachunky.RegionMerger;
import dev.flur.extrachunky.platform.ExtraChunkySender;
import org.popcraft.chunky.Selection;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.flur.extrachunky.platform.MessageFormatter.*;

public class MergeCommand implements ExtraChunkyCommand {
    private final ExtraChunkyCore core;

    public MergeCommand(ExtraChunkyCore core) {
        this.core = core;
    }

    @Override
    public boolean execute(ExtraChunkySender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(prefix("Usage: " + highlight("/extrachunky merge <source1> [source2] ...")));
            sender.sendMessage(prefix("Merges region files from source world directories into the current world."));
            return true;
        }

        // Get target world from Chunky's selection
        Selection selection = core.getSelection();
        String worldName = selection.world().getName();

        Optional<Path> worldPathOpt = core.getPlatform().getWorldPath(worldName);

        if (worldPathOpt.isEmpty()) {
            sender.sendMessage(prefix("World '" + highlight(worldName) + "' not found!"));
            return true;
        }

        Path targetWorld = worldPathOpt.get();

        // Parse source paths
        List<Path> sourcePaths = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            sourcePaths.add(Paths.get(args[i]));
        }

        RegionMerger merger = new RegionMerger(core.getPlatform().getLogger());

        // Validate sources
        List<String> validationErrors = merger.validateSources(sourcePaths);
        if (!validationErrors.isEmpty()) {
            sender.sendMessage(prefix("&cValidation errors:" + NORMAL));
            for (String error : validationErrors) {
                sender.sendMessage(NORMAL + "  - " + error);
            }
            return true;
        }

        sender.sendMessage(prefix("Scanning " + highlight(sourcePaths.size() + "") + " source directories..."));

        // Count files to merge
        int newFiles = merger.countNewFiles(targetWorld, sourcePaths);
        sender.sendMessage(prefix("Found " + highlight(newFiles + "") + " new region files to merge"));

        if (newFiles == 0) {
            sender.sendMessage(prefix("No new files to merge. All regions already exist in target."));
            return true;
        }

        // Perform merge asynchronously
        core.getPlatform().getScheduler().runTaskAsync(() -> {
            sender.sendMessage(prefix("Starting merge..."));

            RegionMerger.MergeResult result = merger.merge(targetWorld, sourcePaths);

            // Report results on main thread
            core.getPlatform().getScheduler().runTask(() -> {
                sender.sendMessage(prefix("Merged " + highlight(result.merged() + "") + " region files"));

                if (result.skipped() > 0) {
                    sender.sendMessage(prefix("Skipped " + highlight(result.skipped() + "") + " existing files"));
                }

                if (result.hasConflicts()) {
                    sender.sendMessage(prefix("&eConflicts detected (" + result.conflicts().size() + "):" + NORMAL));
                    int shown = 0;
                    for (String conflict : result.conflicts()) {
                        if (shown++ < 5) {
                            sender.sendMessage(NORMAL + "  - " + conflict);
                        }
                    }
                    if (result.conflicts().size() > 5) {
                        sender.sendMessage(NORMAL + "  ... and " + (result.conflicts().size() - 5) + " more");
                    }
                }

                if (result.hasErrors()) {
                    sender.sendMessage(prefix("&cErrors during merge:" + NORMAL));
                    for (String error : result.errors()) {
                        sender.sendMessage(NORMAL + "  - " + error);
                    }
                }

                if (result.isSuccess()) {
                    sender.sendMessage(prefix("&aDone!" + NORMAL + " World merge completed."));
                } else {
                    sender.sendMessage(prefix("&eMerge completed with errors." + NORMAL + " Check console for details."));
                }
            });
        });

        return true;
    }
}
