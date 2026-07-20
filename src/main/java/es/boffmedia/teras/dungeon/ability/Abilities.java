package es.boffmedia.teras.dungeon.ability;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which abilities each enemy carries, keyed by the enemy id that {@code EnemySpawner} stamps onto
 * every spawn as a scoreboard tag.
 *
 * <p>Keying on a tag rather than the NPC's display name is deliberate: installed clones are
 * ordinary CustomNPCs data afterwards and renaming one in the NPC editor is the intended workflow,
 * so a name-based lookup would break the moment anyone used the tool as designed. The tag also
 * works unchanged for {@code teras:dungeon_enemy}, which has no CustomNPCs identity at all.</p>
 *
 * <p>Loaded from the {@code "abilities"} block of {@code enemies.json} alongside the spawn tables;
 * absent means the built-in defaults from {@code DungeonEnemyPacks}.</p>
 */
public final class Abilities {
    private Abilities() {}

    /** Prefixed rather than colon-separated so the id survives as a plain scoreboard tag. */
    public static final String TAG_PREFIX = "teras_enemy_";

    private static Map<String, List<AbilityDef>> byEnemy = new LinkedHashMap<>();

    /** The enemy id stamped on {@code entity}, or null when it is not one of ours. */
    public static String enemyId(Entity entity) {
        for (String tag : entity.getTags()) {
            if (tag.startsWith(TAG_PREFIX)) {
                return tag.substring(TAG_PREFIX.length());
            }
        }
        return null;
    }

    /** Abilities on {@code entity}; empty for anything unrecognised, so callers need no guard. */
    public static List<AbilityDef> of(Entity entity) {
        String id = enemyId(entity);
        return id == null ? List.of() : byEnemy.getOrDefault(id, List.of());
    }

    /**
     * Seeds the built-in table without announcing it. {@code SpawnTables.load} calls this before
     * reading {@code enemies.json} so a parse failure still leaves the built-ins standing; it is not
     * the load anyone is waiting to see reported, and logging it made a correct startup read as
     * though the table had been loaded twice over.
     */
    public static void seed(Map<String, List<AbilityDef>> defaults) {
        load(defaults, null, false);
    }

    /** Replaces the table wholesale; called from {@code SpawnTables.load}. */
    public static void load(Map<String, List<AbilityDef>> defaults, JsonObject json) {
        load(defaults, json, true);
    }

    private static void load(Map<String, List<AbilityDef>> defaults, JsonObject json,
                             boolean announce) {
        Map<String, List<AbilityDef>> loaded = new LinkedHashMap<>(defaults);
        if (json != null) {
            for (String enemy : json.keySet()) {
                JsonElement element = json.get(enemy);
                if (!element.isJsonArray()) {
                    Teras.LOGGER.warn("Dungeons: abilities for '{}' should be a list, ignoring", enemy);
                    continue;
                }
                // An explicit empty list is how an admin turns an enemy's abilities off, so it
                // overwrites the built-in default rather than falling through to it.
                loaded.put(enemy, readList(element, enemy));
            }
        }
        byEnemy = Map.copyOf(loaded);
        if (announce) {
            Teras.LOGGER.info("Dungeons: abilities loaded for {} enemies", byEnemy.size());
        }
    }

    private static List<AbilityDef> readList(JsonElement element, String enemy) {
        List<AbilityDef> defs = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return defs;
        }
        for (JsonElement item : element.getAsJsonArray()) {
            try {
                JsonObject obj = item.getAsJsonObject();
                AbilityKind kind = AbilityKind.valueOf(
                        obj.get("kind").getAsString().toUpperCase(Locale.ROOT));
                String arg = obj.has("arg") ? obj.get("arg").getAsString() : "";
                Map<String, Double> params = new LinkedHashMap<>();
                if (obj.has("params")) {
                    JsonObject raw = obj.getAsJsonObject("params");
                    for (String key : raw.keySet()) {
                        params.put(key, raw.get(key).getAsDouble());
                    }
                }
                defs.add(new AbilityDef(kind, arg, params));
            } catch (Exception e) {
                // One bad entry costs that ability, never the enemy and never the floor.
                Teras.LOGGER.warn("Dungeons: skipping bad ability on '{}' ({}): {}",
                        enemy, item, e.toString());
            }
        }
        return defs;
    }

    /** Renders the table back out, for the installer that rewrites {@code enemies.json}. */
    public static JsonObject render(Map<String, List<AbilityDef>> table) {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, List<AbilityDef>> entry : table.entrySet()) {
            JsonArray array = new JsonArray();
            for (AbilityDef def : entry.getValue()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("kind", def.kind().name().toLowerCase(Locale.ROOT));
                if (!def.arg().isEmpty()) {
                    obj.addProperty("arg", def.arg());
                }
                if (!def.params().isEmpty()) {
                    JsonObject params = new JsonObject();
                    def.params().forEach(params::addProperty);
                    obj.add("params", params);
                }
                array.add(obj);
            }
            root.add(entry.getKey(), array);
        }
        return root;
    }
}
