package es.boffmedia.teras.dungeon.encounter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.ability.Abilities;
import es.boffmedia.teras.dungeon.model.SeededRng;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-stage enemy tables, from {@code config/teras/dungeons/enemies.json}. Two entry kinds:
 * {@code entity} (any registered entity id — vanilla or modded, no dependency) and {@code cnpc}
 * (a CustomNPCs clone by tab + name). A server without CustomNPCs, or with a clone missing, still
 * fights the entity entries — spawn failures degrade the wave, never the floor.
 *
 * <p>Stage keys are strings ("1".."12"); a missing stage falls back to {@code "default"}. Wave
 * composition draws from a seed derived from the floor, so a seed-run fights the same waves.</p>
 */
public final class SpawnTables {
    private SpawnTables() {}

    public enum Kind { ENTITY, CNPC, GEO }

    public record SpawnEntry(Kind kind, String id, int tab, int weight) {}

    public record StageTable(int countMin, int countMax, List<SpawnEntry> wave) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_KEY = "default";

    private static Map<String, StageTable> stages = new LinkedHashMap<>();
    private static Map<String, List<SpawnEntry>> bosses = new LinkedHashMap<>();
    private static Map<String, List<SpawnEntry>> miniBosses = new LinkedHashMap<>();

    static {
        resetToDefaults();
    }

    public static void load() {
        resetToDefaults();
        // Seeded before the file is read so a parse failure below still leaves the built-in
        // abilities standing, the same way the spawn tables keep their defaults. Silent: the load
        // worth reporting is the one that consulted the file.
        Abilities.seed(DungeonEnemyPacks.abilities());
        Path path = FMLPaths.CONFIGDIR.get().resolve("teras").resolve("dungeons").resolve("enemies.json");
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(renderDefaults()));
                Teras.LOGGER.info("Dungeons: created default {}", path);
                // The built-ins are the final table on a fresh install, so this is where that run's
                // one ability line belongs.
                Abilities.load(DungeonEnemyPacks.abilities(), null);
                return;
            }
            JsonObject root;
            try (Reader reader = Files.newBufferedReader(path)) {
                root = GSON.fromJson(reader, JsonObject.class);
            }
            if (root.has("stages")) {
                JsonObject stagesJson = root.getAsJsonObject("stages");
                for (String key : stagesJson.keySet()) {
                    StageTable table = readStage(stagesJson.getAsJsonObject(key));
                    if (table != null) {
                        stages.put(key, table);
                    }
                }
            }
            if (root.has("bosses")) {
                readPools(root.getAsJsonObject("bosses"), bosses);
            }
            if (root.has("miniBosses")) {
                readPools(root.getAsJsonObject("miniBosses"), miniBosses);
            }
            Abilities.load(DungeonEnemyPacks.abilities(),
                    root.has("abilities") ? root.getAsJsonObject("abilities") : null);
            Teras.LOGGER.info("Dungeons: enemy tables loaded from {}", path);
        } catch (Exception e) {
            Teras.LOGGER.warn("Dungeons: failed to load enemies.json, using defaults: {}", e.toString());
        }
    }

    public static StageTable stageTable(int stage) {
        return stages.getOrDefault(String.valueOf(stage), stages.get(DEFAULT_KEY));
    }

    public static List<SpawnEntry> bossPool(int stage) {
        return bosses.getOrDefault(String.valueOf(stage), bosses.get(DEFAULT_KEY));
    }

    public static List<SpawnEntry> miniBossPool(int stage) {
        return miniBosses.getOrDefault(String.valueOf(stage), miniBosses.get(DEFAULT_KEY));
    }

    /**
     * Rewrites {@code enemies.json} so the stage tables point at the installed CustomNPCs
     * bestiary instead of the vanilla fallback. Called by
     * {@code /teras dungeon enemigos instalar}; the previous file is kept as {@code .bak} because
     * this replaces hand-tuned tables.
     */
    public static void writeCnpcBestiary(int tab) {
        Path path = FMLPaths.CONFIGDIR.get().resolve("teras").resolve("dungeons").resolve("enemies.json");
        try {
            Files.createDirectories(path.getParent());
            if (Files.exists(path)) {
                Files.copy(path, path.resolveSibling("enemies.json.bak"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(path, GSON.toJson(renderBestiary(tab)));
            Teras.LOGGER.info("Dungeons: enemies.json rewritten against the CustomNPCs bestiary");
        } catch (Exception e) {
            Teras.LOGGER.error("Dungeons: could not write enemies.json: {}", e.toString());
        }
    }

    /**
     * Stage curve: early floors are chaff, elites fade in from stage 3 and take over by the late
     * game. Bosses and mini-bosses draw from their own pools throughout.
     */
    private static JsonObject renderBestiary(int tab) {
        JsonObject stages = new JsonObject();
        stages.add(DEFAULT_KEY, renderStage(new StageTable(3, 5,
                mix(DungeonEnemyPacks.chaff(), 3, DungeonEnemyPacks.elites(), 1, tab))));
        stages.add("1", renderStage(new StageTable(3, 4,
                mix(DungeonEnemyPacks.chaff(), 1, List.of(), 0, tab))));
        stages.add("2", renderStage(new StageTable(3, 5,
                mix(DungeonEnemyPacks.chaff(), 1, List.of(), 0, tab))));
        stages.add("3", renderStage(new StageTable(4, 6,
                mix(DungeonEnemyPacks.chaff(), 4, DungeonEnemyPacks.elites(), 1, tab))));
        // 4-7 used to be missing, so the whole mid-game fell through to "default" — the
        // elite-heavy late table — and the curve flattened exactly where it should be ramping.
        stages.add("4", renderStage(new StageTable(4, 6,
                mix(DungeonEnemyPacks.chaff(), 3, DungeonEnemyPacks.elites(), 1, tab))));
        stages.add("5", renderStage(new StageTable(4, 6,
                mix(DungeonEnemyPacks.chaff(), 5, DungeonEnemyPacks.elites(), 2, tab))));
        stages.add("6", renderStage(new StageTable(4, 7,
                mix(DungeonEnemyPacks.chaff(), 2, DungeonEnemyPacks.elites(), 1, tab))));
        stages.add("7", renderStage(new StageTable(4, 7,
                mix(DungeonEnemyPacks.chaff(), 3, DungeonEnemyPacks.elites(), 2, tab))));
        for (int stage = 8; stage <= 12; stage++) {
            stages.add(String.valueOf(stage), renderStage(new StageTable(4, 7,
                    mix(DungeonEnemyPacks.chaff(), 1, DungeonEnemyPacks.elites(), 2, tab))));
        }

        JsonObject root = new JsonObject();
        root.add("stages", stages);
        JsonObject bosses = new JsonObject();
        bosses.add(DEFAULT_KEY, renderPool(cnpcPool(DungeonEnemyPacks.bosses(), 1, tab)));
        root.add("bosses", bosses);
        JsonObject miniBosses = new JsonObject();
        miniBosses.add(DEFAULT_KEY, renderPool(cnpcPool(DungeonEnemyPacks.miniBosses(), 1, tab)));
        root.add("miniBosses", miniBosses);
        root.add("abilities", Abilities.render(DungeonEnemyPacks.abilities()));
        return root;
    }

    private static List<SpawnEntry> mix(List<EnemyPreset> common, int commonWeight,
                                        List<EnemyPreset> rare, int rareWeight, int tab) {
        List<SpawnEntry> pool = new ArrayList<>(cnpcPool(common, commonWeight, tab));
        if (rareWeight > 0) {
            pool.addAll(cnpcPool(rare, rareWeight, tab));
        }
        return pool;
    }

    private static List<SpawnEntry> cnpcPool(List<EnemyPreset> presets, int weight, int tab) {
        List<SpawnEntry> pool = new ArrayList<>(presets.size());
        for (EnemyPreset preset : presets) {
            pool.add(new SpawnEntry(Kind.CNPC, preset.id(), tab, weight));
        }
        return pool;
    }

    /**
     * A single entry written as one string, for places where a whole JSON object would be noise —
     * {@code entity:minecraft:zombie}, {@code geo:husk_guardian}, {@code cnpc:7:esqueleto_guardia}.
     * Used by the {@code SUMMON} ability's spec. Null when it does not parse.
     */
    public static SpawnEntry parseSpec(String spec) {
        if (spec == null || spec.isBlank()) {
            return null;
        }
        String[] parts = spec.split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        try {
            Kind kind = Kind.valueOf(parts[0].trim().toUpperCase(java.util.Locale.ROOT));
            if (kind != Kind.CNPC) {
                return new SpawnEntry(kind, parts[1].trim(), 0, 1);
            }
            // cnpc carries its tab: cnpc:<tab>:<name>
            String[] clone = parts[1].split(":", 2);
            if (clone.length != 2) {
                return null;
            }
            return new SpawnEntry(Kind.CNPC, clone[1].trim(), Integer.parseInt(clone[0].trim()), 1);
        } catch (Exception e) {
            return null;
        }
    }

    public static SpawnEntry pickWeighted(List<SpawnEntry> pool, SeededRng rng) {
        int total = pool.stream().mapToInt(SpawnEntry::weight).sum();
        int roll = rng.between(1, Math.max(1, total));
        for (SpawnEntry entry : pool) {
            roll -= entry.weight();
            if (roll <= 0) {
                return entry;
            }
        }
        return pool.get(pool.size() - 1);
    }

    private static StageTable readStage(JsonObject json) {
        List<SpawnEntry> wave = readPool(json.get("wave"));
        if (wave.isEmpty()) {
            return null;
        }
        int min = json.has("countMin") ? json.get("countMin").getAsInt() : 3;
        int max = json.has("countMax") ? Math.max(min, json.get("countMax").getAsInt()) : Math.max(min, 5);
        return new StageTable(min, max, wave);
    }

    private static void readPools(JsonObject json, Map<String, List<SpawnEntry>> target) {
        for (String key : json.keySet()) {
            List<SpawnEntry> pool = readPool(json.get(key));
            if (!pool.isEmpty()) {
                target.put(key, pool);
            }
        }
    }

    private static List<SpawnEntry> readPool(JsonElement element) {
        List<SpawnEntry> entries = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return entries;
        }
        for (JsonElement item : element.getAsJsonArray()) {
            try {
                JsonObject obj = item.getAsJsonObject();
                Kind kind = Kind.valueOf(obj.get("kind").getAsString().toUpperCase(java.util.Locale.ROOT));
                String id = kind == Kind.CNPC ? obj.get("name").getAsString() : obj.get("id").getAsString();
                int tab = obj.has("tab") ? obj.get("tab").getAsInt() : 0;
                int weight = obj.has("weight") ? Math.max(1, obj.get("weight").getAsInt()) : 1;
                entries.add(new SpawnEntry(kind, id, tab, weight));
            } catch (Exception e) {
                Teras.LOGGER.warn("Dungeons: skipping bad enemies.json entry {}: {}", item, e.toString());
            }
        }
        return entries;
    }

    private static void resetToDefaults() {
        stages = new LinkedHashMap<>();
        bosses = new LinkedHashMap<>();
        miniBosses = new LinkedHashMap<>();
        // First-party variants, not vanilla mobs. This table is the fallback a piso with no roster
        // of its own gets, so it has to work on the server that actually runs: Pixelmon's
        // MobSpawnReplacement swaps every joining vanilla monster for a Pokémon, and a fallback
        // wave of zombies and skeletons is deleted on spawn — a floor that never seals, with
        // nothing in the log to say why. Geo enemies are never vanilla monsters, and unlike the
        // CustomNPCs clones they need no `enemigos instalar` before they exist.
        stages.put(DEFAULT_KEY, new StageTable(3, 5, List.of(
                new SpawnEntry(Kind.GEO, "saqueador_cuevas", 0, 3),
                new SpawnEntry(Kind.GEO, "arquero_gruta", 0, 2),
                new SpawnEntry(Kind.GEO, "husk_guardian", 0, 1))));
        bosses.put(DEFAULT_KEY, List.of(
                new SpawnEntry(Kind.GEO, "coloso_guardian", 0, 1)));
        miniBosses.put(DEFAULT_KEY, List.of(
                new SpawnEntry(Kind.GEO, "centinela_hueso", 0, 1)));
    }

    private static JsonObject renderDefaults() {
        JsonObject root = new JsonObject();
        JsonObject stagesJson = new JsonObject();
        stagesJson.add(DEFAULT_KEY, renderStage(stages.get(DEFAULT_KEY)));
        root.add("stages", stagesJson);
        root.add("bosses", renderPools(bosses));
        root.add("miniBosses", renderPools(miniBosses));
        // Written out even though the vanilla fallback wave cannot use them: the block is where an
        // admin discovers abilities exist and what the shipped tuning looks like.
        root.add("abilities", Abilities.render(DungeonEnemyPacks.abilities()));
        return root;
    }

    private static JsonObject renderStage(StageTable table) {
        JsonObject json = new JsonObject();
        json.addProperty("countMin", table.countMin());
        json.addProperty("countMax", table.countMax());
        json.add("wave", renderPool(table.wave()));
        return json;
    }

    private static JsonObject renderPools(Map<String, List<SpawnEntry>> pools) {
        JsonObject json = new JsonObject();
        for (Map.Entry<String, List<SpawnEntry>> pool : pools.entrySet()) {
            json.add(pool.getKey(), renderPool(pool.getValue()));
        }
        return json;
    }

    private static JsonArray renderPool(List<SpawnEntry> pool) {
        JsonArray array = new JsonArray();
        for (SpawnEntry entry : pool) {
            JsonObject obj = new JsonObject();
            obj.addProperty("kind", entry.kind().name().toLowerCase(java.util.Locale.ROOT));
            if (entry.kind() == Kind.CNPC) {
                obj.addProperty("name", entry.id());
                obj.addProperty("tab", entry.tab());
            } else {
                obj.addProperty("id", entry.id());
            }
            obj.addProperty("weight", entry.weight());
            array.add(obj);
        }
        return array;
    }
}
