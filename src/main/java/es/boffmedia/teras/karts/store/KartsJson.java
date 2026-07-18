package es.boffmedia.teras.karts.store;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.karts.model.KartTrack;
import es.boffmedia.teras.karts.model.TrackCheckpoint;
import es.boffmedia.teras.karts.model.TrackPoint;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads and writes {@code circuitos.json}, including the one-way upgrade from the 1.16.5 file.
 *
 * <p>Hand-rolled rather than reflected: {@link KartTrack} keeps a derived spline cache and mutating
 * accessors that Gson would either trip over or quietly bypass, and the legacy format needs real
 * conversion (a track-wide compass direction becoming a per-slot yaw), not field renaming.</p>
 *
 * <h2>Current format (version 2)</h2>
 * <pre>
 * { "version": 2,
 *   "circuitos": {
 *     "nombre": {
 *       "displayName": "Nombre bonito",
 *       "dimension": "minecraft:overworld",
 *       "vueltasDefecto": 3,
 *       "salidas":     [ {"x":0,"y":64,"z":0,"yaw":90} ],
 *       "checkpoints": [ {"a":{"x":0,"y":64,"z":0}, "b":{"x":5,"y":68,"z":5}, "padding":1.0} ]
 *     } } }
 * </pre>
 *
 * <h2>Legacy format (1.16.5, no version key)</h2>
 * <pre>
 * { "nombre": { "name":"nombre", "startingDirection":"NORTH",
 *               "startingPoints":[{"x":..,"y":..,"z":..}],
 *               "checkpoints":[{"start":{...},"end":{...}}] } }
 * </pre>
 */
final class KartsJson {
    private KartsJson() {}

    static final int CURRENT_VERSION = 2;

    /** Compass heading to the yaw a kart faces. Minecraft yaw: 0 south, 90 west, 180 north, -90 east. */
    private static float yawOf(String startingDirection) {
        if (startingDirection == null) {
            return 0f;
        }
        return switch (startingDirection.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "NORTH" -> 180f;
            case "EAST" -> -90f;
            case "WEST" -> 90f;
            default -> 0f;
        };
    }

    /** Whether a parsed file is the 1.16.5 shape and needs converting. */
    static boolean isLegacy(JsonObject root) {
        return root != null && !root.has("version") && !root.has("circuitos");
    }

    static JsonObject write(Map<String, KartTrack> tracks) {
        JsonObject root = new JsonObject();
        root.addProperty("version", CURRENT_VERSION);
        JsonObject circuits = new JsonObject();
        tracks.forEach((name, track) -> circuits.add(name, writeTrack(track)));
        root.add("circuitos", circuits);
        return root;
    }

    private static JsonObject writeTrack(KartTrack track) {
        JsonObject json = new JsonObject();
        json.addProperty("displayName", track.displayName());
        if (track.dimension() != null) {
            json.addProperty("dimension", track.dimension());
        }
        json.addProperty("vueltasDefecto", track.defaultLaps());

        JsonArray starts = new JsonArray();
        for (TrackPoint point : track.startingPoints()) {
            JsonObject slot = writePoint(point);
            slot.addProperty("yaw", point.yaw());
            starts.add(slot);
        }
        json.add("salidas", starts);

        JsonArray checkpoints = new JsonArray();
        for (TrackCheckpoint checkpoint : track.checkpoints()) {
            JsonObject gate = new JsonObject();
            gate.add("a", writePoint(checkpoint.cornerA()));
            gate.add("b", writePoint(checkpoint.cornerB()));
            gate.addProperty("padding", checkpoint.padding());
            checkpoints.add(gate);
        }
        json.add("checkpoints", checkpoints);
        return json;
    }

    private static JsonObject writePoint(TrackPoint point) {
        JsonObject json = new JsonObject();
        json.addProperty("x", point.x());
        json.addProperty("y", point.y());
        json.addProperty("z", point.z());
        return json;
    }

    /**
     * Parses either format into tracks. Entries that fail to parse are skipped with a warning
     * rather than failing the whole file — one hand-edited circuit should not cost an admin the
     * rest of their work.
     */
    static Map<String, KartTrack> read(JsonObject root) {
        Map<String, KartTrack> tracks = new LinkedHashMap<>();
        if (root == null) {
            return tracks;
        }
        if (isLegacy(root)) {
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                accept(tracks, entry.getKey(), () -> readLegacyTrack(entry.getKey(), entry.getValue()));
            }
            return tracks;
        }
        JsonObject circuits = root.getAsJsonObject("circuitos");
        if (circuits == null) {
            return tracks;
        }
        for (Map.Entry<String, JsonElement> entry : circuits.entrySet()) {
            accept(tracks, entry.getKey(), () -> readTrack(entry.getKey(), entry.getValue()));
        }
        return tracks;
    }

    private static void accept(Map<String, KartTrack> into, String name,
                               java.util.function.Supplier<KartTrack> parse) {
        try {
            KartTrack track = parse.get();
            if (track != null) {
                into.put(name, track);
            }
        } catch (Exception e) {
            Teras.LOGGER.warn("Karts: skipping circuit '{}': {}", name, e.toString());
        }
    }

    private static KartTrack readTrack(String name, JsonElement element) {
        JsonObject json = element.getAsJsonObject();
        KartTrack track = new KartTrack(name);
        if (json.has("displayName")) {
            track.setDisplayName(json.get("displayName").getAsString());
        }
        if (json.has("dimension")) {
            track.setDimension(json.get("dimension").getAsString());
        }
        if (json.has("vueltasDefecto")) {
            track.setDefaultLaps(json.get("vueltasDefecto").getAsInt());
        }
        if (json.has("salidas")) {
            for (JsonElement slot : json.getAsJsonArray("salidas")) {
                JsonObject point = slot.getAsJsonObject();
                track.addStartingPoint(new TrackPoint(
                        point.get("x").getAsDouble(),
                        point.get("y").getAsDouble(),
                        point.get("z").getAsDouble(),
                        point.has("yaw") ? point.get("yaw").getAsFloat() : 0f));
            }
        }
        if (json.has("checkpoints")) {
            for (JsonElement gate : json.getAsJsonArray("checkpoints")) {
                JsonObject checkpoint = gate.getAsJsonObject();
                track.addCheckpoint(new TrackCheckpoint(
                        readPoint(checkpoint.getAsJsonObject("a")),
                        readPoint(checkpoint.getAsJsonObject("b")),
                        checkpoint.has("padding")
                                ? checkpoint.get("padding").getAsDouble()
                                : TrackCheckpoint.DEFAULT_PADDING));
            }
        }
        return track;
    }

    private static KartTrack readLegacyTrack(String name, JsonElement element) {
        JsonObject json = element.getAsJsonObject();
        KartTrack track = new KartTrack(name);
        float yaw = yawOf(json.has("startingDirection") ? json.get("startingDirection").getAsString() : null);

        if (json.has("startingPoints")) {
            for (JsonElement slot : json.getAsJsonArray("startingPoints")) {
                JsonObject point = slot.getAsJsonObject();
                track.addStartingPoint(new TrackPoint(
                        point.get("x").getAsDouble(),
                        point.get("y").getAsDouble(),
                        point.get("z").getAsDouble(),
                        yaw));
            }
        }
        if (json.has("checkpoints")) {
            for (JsonElement gate : json.getAsJsonArray("checkpoints")) {
                JsonObject checkpoint = gate.getAsJsonObject();
                track.addCheckpoint(new TrackCheckpoint(
                        readPoint(checkpoint.getAsJsonObject("start")),
                        readPoint(checkpoint.getAsJsonObject("end")),
                        checkpoint.has("padding")
                                ? checkpoint.get("padding").getAsDouble()
                                : TrackCheckpoint.DEFAULT_PADDING));
            }
        }
        // The 1.16.5 file had no dimension: races were wherever the tracks happened to be built.
        // Left null so validation asks the admin to re-save from the right world rather than
        // guessing the overworld and putting a race in the wrong dimension.
        return track;
    }

    private static TrackPoint readPoint(JsonObject json) {
        return TrackPoint.at(
                json.get("x").getAsDouble(),
                json.get("y").getAsDouble(),
                json.get("z").getAsDouble());
    }
}
