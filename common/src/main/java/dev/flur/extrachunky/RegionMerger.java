package dev.flur.extrachunky;

import dev.flur.extrachunky.platform.ExtraChunkyLogger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class RegionMerger {
    private static final String[] DIMENSIONS = {"region", "DIM-1/region", "DIM1/region"};
    private static final String[] DIMENSION_NAMES = {"Overworld", "Nether", "End"};

    private final ExtraChunkyLogger logger;

    public RegionMerger(ExtraChunkyLogger logger) {
        this.logger = logger;
    }

    /**
     * Merges region files from multiple source worlds into a target world.
     *
     * @param targetWorld  Path to the target world directory
     * @param sourceWorlds List of paths to source world directories
     * @return Result of the merge operation
     */
    public MergeResult merge(Path targetWorld, List<Path> sourceWorlds) {
        int merged = 0;
        int skipped = 0;
        List<String> conflicts = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < DIMENSIONS.length; i++) {
            String dim = DIMENSIONS[i];
            String dimName = DIMENSION_NAMES[i];
            Path targetDir = targetWorld.resolve(dim);

            try {
                Files.createDirectories(targetDir);
            } catch (IOException e) {
                errors.add("Failed to create directory: " + targetDir);
                logger.warning("Failed to create target directory: " + targetDir);
                continue;
            }

            for (Path source : sourceWorlds) {
                Path sourceDir = source.resolve(dim);
                if (!Files.exists(sourceDir)) {
                    continue;
                }

                try (DirectoryStream<Path> stream = Files.newDirectoryStream(sourceDir, "*.mca")) {
                    for (Path mcaFile : stream) {
                        Path targetFile = targetDir.resolve(mcaFile.getFileName());
                        String fileName = mcaFile.getFileName().toString();

                        if (Files.exists(targetFile)) {
                            // Check if files are the same size (basic conflict detection)
                            long sourceSize = Files.size(mcaFile);
                            long targetSize = Files.size(targetFile);

                            if (sourceSize != targetSize) {
                                conflicts.add(dimName + "/" + fileName + " (sizes differ: " + sourceSize + " vs " + targetSize + ")");
                            }
                            skipped++;
                        } else {
                            try {
                                Files.copy(mcaFile, targetFile, StandardCopyOption.COPY_ATTRIBUTES);
                                merged++;
                            } catch (IOException e) {
                                errors.add("Failed to copy: " + mcaFile + " -> " + targetFile);
                                logger.warning("Failed to copy region file: " + e.getMessage());
                            }
                        }
                    }
                } catch (IOException e) {
                    errors.add("Failed to list files in: " + sourceDir);
                    logger.warning("Failed to list source directory: " + sourceDir);
                }
            }
        }

        return new MergeResult(merged, skipped, conflicts, errors);
    }

    /**
     * Validates source directories exist and contain world data.
     *
     * @param paths List of paths to validate
     * @return List of validation errors (empty if all valid)
     */
    public List<String> validateSources(List<Path> paths) {
        List<String> errors = new ArrayList<>();

        for (Path path : paths) {
            if (!Files.exists(path)) {
                errors.add("Path does not exist: " + path);
                continue;
            }
            if (!Files.isDirectory(path)) {
                errors.add("Path is not a directory: " + path);
                continue;
            }

            // Check for at least one dimension folder
            boolean hasRegions = false;
            for (String dim : DIMENSIONS) {
                Path dimPath = path.resolve(dim);
                if (Files.exists(dimPath) && Files.isDirectory(dimPath)) {
                    hasRegions = true;
                    break;
                }
            }

            if (!hasRegions) {
                errors.add("No region folders found in: " + path);
            }
        }

        return errors;
    }

    /**
     * Counts the number of region files that would be merged.
     *
     * @param targetWorld  Target world path
     * @param sourceWorlds Source world paths
     * @return Count of new files that would be copied
     */
    public int countNewFiles(Path targetWorld, List<Path> sourceWorlds) {
        int count = 0;

        for (String dim : DIMENSIONS) {
            Path targetDir = targetWorld.resolve(dim);

            for (Path source : sourceWorlds) {
                Path sourceDir = source.resolve(dim);
                if (!Files.exists(sourceDir)) {
                    continue;
                }

                try (DirectoryStream<Path> stream = Files.newDirectoryStream(sourceDir, "*.mca")) {
                    for (Path mcaFile : stream) {
                        Path targetFile = targetDir.resolve(mcaFile.getFileName());
                        if (!Files.exists(targetFile)) {
                            count++;
                        }
                    }
                } catch (IOException e) {
                    // Ignore for counting
                }
            }
        }

        return count;
    }

    public record MergeResult(
            int merged,
            int skipped,
            List<String> conflicts,
            List<String> errors
    ) {
        public boolean hasConflicts() {
            return !conflicts.isEmpty();
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public boolean isSuccess() {
            return errors.isEmpty();
        }
    }
}
