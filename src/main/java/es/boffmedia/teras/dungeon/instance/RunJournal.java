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

    /**
     * A pending return with the moment it was filed.
     *
     * <p>The stamp is what makes {@code returns.json} finite. An entry is only ever consumed by its
     * owner logging back in, so a player who never returns leaves one behind forever — invisible,
     * cheap individually, and unbounded over a server's lifetime.</p>
     */
    record TimestampedReturn(DungeonRun.ReturnPoint point, long savedAtMs) {}

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

    // --- the ascensor ledger ------------------------------------------------------------------

    private static Path elevatorsFile() {
        return runsDir().resolve("ascensores.json");
    }

    static Map<UUID, Map<String, Integer>> loadElevators() {
        return loadElevators(elevatorsFile());
    }

    static void saveElevators(Map<UUID, Map<String, Integer>> unlocks) {
        saveElevators(unlocks, elevatorsFile());
    }

    /**
     * Reads the ascensor unlocks. Takes its path for the same reason {@link #loadReturns(Path)}
     * does — the format has to be exercisable without a game directory.
     *
     * <p><b>No timestamp, unlike a return.</b> A return is consumed once and rots if its owner never
     * comes back; an unlock is a permanent fact about a player, and making one expire would punish
     * somebody for not playing for a month.</p>
     */
    static Map<UUID, Map<String, Integer>> loadElevators(Path file) {
        Map<UUID, Map<String, Integer>> unlocks = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return unlocks;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            for (String key : root.keySet()) {
                JsonObject byDungeon = root.getAsJsonObject(key);
                Map<String, Integer> tramos = new LinkedHashMap<>();
                for (String dungeon : byDungeon.keySet()) {
                    tramos.put(dungeon, byDungeon.get(dungeon).getAsInt());
                }
                unlocks.put(UUID.fromString(key), tramos);
            }
        } catch (Exception e) {
            Teras.LOGGER.warn("Dungeons: unreadable ascensores.json: {}", e.toString());
        }
        return unlocks;
    }

    static void saveElevators(Map<UUID, Map<String, Integer>> unlocks, Path file) {
        JsonObject root = new JsonObject();
        for (Map.Entry<UUID, Map<String, Integer>> entry : unlocks.entrySet()) {
            JsonObject byDungeon = new JsonObject();
            entry.getValue().forEach(byDungeon::addProperty);
            root.add(entry.getKey().toString(), byDungeon);
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root));
        } catch (Exception e) {
            Teras.LOGGER.warn("Dungeons: could not save ascensores.json: {}", e.toString());
        }
    }

    // --- pending returns for players offline when their run ended -----------------------------

    static Map<UUID, TimestampedReturn> loadReturns() {
        return loadReturns(returnsFile());
    }

    static void saveReturns(Map<UUID, TimestampedReturn> returns) {
        saveReturns(returns, returnsFile());
    }

    /**
     * Reads a returns file. Takes its path so the format can be exercised without a game directory —
     * every other caller wants {@link #returnsFile()}.
     */
    static Map<UUID, TimestampedReturn> loadReturns(Path file) {
        Map<UUID, TimestampedReturn> returns = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return returns;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            for (String key : root.keySet()) {
                returns.put(UUID.fromString(key), parseReturn(root.getAsJsonObject(key)));
            }
        } catch (Exception e) {
            Teras.LOGGER.warn("Dungeons: unreadable returns.json: {}", e.toString());
        }
        return returns;
    }

    static void saveReturns(Map<UUID, TimestampedReturn> returns, Path file) {
        JsonObject root = new JsonObject();
        for (Map.Entry<UUID, TimestampedReturn> entry : returns.entrySet()) {
            JsonObject obj = new JsonObject();
            obj.add("point", renderPoint(entry.getValue().point()));
            obj.addProperty("savedAtMs", entry.getValue().savedAtMs());
            root.add(entry.getKey().toString(), obj);
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root));
        } catch (Exception e) {
            Teras.LOGGER.warn("Dungeons: could not save returns.json: {}", e.toString());
        }
    }

    /**
     * One entry, in either format. Files written before the stamp existed are flat return points;
     * they are read as saved <b>now</b>, so an upgrade never expires the entries it inherits — the
     * sweep is for players who stopped coming back, not for a format change.
     */
    private static TimestampedReturn parseReturn(JsonObject entry) {
        if (entry.has("point")) {
            return new TimestampedReturn(parsePoint(entry.getAsJsonObject("point")),
                    entry.has("savedAtMs") ? entry.get("savedAtMs").getAsLong()
                            : System.currentTimeMillis());
        }
        return new TimestampedReturn(parsePoint(entry), System.currentTimeMillis());
    }

    private static JsonObject renderPoint(DungeonRun.ReturnPoint p) {
        JsonObject obj = new JsonObject();
        obj.addProperty("dim", p.dimension());
        obj.addProperty("x", p.x());
        obj.addProperty("y", p.y());
        obj.addProperty("z", p.z());
        obj.addProperty("yaw", p.yaw());
        obj.addProperty("pitch", p.pitch());
        obj.addProperty("mode", p.gameMode());
        return obj;
    }

    /** {@code mode} is lenient: journals written before runs forced adventure lack the field. */
    private static DungeonRun.ReturnPoint parsePoint(JsonObject p) {
        return new DungeonRun.ReturnPoint(
                p.get("dim").getAsString(), p.get("x").getAsDouble(),
                p.get("y").getAsDouble(), p.get("z").getAsDouble(),
                p.get("yaw").getAsFloat(), p.get("pitch").getAsFloat(),
                p.has("mode") ? p.get("mode").getAsString() : "survival");
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
            party.put(UUID.fromString(p.get("uuid").getAsString()), parsePoint(p));
        }
        return party;
    }
}
