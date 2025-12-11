package dev.flur.extrachunky.transfer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.flur.extrachunky.platform.ExtraChunkyLogger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent queue for region file transfers.
 * Saves state to disk to survive server restarts.
 */
public class TransferQueue {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String QUEUE_FILE = "transfer-queue.json";

    private final Path queuePath;
    private final ExtraChunkyLogger logger;
    private final Map<RegionCoord, TransferState> states = new ConcurrentHashMap<>();
    private String worldName;

    /**
     * Creates a new transfer queue.
     *
     * @param dataDirectory Plugin data directory
     * @param logger        Logger for status messages
     */
    public TransferQueue(Path dataDirectory, ExtraChunkyLogger logger) {
        this.queuePath = dataDirectory.resolve(QUEUE_FILE);
        this.logger = logger;
    }

    /**
     * Loads the queue from disk.
     */
    public void load() {
        if (!Files.exists(queuePath)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(queuePath)) {
            QueueData data = GSON.fromJson(reader, QueueData.class);
            if (data != null && data.states != null) {
                this.worldName = data.worldName;
                states.clear();
                for (StateEntry entry : data.states) {
                    RegionCoord coord = new RegionCoord(entry.x, entry.z, entry.dimension);
                    TransferState state = new TransferState(
                            coord,
                            entry.status,
                            entry.attemptCount,
                            entry.bytesTransferred,
                            entry.totalBytes,
                            entry.errorMessage,
                            entry.lastAttemptTime
                    );
                    states.put(coord, state);
                }
                logger.info("Loaded " + states.size() + " pending transfers from disk");
            }
        } catch (IOException e) {
            logger.warning("Failed to load transfer queue: " + e.getMessage());
        }
    }

    /**
     * Saves the queue to disk.
     */
    public void save() {
        try {
            Files.createDirectories(queuePath.getParent());
            try (Writer writer = Files.newBufferedWriter(queuePath)) {
                QueueData data = new QueueData();
                data.worldName = this.worldName;
                data.states = new ArrayList<>();

                for (TransferState state : states.values()) {
                    StateEntry entry = new StateEntry();
                    entry.x = state.region().x();
                    entry.z = state.region().z();
                    entry.dimension = state.region().dimension();
                    entry.status = state.status();
                    entry.attemptCount = state.attemptCount();
                    entry.bytesTransferred = state.bytesTransferred();
                    entry.totalBytes = state.totalBytes();
                    entry.errorMessage = state.errorMessage();
                    entry.lastAttemptTime = state.lastAttemptTime();
                    data.states.add(entry);
                }

                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            logger.warning("Failed to save transfer queue: " + e.getMessage());
        }
    }

    /**
     * Sets the world name for this transfer batch.
     */
    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    /**
     * Gets the world name for this transfer batch.
     */
    public String getWorldName() {
        return worldName;
    }

    /**
     * Adds a region to the transfer queue.
     */
    public void addRegion(RegionCoord coord) {
        if (!states.containsKey(coord)) {
            states.put(coord, TransferState.pending(coord));
        }
    }

    /**
     * Adds multiple regions to the transfer queue.
     */
    public void addRegions(Collection<RegionCoord> coords) {
        for (RegionCoord coord : coords) {
            addRegion(coord);
        }
    }

    /**
     * Gets the next pending region.
     *
     * @return Next region to process, or null if none pending
     */
    public RegionCoord getNextPending() {
        return states.values().stream()
                .filter(s -> s.status() == TransferState.Status.PENDING)
                .map(TransferState::region)
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets all regions with a specific status.
     */
    public List<RegionCoord> getRegionsByStatus(TransferState.Status status) {
        return states.values().stream()
                .filter(s -> s.status() == status)
                .map(TransferState::region)
                .toList();
    }

    /**
     * Updates the state of a region.
     */
    public void updateState(TransferState state) {
        states.put(state.region(), state);
        save(); // Auto-save on state change
    }

    /**
     * Gets the current state of a region.
     */
    public TransferState getState(RegionCoord coord) {
        return states.get(coord);
    }

    /**
     * Marks failed regions for retry.
     *
     * @param maxAttempts Maximum retry attempts
     * @return Number of regions marked for retry
     */
    public int retryFailed(int maxAttempts) {
        int count = 0;
        for (Map.Entry<RegionCoord, TransferState> entry : states.entrySet()) {
            TransferState state = entry.getValue();
            if (state.canRetry(maxAttempts)) {
                states.put(entry.getKey(), state.retry());
                count++;
            }
        }
        if (count > 0) {
            save();
        }
        return count;
    }

    /**
     * Gets a summary of the current queue status.
     */
    public TransferSummary getSummary() {
        int pending = 0, inProgress = 0, completed = 0, failed = 0;
        long bytesTransferred = 0, totalBytes = 0;

        for (TransferState state : states.values()) {
            switch (state.status()) {
                case PENDING -> pending++;
                case COMPRESSING, UPLOADING -> {
                    inProgress++;
                    bytesTransferred += state.bytesTransferred();
                    totalBytes += state.totalBytes();
                }
                case COMPLETED -> {
                    completed++;
                    bytesTransferred += state.totalBytes();
                    totalBytes += state.totalBytes();
                }
                case FAILED -> failed++;
            }
        }

        return new TransferSummary(
                states.size(),
                pending,
                inProgress,
                completed,
                failed,
                bytesTransferred,
                totalBytes
        );
    }

    /**
     * Clears all completed and failed transfers.
     */
    public void clearCompleted() {
        states.entrySet().removeIf(e ->
                e.getValue().status() == TransferState.Status.COMPLETED ||
                        e.getValue().status() == TransferState.Status.FAILED);
        save();
    }

    /**
     * Clears the entire queue.
     */
    public void clear() {
        states.clear();
        worldName = null;
        try {
            Files.deleteIfExists(queuePath);
        } catch (IOException e) {
            logger.warning("Failed to delete queue file: " + e.getMessage());
        }
    }

    /**
     * Checks if the queue is empty.
     */
    public boolean isEmpty() {
        return states.isEmpty();
    }

    /**
     * Gets the total number of regions in the queue.
     */
    public int size() {
        return states.size();
    }

    // JSON serialization classes
    private static class QueueData {
        String worldName;
        List<StateEntry> states;
    }

    private static class StateEntry {
        int x;
        int z;
        String dimension;
        TransferState.Status status;
        int attemptCount;
        long bytesTransferred;
        long totalBytes;
        String errorMessage;
        long lastAttemptTime;
    }
}
