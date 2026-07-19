package es.boffmedia.teras.dungeon.instance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import net.minecraft.core.BlockPos;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Crash insurance for instanced runs, under {@code config/teras/dungeons/runs/}. A run's journal
 * is written before its floor is built and deleted only after the floor is cleared, so whatever
 * the server was doing when it died, boot finds either nothing or a file naming exactly which
 * cells to sweep and who to send home. {@code returns.json} holds the send-homes for players who
 * were offline when their run ended.
 *
 * <p>Cell origins are stored <b>absolute</b>: during a stage advance a run briefly owns two
 * floors on two pads, and the journal simply lists every cell box currently standing — a floor
 * is swept, never resumed.</p>
 */
final class RunJournal {
    private RunJournal() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** What the boot sweep needs to undo whatever floors a run left standing. */
    record SweptRun(int id, String dimension, List<BlockPos> cellOrigins,
                    int roomSize, int roomHeight,
                    Map<UUID, DungeonRun.ReturnPoint> party) {}

    private static Path runsDir() {
        return FMLPaths.CONFIGDIR.get().resolve("teras").resolve("dungeons").resolve("runs");
    }

    private static Path runFile(int id) {
        return runsDir().resolve("run-" + id + ".json");
    }

    private static Path returnsFile() {
        return runsDir().resolve("returns.json");
    }

    static void write(int runId, String dimension, int roomSize, int roomHeight,
                      List<BlockPos> cellOrigins, Map<UUID, DungeonRun.ReturnPoint> party) {
        JsonObject root = new JsonObject();
        root.addProperty("id", runId);
        root.addProperty("dimension", dimension);
        root.addProperty("roomSize", roomSize);
        root.addProperty("roomHeight", roomHeight);
        JsonArray cells = new JsonArray();
        for (BlockPos cell : cellOrigins) {
            JsonArray cellJson = new JsonArray();
            cellJson.add(cell.getX());
            cellJson.add(cell.getY());
            cellJson.add(cell.getZ());
            cells.add(cellJson);
        }
        root.add("cellOrigins", cells);
        root.add("players", renderParty(party));
        try {
            Files.createDirectories(runsDir());
            Files.writeString(runFile(runId), GSON.toJson(root));
        } catch (Exception e) {
            Teras.LOGGER.warn("Dungeons: could not journal run {}: {}", runId, e.toString());
        }
    }

    static void delete(int runId) {
        try {
            Files.deleteIfExists(runFile(runId));
        } catch (Exception e) {
            Teras.LOGGER.warn("Dungeons: could not delete journal of run {}: {}", runId, e.toString());
        }
    }

    /** Parses every journal on disk; corrupt files are logged and skipped, never deleted blind. */
    static List<SweptRun> readAll() {
        List<SweptRun> runs = new ArrayList<>();
        if (!Files.isDirectory(runsDir())) {
            return runs;
        }
        try (Stream<Path> files = Files.list(runsDir())) {
            files.filter(f -> f.getFileName().toString().startsWith("run-")).forEach(file -> {
                try (Reader reader = Files.newBufferedReader(file)) {
                    JsonObject root = GSON.fromJson(reader, JsonObject.class);
                    List<BlockPos> cells = new ArrayList<>();
                    for (var cell : root.getAsJsonArray("cellOrigins")) {
                        JsonArray c = cell.getAsJsonArray();
                        cells.add(new BlockPos(c.get(0).getAsInt(), c.get(1).getAsInt(),
                                c.get(2).getAsInt()));
                    }
                    runs.add(new SweptRun(root.get("id").getAsInt(),
                            root.get("dimension").getAsString(), cells,
                            root.get("roomSize").getAsInt(),
                            root.get("roomHeight").getAsInt(),
                            parseParty(root.getAsJsonArray("players"))));
                } catch (Exception e) {
                    Teras.LOGGER.warn("Dungeons: unreadable run journal {}: {}", file, e.toString());
                }
            });
        } catch (Exception e) {
            Teras.LOGGER.warn("Dungeons: could not list run journals: {}", e.toString());
        }
        return runs;
    }

    // --- pending returns for players offline when their run ended -----------------------------

    static Map<UUID, DungeonRun.ReturnPoint> loadReturns() {
        Map<UUID, DungeonRun.ReturnPoint> returns = new LinkedHashMap<>();
        if (!Files.exists(returnsFile())) {
            return returns;
        }
        try (Reader reader = Files.newBufferedReader(returnsFile())) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            for (String key : root.keySet()) {
                JsonObject p = root.getAsJsonObject(key);
                returns.put(UUID.fromString(key), new DungeonRun.ReturnPoint(
                        p.get("dim").getAsString(), p.get("x").getAsDouble(),
                        p.get("y").getAsDouble(), p.get("z").getAsDouble(),
                        p.get("yaw").getAsFloat(), p.get("pitch").getAsFloat()));
            }
        } catch (Exception e) {
            Teras.LOGGER.warn("Dungeons: unreadable returns.json: {}", e.toString());
        }
        return returns;
    }

    static void saveReturns(Map<UUID, DungeonRun.ReturnPoint> returns) {
        JsonObject root = new JsonObject();
        for (Map.Entry<UUID, DungeonRun.ReturnPoint> entry : returns.entrySet()) {
            root.add(entry.getKey().toString(), renderPoint(entry.getValue()));
        }
        try {
            Files.createDirectories(runsDir());
            Files.writeString(returnsFile(), GSON.toJson(root));
        } catch (Exception e) {
            Teras.LOGGER.warn("Dungeons: could not save returns.json: {}", e.toString());
        }
    }

    private static JsonObject renderPoint(DungeonRun.ReturnPoint p) {
        JsonObject obj = new JsonObject();
        obj.addProperty("dim", p.dimension());
        obj.addProperty("x", p.x());
        obj.addProperty("y", p.y());
        obj.addProperty("z", p.z());
        obj.addProperty("yaw", p.yaw());
        obj.addProperty("pitch", p.pitch());
        return obj;
    }

    private static JsonArray renderParty(Map<UUID, DungeonRun.ReturnPoint> party) {
        JsonArray players = new JsonArray();
        for (Map.Entry<UUID, DungeonRun.ReturnPoint> entry : party.entrySet()) {
            JsonObject obj = renderPoint(entry.getValue());
            obj.addProperty("uuid", entry.getKey().toString());
            players.add(obj);
        }
        return players;
    }

    private static Map<UUID, DungeonRun.ReturnPoint> parseParty(JsonArray players) {
        Map<UUID, DungeonRun.ReturnPoint> party = new LinkedHashMap<>();
        if (players == null) {
            return party;
        }
        for (var element : players) {
            JsonObject p = element.getAsJsonObject();
            party.put(UUID.fromString(p.get("uuid").getAsString()), new DungeonRun.ReturnPoint(
                    p.get("dim").getAsString(), p.get("x").getAsDouble(),
                    p.get("y").getAsDouble(), p.get("z").getAsDouble(),
                    p.get("yaw").getAsFloat(), p.get("pitch").getAsFloat()));
        }
        return party;
    }
}
