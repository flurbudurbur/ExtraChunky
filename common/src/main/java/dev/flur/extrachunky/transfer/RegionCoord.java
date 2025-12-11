package dev.flur.extrachunky.transfer;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents the coordinates of a Minecraft region file.
 * Region files are named r.X.Z.mca where X and Z are region coordinates.
 */
public record RegionCoord(int x, int z, String dimension) {
    private static final Pattern REGION_FILE_PATTERN = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    /**
     * Standard dimension identifiers.
     */
    public static final String OVERWORLD = "overworld";
    public static final String NETHER = "nether";
    public static final String END = "end";

    /**
     * Gets the filename for this region.
     *
     * @return Filename in format "r.X.Z.mca"
     */
    public String toFileName() {
        return "r." + x + "." + z + ".mca";
    }

    /**
     * Gets the relative path for this region within a world folder.
     *
     * @return Relative path including dimension folder if needed
     */
    public String toRelativePath() {
        String dimFolder = getDimensionFolder();
        if (dimFolder.isEmpty()) {
            return "region/" + toFileName();
        }
        return dimFolder + "/region/" + toFileName();
    }

    /**
     * Gets the dimension folder name.
     *
     * @return Folder name ("", "DIM-1", or "DIM1")
     */
    public String getDimensionFolder() {
        return switch (dimension) {
            case NETHER -> "DIM-1";
            case END -> "DIM1";
            default -> "";
        };
    }

    /**
     * Creates a RegionCoord from a filename.
     *
     * @param fileName Region filename (e.g., "r.0.0.mca")
     * @param dimension Dimension identifier
     * @return RegionCoord, or null if filename doesn't match pattern
     */
    public static RegionCoord fromFileName(String fileName, String dimension) {
        Matcher matcher = REGION_FILE_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            return null;
        }

        int x = Integer.parseInt(matcher.group(1));
        int z = Integer.parseInt(matcher.group(2));
        return new RegionCoord(x, z, dimension);
    }

    /**
     * Creates a RegionCoord from chunk coordinates.
     *
     * @param chunkX    Chunk X coordinate
     * @param chunkZ    Chunk Z coordinate
     * @param dimension Dimension identifier
     * @return RegionCoord containing this chunk
     */
    public static RegionCoord fromChunk(int chunkX, int chunkZ, String dimension) {
        return new RegionCoord(chunkX >> 5, chunkZ >> 5, dimension);
    }

    /**
     * Creates an Overworld region coordinate.
     */
    public static RegionCoord overworld(int x, int z) {
        return new RegionCoord(x, z, OVERWORLD);
    }

    /**
     * Creates a Nether region coordinate.
     */
    public static RegionCoord nether(int x, int z) {
        return new RegionCoord(x, z, NETHER);
    }

    /**
     * Creates an End region coordinate.
     */
    public static RegionCoord end(int x, int z) {
        return new RegionCoord(x, z, END);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegionCoord that = (RegionCoord) o;
        return x == that.x && z == that.z && Objects.equals(dimension, that.dimension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z, dimension);
    }

    @Override
    public String toString() {
        return dimension + "/r." + x + "." + z;
    }
}
