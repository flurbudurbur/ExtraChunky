package dev.flur.extrachunky.transfer;

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import dev.flur.extrachunky.platform.ExtraChunkyLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handles ZSTD compression and decompression of Minecraft region files.
 * Uses streaming to handle large files without loading into memory.
 */
public class RegionFileCompressor {
    private static final String ZSTD_EXTENSION = ".zst";
    private static final int BUFFER_SIZE = 8192;

    private final ExtraChunkyLogger logger;
    private final int compressionLevel;

    /**
     * Creates a new compressor with the specified compression level.
     *
     * @param logger           Logger for status messages
     * @param compressionLevel ZSTD compression level (1-19, higher = better compression, slower)
     */
    public RegionFileCompressor(ExtraChunkyLogger logger, int compressionLevel) {
        this.logger = logger;
        this.compressionLevel = Math.max(1, Math.min(19, compressionLevel));
    }

    /**
     * Compresses a region file using ZSTD.
     *
     * @param mcaFile   The source MCA file to compress
     * @param outputDir Directory to write the compressed file
     * @return Path to the compressed file
     * @throws IOException if compression fails
     */
    public Path compress(Path mcaFile, Path outputDir) throws IOException {
        String compressedName = getCompressedFileName(mcaFile.getFileName().toString());
        Path outputPath = outputDir.resolve(compressedName);

        Files.createDirectories(outputDir);

        long startTime = System.currentTimeMillis();
        long originalSize = Files.size(mcaFile);

        try (InputStream fis = Files.newInputStream(mcaFile);
             OutputStream fos = Files.newOutputStream(outputPath);
             ZstdOutputStream zstdOut = new ZstdOutputStream(fos, compressionLevel)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                zstdOut.write(buffer, 0, bytesRead);
            }
        }

        long compressedSize = Files.size(outputPath);
        long duration = System.currentTimeMillis() - startTime;
        float ratio = originalSize > 0 ? (float) compressedSize / originalSize * 100 : 0;

        logger.info(String.format("Compressed %s: %d KB -> %d KB (%.1f%%) in %dms",
                mcaFile.getFileName(), originalSize / 1024, compressedSize / 1024, ratio, duration));

        return outputPath;
    }

    /**
     * Decompresses a ZSTD-compressed region file.
     *
     * @param zstdFile  The compressed file to decompress
     * @param outputDir Directory to write the decompressed file
     * @return Path to the decompressed MCA file
     * @throws IOException if decompression fails
     */
    public Path decompress(Path zstdFile, Path outputDir) throws IOException {
        String originalName = getOriginalFileName(zstdFile.getFileName().toString());
        Path outputPath = outputDir.resolve(originalName);

        Files.createDirectories(outputDir);

        long startTime = System.currentTimeMillis();

        try (InputStream fis = Files.newInputStream(zstdFile);
             ZstdInputStream zstdIn = new ZstdInputStream(fis);
             OutputStream fos = Files.newOutputStream(outputPath)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = zstdIn.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }

        long decompressedSize = Files.size(outputPath);
        long duration = System.currentTimeMillis() - startTime;

        logger.info(String.format("Decompressed %s: %d KB in %dms",
                originalName, decompressedSize / 1024, duration));

        return outputPath;
    }

    /**
     * Gets the compressed filename for an MCA file.
     *
     * @param mcaFileName Original MCA filename (e.g., "r.0.0.mca")
     * @return Compressed filename (e.g., "r.0.0.mca.zst")
     */
    public String getCompressedFileName(String mcaFileName) {
        return mcaFileName + ZSTD_EXTENSION;
    }

    /**
     * Gets the original filename from a compressed file.
     *
     * @param compressedName Compressed filename (e.g., "r.0.0.mca.zst")
     * @return Original filename (e.g., "r.0.0.mca")
     */
    public String getOriginalFileName(String compressedName) {
        if (compressedName.endsWith(ZSTD_EXTENSION)) {
            return compressedName.substring(0, compressedName.length() - ZSTD_EXTENSION.length());
        }
        return compressedName;
    }

    /**
     * Checks if a file is a compressed region file.
     *
     * @param path Path to check
     * @return true if the file has .mca.zst extension
     */
    public boolean isCompressedRegionFile(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".mca" + ZSTD_EXTENSION);
    }

    /**
     * Gets the compression ratio for a file pair.
     *
     * @param originalSize   Size of the original file
     * @param compressedSize Size of the compressed file
     * @return Compression ratio as a percentage (e.g., 35.5 means 35.5% of original size)
     */
    public static float getCompressionRatio(long originalSize, long compressedSize) {
        if (originalSize == 0) return 0;
        return (float) compressedSize / originalSize * 100;
    }
}
