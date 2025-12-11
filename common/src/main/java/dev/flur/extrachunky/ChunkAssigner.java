package dev.flur.extrachunky;

public class ChunkAssigner {
    private final int instanceId;
    private final int totalInstances;

    public ChunkAssigner(int instanceId, int totalInstances) {
        this.instanceId = instanceId;
        this.totalInstances = totalInstances;
    }

    /**
     * Determines if this instance should process the given region based on spiral index distribution.
     *
     * @param regionX       Region X coordinate
     * @param regionZ       Region Z coordinate
     * @param centerRegionX Center region X coordinate
     * @param centerRegionZ Center region Z coordinate
     * @return true if this instance should process this region
     */
    public boolean shouldProcessRegion(int regionX, int regionZ, int centerRegionX, int centerRegionZ) {
        long index = spiralIndex(regionX, regionZ, centerRegionX, centerRegionZ);
        return (index % totalInstances) == (instanceId - 1);
    }

    /**
     * Calculates the spiral index of a position relative to a center point.
     * The spiral starts at the center (index 0) and expands outward in a square pattern.
     * <p>
     * The spiral order:
     * - Layer 0: center only (index 0)
     * - Layer 1: 8 positions around center (indices 1-8)
     * - Layer 2: 16 positions (indices 9-24)
     * - etc.
     *
     * @param x       X coordinate
     * @param z       Z coordinate
     * @param centerX Center X coordinate
     * @param centerZ Center Z coordinate
     * @return The spiral index of the position
     */
    public static long spiralIndex(int x, int z, int centerX, int centerZ) {
        int dx = x - centerX;
        int dz = z - centerZ;
        int layer = Math.max(Math.abs(dx), Math.abs(dz));

        if (layer == 0) {
            return 0;
        }

        // Calculate base index: sum of all positions in inner layers
        // Layer n has perimeter of 8*n positions
        // Inner layers (0 to layer-1) contain: 1 + 8 + 16 + ... + 8*(layer-1) = (2*layer-1)^2 positions
        long innerSide = 2L * layer - 1;
        long baseIndex = innerSide * innerSide;

        // Walk perimeter clockwise starting from top-right corner (layer, layer)
        // Right edge: top to bottom (z from layer to -layer)
        if (dx == layer) {
            return baseIndex + (layer - dz);
        }
        // Bottom edge: right to left (x from layer to -layer)
        else if (dz == -layer) {
            return baseIndex + 2L * layer + (layer - dx);
        }
        // Left edge: bottom to top (z from -layer to layer)
        else if (dx == -layer) {
            return baseIndex + 4L * layer + (dz + layer);
        }
        // Top edge: left to right (x from -layer to layer)
        else {
            return baseIndex + 6L * layer + (dx + layer);
        }
    }

    public int getInstanceId() {
        return instanceId;
    }

    public int getTotalInstances() {
        return totalInstances;
    }
}
