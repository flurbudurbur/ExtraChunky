package dev.flur.extrachunky.paper;

/**
 * Utility class for detecting Paper server.
 */
public final class Paper {
    private static final boolean IS_PAPER;

    static {
        boolean isPaper;
        try {
            Class.forName("io.papermc.paper.chunk.system.scheduling.ChunkHolderManager");
            isPaper = true;
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("com.destroystokyo.paper.PaperConfig");
                isPaper = true;
            } catch (ClassNotFoundException ex) {
                isPaper = false;
            }
        }
        IS_PAPER = isPaper;
    }

    private Paper() {
    }

    /**
     * Checks if the server is running Paper.
     *
     * @return true if running on Paper
     */
    public static boolean isPaper() {
        return IS_PAPER;
    }
}
