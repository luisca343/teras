package es.boffmedia.teras.karts.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Circuit records, in {@code config/teras/karts/leaderboards.json}: best total times and best single
 * laps per circuit.
 *
 * <p>Kept locally rather than only on the backend so records survive the backend being down, and so
 * a time trial can tell a racer where they stand the moment they cross the line.</p>
 *
 * <p>One entry per player per table — a personal best, not a history — so a good driver cannot fill
 * the whole top ten.</p>
 */
public final class LeaderboardStore {
    private LeaderboardStore() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** @param timeMs total race time for the records table, or the lap time for the lap table. */
    public record Record(UUID playerId, String playerName, long timeMs, int laps,
                         String kart, long achievedAt) {}

    private record Board(List<Record> bestTimes, List<Record> bestLaps) {
        static Board empty() {
            return new Board(new ArrayList<>(), new ArrayList<>());
        }
    }

    private static Map<String, Board> boards;
    private static boolean dirty;

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("teras").resolve("karts").resolve("leaderboards.json");
    }

    public static List<Record> bestTimes(String track, int limit) {
        ensureLoaded();
        Board board = boards.get(track);
        return board == null ? List.of() : List.copyOf(board.bestTimes().subList(
                0, Math.min(limit, board.bestTimes().size())));
    }

    public static List<Record> bestLaps(String track, int limit) {
        ensureLoaded();
        Board board = boards.get(track);
        return board == null ? List.of() : List.copyOf(board.bestLaps().subList(
                0, Math.min(limit, board.bestLaps().size())));
    }

    /** The circuit record — the fastest total time anyone has set. */
    public static Optional<Record> trackRecord(String track) {
        return bestTimes(track, 1).stream().findFirst();
    }

    public static Optional<Record> personalBest(String track, UUID player) {
        ensureLoaded();
        Board board = boards.get(track);
        if (board == null) {
            return Optional.empty();
        }
        return board.bestTimes().stream()
                .filter(record -> record.playerId().equals(player))
                .findFirst();
    }

    /**
     * Files a result. Returns true if it improved on that player's previous best, which is what
     * decides whether the racer is congratulated.
     */
    public static boolean submitTime(String track, Record record, int topN) {
        ensureLoaded();
        Board board = boards.computeIfAbsent(track, key -> Board.empty());
        boolean improved = insertPersonalBest(board.bestTimes(), record, topN);
        dirty |= improved;
        return improved;
    }

    public static boolean submitLap(String track, Record record, int topN) {
        ensureLoaded();
        Board board = boards.computeIfAbsent(track, key -> Board.empty());
        boolean improved = insertPersonalBest(board.bestLaps(), record, topN);
        dirty |= improved;
        return improved;
    }

    /**
     * Keeps one row per player, replacing theirs only when the new time is quicker, then trims the
     * table to {@code topN}.
     */
    private static boolean insertPersonalBest(List<Record> table, Record record, int topN) {
        if (record.timeMs() <= 0) {
            return false;
        }
        Optional<Record> existing = table.stream()
                .filter(row -> row.playerId().equals(record.playerId()))
                .findFirst();
        if (existing.isPresent()) {
            if (existing.get().timeMs() <= record.timeMs()) {
                return false;
            }
            table.remove(existing.get());
        }
        table.add(record);
        table.sort(Comparator.comparingLong(Record::timeMs));
        while (table.size() > Math.max(1, topN)) {
            table.remove(table.size() - 1);
        }
        return true;
    }

    public static void saveIfDirty() {
        if (!dirty) {
            return;
        }
        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            JsonObject circuits = new JsonObject();
            boards.forEach((track, board) -> {
                JsonObject json = new JsonObject();
                json.add("mejoresTiempos", writeRecords(board.bestTimes()));
                json.add("mejorVuelta", writeRecords(board.bestLaps()));
                circuits.add(track, json);
            });
            root.add("circuitos", circuits);
            Files.writeString(file, GSON.toJson(root));
            dirty = false;
        } catch (Exception e) {
            Teras.LOGGER.warn("Karts: failed to save leaderboards: {}", e.toString());
        }
    }

    private static JsonArray writeRecords(List<Record> records) {
        JsonArray array = new JsonArray();
        for (Record record : records) {
            JsonObject json = new JsonObject();
            json.addProperty("uuid", record.playerId().toString());
            json.addProperty("nombre", record.playerName());
            json.addProperty("tiempoMs", record.timeMs());
            json.addProperty("vueltas", record.laps());
            json.addProperty("kart", record.kart());
            json.addProperty("fecha", record.achievedAt());
            array.add(json);
        }
        return array;
    }

    public static void reload() {
        boards = null;
        dirty = false;
        ensureLoaded();
    }

    private static void ensureLoaded() {
        if (boards != null) {
            return;
        }
        boards = new LinkedHashMap<>();
        Path file = path();
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("circuitos")) {
                return;
            }
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("circuitos").entrySet()) {
                JsonObject json = entry.getValue().getAsJsonObject();
                boards.put(entry.getKey(), new Board(
                        readRecords(json.get("mejoresTiempos")),
                        readRecords(json.get("mejorVuelta"))));
            }
        } catch (Exception e) {
            Teras.LOGGER.warn("Karts: failed to load leaderboards: {}", e.toString());
        }
    }

    private static List<Record> readRecords(JsonElement element) {
        List<Record> records = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return records;
        }
        for (JsonElement item : element.getAsJsonArray()) {
            try {
                JsonObject json = item.getAsJsonObject();
                records.add(new Record(
                        UUID.fromString(json.get("uuid").getAsString()),
                        json.get("nombre").getAsString(),
                        json.get("tiempoMs").getAsLong(),
                        json.has("vueltas") ? json.get("vueltas").getAsInt() : 0,
                        json.has("kart") ? json.get("kart").getAsString() : "",
                        json.has("fecha") ? json.get("fecha").getAsLong() : 0L));
            } catch (Exception e) {
                Teras.LOGGER.warn("Karts: skipping malformed leaderboard row: {}", e.toString());
            }
        }
        records.sort(Comparator.comparingLong(Record::timeMs));
        return records;
    }

    /** All circuits with records, for the HTTP endpoint. */
    public static List<String> trackNames() {
        ensureLoaded();
        return List.copyOf(boards.keySet());
    }
}
