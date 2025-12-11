package dev.flur.extrachunky.folia;

/**
 * Utility class for detecting Folia server.
 */
public final class Folia {
    private static final boolean IS_FOLIA;

    static {
        boolean isFolia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (ClassNotFoundException e) {
            isFolia = false;
        }
        IS_FOLIA = isFolia;
    }

    private Folia() {
    }

    /**
     * Checks if the server is running Folia.
     *
     * @return true if running on Folia
     */
    public static boolean isFolia() {
        return IS_FOLIA;
    }
}
