package dev.flur.extrachunky;

import dev.flur.extrachunky.platform.ExtraChunkyLogger;
import dev.flur.extrachunky.transfer.RegionCoord;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class CsvGenerator {
    private final ExtraChunkyLogger logger;

    public CsvGenerator(ExtraChunkyLogger logger) {
        this.logger = logger;
    }

    /**
     * Generates a CSV file containing only the chunks this instance should process.
     *
     * @param world      World name
     * @param centerX    Center X coordinate
     * @param centerZ    Center Z coordinate
     * @param radius     Generation radius
     * @param shape      Shape (square, circle, etc.)
     * @param instanceId Instance ID for filename
     * @param assigner   Chunk assignment calculator
     * @param outputDir  Directory to write the CSV file
     * @return Generation result with path and chunk count
     * @throws IOException if file operations fail
     */
    public GenerationResult generateChunkCsv(String world, double centerX, double centerZ,
                                             double radius, String shape, int instanceId,
                                             ChunkAssigner assigner, Path outputDir) throws IOException {
        String fileName = "extrachunky_" + instanceId + ".csv";
        Path csvPath = outputDir.resolve(fileName);

        // Calculate chunk bounds
        int centerChunkX = (int) Math.floor(centerX) >> 4;
        int centerChunkZ = (int) Math.floor(centerZ) >> 4;
        int radiusChunks = (int) Math.ceil(radius / 16.0);

        // Calculate center region for spiral indexing
        int centerRegionX = centerChunkX >> 5;
        int centerRegionZ = centerChunkZ >> 5;

        long chunkCount = 0;
        long totalChunks = 0;
        Set<RegionCoord> assignedRegions = new HashSet<>();

        Files.createDirectories(outputDir);

        try (BufferedWriter writer = Files.newBufferedWriter(csvPath)) {
            for (int cx = centerChunkX - radiusChunks; cx <= centerChunkX + radiusChunks; cx++) {
                for (int cz = centerChunkZ - radiusChunks; cz <= centerChunkZ + radiusChunks; cz++) {
                    // Check if chunk is within shape
                    if (!isInShape(cx, cz, centerChunkX, centerChunkZ, radiusChunks, shape)) {
                        continue;
                    }

                    totalChunks++;

                    // Check if this instance should process this chunk's region
                    int regionX = cx >> 5;
                    int regionZ = cz >> 5;
                    if (assigner.shouldProcessRegion(regionX, regionZ, centerRegionX, centerRegionZ)) {
                        writer.write(cx + "," + cz);
                        writer.newLine();
                        chunkCount++;

                        // Track the region for transfer
                        assignedRegions.add(RegionCoord.overworld(regionX, regionZ));
                    }
                }
            }
        }

        logger.info("Generated " + chunkCount + " chunk entries in " + csvPath.getFileName() +
                " (" + assignedRegions.size() + " regions)");
        return new GenerationResult(csvPath, fileName, chunkCount, totalChunks, assignedRegions);
    }

    /**
     * Checks if a chunk coordinate is within the specified shape.
     */
    private boolean isInShape(int chunkX, int chunkZ, int centerChunkX, int centerChunkZ, int radiusChunks, String shape) {
        int dx = chunkX - centerChunkX;
        int dz = chunkZ - centerChunkZ;

        return switch (shape.toLowerCase()) {
            case "circle", "ellipse" -> {
                // For circle, check if chunk center is within radius
                double distance = Math.sqrt(dx * dx + dz * dz);
                yield distance <= radiusChunks;
            }
            case "rectangle", "square" -> {
                // For square/rectangle, check if within bounds
                yield Math.abs(dx) <= radiusChunks && Math.abs(dz) <= radiusChunks;
            }
            case "diamond" -> {
                // For diamond, use Manhattan distance
                yield Math.abs(dx) + Math.abs(dz) <= radiusChunks;
            }
            case "pentagon", "star" -> {
                // Fallback to square for complex shapes
                yield Math.abs(dx) <= radiusChunks && Math.abs(dz) <= radiusChunks;
            }
            default -> {
                // Default to square
                yield Math.abs(dx) <= radiusChunks && Math.abs(dz) <= radiusChunks;
            }
        };
    }

    public record GenerationResult(Path csvPath, String fileName, long chunkCount, long totalChunks, Set<RegionCoord> assignedRegions) {
    }
}
