package dev.flur.extrachunky.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Network message envelope for bidirectional host-worker communication.
 * All messages are serialized as JSON with a type field and payload.
 */
public class NetworkMessage {
    private static final Gson GSON = new GsonBuilder().create();

    public enum Type {
        // Worker -> Host
        REGISTER,           // Worker requests registration
        PROGRESS,           // Worker reports chunk generation progress
        GENERATION_COMPLETE,// Worker finished generating, starting transfer
        TRANSFER_PROGRESS,  // Worker reports transfer progress
        TRANSFER_COMPLETE,  // Worker finished all transfers
        TRANSFER_FAILED,    // Worker transfer failed

        // Host -> Worker
        REGISTERED,     // Host confirms registration with assigned ID
        ASSIGNMENT,     // Host sends chunk assignment to worker
        START,          // Host tells workers to start generation
        STOP,           // Host tells workers to stop generation
        REASSIGN        // Host sends updated assignment (worker join/leave)
    }

    private final Type type;
    private final JsonObject payload;

    public NetworkMessage(Type type, JsonObject payload) {
        this.type = type;
        this.payload = payload;
    }

    public NetworkMessage(Type type) {
        this(type, new JsonObject());
    }

    public Type getType() {
        return type;
    }

    public JsonObject getPayload() {
        return payload;
    }

    public String toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", type.name());
        obj.add("payload", payload);
        return GSON.toJson(obj);
    }

    public static NetworkMessage fromJson(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        Type type = Type.valueOf(obj.get("type").getAsString());
        JsonObject payload = obj.has("payload") ? obj.getAsJsonObject("payload") : new JsonObject();
        return new NetworkMessage(type, payload);
    }

    // Factory methods for Worker -> Host messages

    public static NetworkMessage register(String hostname) {
        JsonObject payload = new JsonObject();
        payload.addProperty("hostname", hostname);
        return new NetworkMessage(Type.REGISTER, payload);
    }

    public static NetworkMessage progress(int instanceId, String world, long chunksGenerated,
                                          long totalChunks, float percentComplete,
                                          float chunksPerSecond, String hostname) {
        JsonObject payload = new JsonObject();
        payload.addProperty("instanceId", instanceId);
        payload.addProperty("world", world);
        payload.addProperty("chunksGenerated", chunksGenerated);
        payload.addProperty("totalChunks", totalChunks);
        payload.addProperty("percentComplete", percentComplete);
        payload.addProperty("chunksPerSecond", chunksPerSecond);
        payload.addProperty("lastUpdate", System.currentTimeMillis());
        payload.addProperty("hostname", hostname);
        return new NetworkMessage(Type.PROGRESS, payload);
    }

    // Factory methods for Host -> Worker messages

    public static NetworkMessage registered(int assignedId, int totalWorkers) {
        JsonObject payload = new JsonObject();
        payload.addProperty("assignedId", assignedId);
        payload.addProperty("totalWorkers", totalWorkers);
        return new NetworkMessage(Type.REGISTERED, payload);
    }

    public static NetworkMessage assignment(int instanceId, int totalInstances, String world,
                                            double centerX, double centerZ, double radius, String shape) {
        JsonObject payload = new JsonObject();
        payload.addProperty("instanceId", instanceId);
        payload.addProperty("totalInstances", totalInstances);
        payload.addProperty("world", world);
        payload.addProperty("centerX", centerX);
        payload.addProperty("centerZ", centerZ);
        payload.addProperty("radius", radius);
        payload.addProperty("shape", shape);
        return new NetworkMessage(Type.ASSIGNMENT, payload);
    }

    public static NetworkMessage reassign(int instanceId, int totalInstances, String world,
                                          double centerX, double centerZ, double radius, String shape) {
        JsonObject payload = new JsonObject();
        payload.addProperty("instanceId", instanceId);
        payload.addProperty("totalInstances", totalInstances);
        payload.addProperty("world", world);
        payload.addProperty("centerX", centerX);
        payload.addProperty("centerZ", centerZ);
        payload.addProperty("radius", radius);
        payload.addProperty("shape", shape);
        return new NetworkMessage(Type.REASSIGN, payload);
    }

    public static NetworkMessage start() {
        return new NetworkMessage(Type.START);
    }

    public static NetworkMessage stop() {
        return new NetworkMessage(Type.STOP);
    }

    // Factory methods for transfer messages (Worker -> Host)

    public static NetworkMessage generationComplete(int instanceId, String world, int regionCount) {
        JsonObject payload = new JsonObject();
        payload.addProperty("instanceId", instanceId);
        payload.addProperty("world", world);
        payload.addProperty("regionCount", regionCount);
        payload.addProperty("timestamp", System.currentTimeMillis());
        return new NetworkMessage(Type.GENERATION_COMPLETE, payload);
    }

    public static NetworkMessage transferProgress(int instanceId, int completed, int total,
                                                   long bytesTransferred, long totalBytes) {
        JsonObject payload = new JsonObject();
        payload.addProperty("instanceId", instanceId);
        payload.addProperty("completed", completed);
        payload.addProperty("total", total);
        payload.addProperty("bytesTransferred", bytesTransferred);
        payload.addProperty("totalBytes", totalBytes);
        payload.addProperty("percentComplete", total > 0 ? (float) completed / total * 100 : 0);
        payload.addProperty("timestamp", System.currentTimeMillis());
        return new NetworkMessage(Type.TRANSFER_PROGRESS, payload);
    }

    public static NetworkMessage transferComplete(int instanceId, String world, int regionCount) {
        JsonObject payload = new JsonObject();
        payload.addProperty("instanceId", instanceId);
        payload.addProperty("world", world);
        payload.addProperty("regionCount", regionCount);
        payload.addProperty("timestamp", System.currentTimeMillis());
        return new NetworkMessage(Type.TRANSFER_COMPLETE, payload);
    }

    public static NetworkMessage transferFailed(int instanceId, String world, String error, int failedCount) {
        JsonObject payload = new JsonObject();
        payload.addProperty("instanceId", instanceId);
        payload.addProperty("world", world);
        payload.addProperty("error", error);
        payload.addProperty("failedCount", failedCount);
        payload.addProperty("timestamp", System.currentTimeMillis());
        return new NetworkMessage(Type.TRANSFER_FAILED, payload);
    }

    // Payload extraction helpers

    public String getString(String key) {
        return payload.has(key) ? payload.get(key).getAsString() : null;
    }

    public int getInt(String key) {
        return payload.has(key) ? payload.get(key).getAsInt() : 0;
    }

    public long getLong(String key) {
        return payload.has(key) ? payload.get(key).getAsLong() : 0L;
    }

    public float getFloat(String key) {
        return payload.has(key) ? payload.get(key).getAsFloat() : 0f;
    }

    public double getDouble(String key) {
        return payload.has(key) ? payload.get(key).getAsDouble() : 0.0;
    }
}
