package es.boffmedia.teras.dungeon.build;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.model.DungeonSeeds;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.RoomShape;
import es.boffmedia.teras.dungeon.model.SeededRng;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Weighted room-template pools per theme, from {@code config/teras/dungeons/rooms.json}. Every
 * pool key a theme does not define inherits the built-in {@code base} pool (which points at the
 * templates shipped in the mod jar), so selection always resolves — the legacy paster had exactly
 * one loose schematic file per type and crashed the build when one was missing on disk.
 *
 * <p>Selection is derived from the layout's base seed and the room's placement index, never from
 * world randomness: the same seed rebuilds the same floor down to each room's variant.</p>
 *
 * <p>Server admins add or replace templates with datapacks or the world's {@code generated}
 * structure folder (where in-game structure blocks save), then point entries here at them.
 * An entry's {@code rotation} (0/90/180/270) reuses one template for several orientations.</p>
 */
public final class RoomTemplates {
    private RoomTemplates() {}

    public record TemplateEntry(ResourceLocation template, int weight, Rotation rotation) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, Map<String, List<TemplateEntry>>> themes = new LinkedHashMap<>();

    public static void load() {
        themes = new LinkedHashMap<>();
        themes.put("base", builtInBase());
        Path path = FMLPaths.CONFIGDIR.get().resolve("teras").resolve("dungeons").resolve("rooms.json");
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(render(builtInBase())));
                Teras.LOGGER.info("Dungeons: created default {}", path);
                return;
            }
            JsonObject root;
            try (Reader reader = Files.newBufferedReader(path)) {
                root = GSON.fromJson(reader, JsonObject.class);
            }
            for (String theme : root.keySet()) {
                Map<String, List<TemplateEntry>> pools =
                        themes.computeIfAbsent(theme, t -> new LinkedHashMap<>(builtInBase()));
                JsonObject themeJson = root.getAsJsonObject(theme);
                for (String key : themeJson.keySet()) {
                    List<TemplateEntry> entries = readPool(themeJson.get(key));
                    if (!entries.isEmpty()) {
                        pools.put(key, entries);
                    }
                }
            }
            Teras.LOGGER.info("Dungeons: room pools loaded from {} ({} themes)", path, themes.size());
        } catch (Exception e) {
            Teras.LOGGER.warn("Dungeons: failed to load rooms.json, using built-ins: {}", e.toString());
        }
    }

    /** Deterministic weighted pick for a room; {@code roomIndex} is its position in placement order. */
    public static TemplateEntry select(String theme, Room room, long baseSeed, int roomIndex) {
        Map<String, List<TemplateEntry>> pools = themes.getOrDefault(theme, themes.get("base"));
        Map<String, List<TemplateEntry>> base = themes.get("base");
        List<TemplateEntry> pool = null;
        for (String key : poolKeys(room)) {
            pool = firstNonEmpty(pools.get(key), base.get(key));
            if (pool != null) {
                break;
            }
        }
        if (pool == null) {
            // Never throw for a missing pool. The exception would be swallowed by the materializer's
            // job loop, which drops the build — leaving the run waiting on a floor that never lands.
            Teras.LOGGER.error("Dungeons: no template pool for {} — falling back to the plain room", room);
            pool = base.get("normal");
        }
        SeededRng rng = new SeededRng(DungeonSeeds.derive(baseSeed, 0x726F6F6DL + roomIndex));
        int total = pool.stream().mapToInt(TemplateEntry::weight).sum();
        int roll = rng.between(1, Math.max(1, total));
        for (TemplateEntry entry : pool) {
            roll -= entry.weight();
            if (roll <= 0) {
                return entry;
            }
        }
        return pool.get(pool.size() - 1);
    }

    /** Every pool key the built-in base theme defines — the room-type vocabulary, for the editor. */
    public static List<String> knownPoolKeys() {
        return List.copyOf(builtInBase().keySet());
    }

    /**
     * The resolved pool for one key: the theme's entries, else the base theme's, else empty.
     * The room editor uses this to offer variants; {@link #select} keeps its own resolution
     * because it also falls across {@link #poolKeys} shape fallbacks.
     */
    public static List<TemplateEntry> pool(String theme, String poolKey) {
        Map<String, List<TemplateEntry>> pools = themes.getOrDefault(theme, themes.get("base"));
        List<TemplateEntry> entries = pools.get(poolKey);
        if (entries == null || entries.isEmpty()) {
            entries = themes.get("base").get(poolKey);
        }
        return entries == null ? List.of() : List.copyOf(entries);
    }

    /**
     * Appends a template to a pool in {@code rooms.json} and reloads. Used by the room editor's
     * save-as-variant: the new room becomes one more weighted pick alongside what was there.
     *
     * <p>When the file's theme lacks the pool key entirely, the pool is seeded from the built-in
     * base first — {@code load()} treats a present-but-partial theme as "inherit the rest from
     * base", so writing a one-entry array would silently drop the shipped room from selection.</p>
     */
    public static boolean addTemplate(String theme, String poolKey, ResourceLocation template, int weight) {
        Path path = FMLPaths.CONFIGDIR.get().resolve("teras").resolve("dungeons").resolve("rooms.json");
        try {
            JsonObject root;
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    root = GSON.fromJson(reader, JsonObject.class);
                }
                if (root == null) {
                    root = render(builtInBase());
                }
            } else {
                root = render(builtInBase());
            }
            JsonObject themeJson = root.has(theme) ? root.getAsJsonObject(theme) : new JsonObject();
            root.add(theme, themeJson);
            JsonArray pool = themeJson.has(poolKey) ? themeJson.getAsJsonArray(poolKey) : null;
            if (pool == null) {
                pool = new JsonArray();
                for (TemplateEntry builtin : builtInBase().getOrDefault(poolKey, List.of())) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("template", builtin.template().toString());
                    obj.addProperty("weight", builtin.weight());
                    pool.add(obj);
                }
                themeJson.add(poolKey, pool);
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("template", template.toString());
            entry.addProperty("weight", Math.max(1, weight));
            pool.add(entry);
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root));
            load();
            return true;
        } catch (Exception e) {
            Teras.LOGGER.error("Dungeons: could not register template {} in rooms.json: {}",
                    template, e.toString());
            return false;
        }
    }

    /**
     * Preferred pool first, then the fallbacks that still cover the room's footprint: a 2×2 boss
     * asks for {@code boss_quad} and settles for {@code normal_quad}, which is the wrong furniture
     * but the right shape. Singles pool by type alone.
     *
     * <p>For every room the carver produces on its own this is unchanged — large rooms are always
     * NORMAL, so the preferred key is already {@code normal_<shape>}.</p>
     */
    static List<String> poolKeys(Room room) {
        String type = room.type().name().toLowerCase(Locale.ROOT);
        if (room.shape() == RoomShape.SINGLE) {
            return List.of(type);
        }
        String shape = room.shape().name().toLowerCase(Locale.ROOT);
        return List.of(type + "_" + shape, "normal_" + shape);
    }

    private static List<TemplateEntry> firstNonEmpty(List<TemplateEntry> preferred,
                                                     List<TemplateEntry> fallback) {
        if (preferred != null && !preferred.isEmpty()) {
            return preferred;
        }
        return fallback != null && !fallback.isEmpty() ? fallback : null;
    }

    private static List<TemplateEntry> readPool(JsonElement pool) {
        List<TemplateEntry> entries = new ArrayList<>();
        if (!pool.isJsonArray()) {
            return entries;
        }
        for (JsonElement element : pool.getAsJsonArray()) {
            try {
                JsonObject obj = element.getAsJsonObject();
                ResourceLocation template = ResourceLocation.parse(obj.get("template").getAsString());
                int weight = obj.has("weight") ? Math.max(1, obj.get("weight").getAsInt()) : 1;
                Rotation rotation = parseRotation(obj.has("rotation") ? obj.get("rotation").getAsInt() : 0);
                entries.add(new TemplateEntry(template, weight, rotation));
            } catch (Exception e) {
                Teras.LOGGER.warn("Dungeons: skipping bad rooms.json entry {}: {}", element, e.toString());
            }
        }
        return entries;
    }

    private static Rotation parseRotation(int degrees) {
        return switch (degrees) {
            case 90 -> Rotation.CLOCKWISE_90;
            case 180 -> Rotation.CLOCKWISE_180;
            case 270 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static JsonObject render(Map<String, List<TemplateEntry>> base) {
        JsonObject root = new JsonObject();
        JsonObject theme = new JsonObject();
        for (Map.Entry<String, List<TemplateEntry>> pool : base.entrySet()) {
            JsonArray entries = new JsonArray();
            for (TemplateEntry entry : pool.getValue()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("template", entry.template().toString());
                obj.addProperty("weight", entry.weight());
                entries.add(obj);
            }
            theme.add(pool.getKey(), entries);
        }
        root.add("base", theme);
        return root;
    }

    private static Map<String, List<TemplateEntry>> builtInBase() {
        Map<String, List<TemplateEntry>> pools = new LinkedHashMap<>();
        for (String name : new String[] {
                "start", "normal", "boss", "boss_quad", "mini_boss", "shop", "treasure",
                "secret", "super_secret", "challenge", "curse",
                "normal_horizontal", "normal_vertical", "normal_quad",
                "normal_l_top_left", "normal_l_top_right",
                "normal_l_bottom_left", "normal_l_bottom_right"}) {
            pools.put(name, List.of(new TemplateEntry(
                    ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "dungeon/base/" + name),
                    1, Rotation.NONE)));
        }
        return pools;
    }
}
