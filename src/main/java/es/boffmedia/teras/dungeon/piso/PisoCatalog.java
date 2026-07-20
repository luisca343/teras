package es.boffmedia.teras.dungeon.piso;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.RoomShape;
import es.boffmedia.teras.dungeon.model.ShapeFamily;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Loads the piso catalog ({@code pisos/*.json}) and the dungeons that reference it
 * ({@code mazmorras.json}).
 *
 * <p><b>Nothing is repaired.</b> With the old themes a missing room quietly borrowed another
 * theme's; pisos have no fallback, so a piso that fails validation is dropped from the catalog with
 * a log line naming what is wrong, and a dungeon naming a dropped piso is dropped in turn. The
 * alternative is discovering it inside the materializer's job loop, which swallows exceptions and
 * leaves a run waiting on a floor that never lands.</p>
 *
 * <p>This is the only class in {@code dungeon/piso} that touches the game; everything it produces is
 * the plain model beside it, so selection and validation stay testable.</p>
 */
public final class PisoCatalog {
    private PisoCatalog() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Every piso that parsed and passed its structural checks — including ones whose templates do
     * not exist yet. Selection must never see these, but the authoring commands have to: "which
     * rooms does this piso still owe" is the question they exist to answer.
     */
    private static Map<String, FloorDef> declared = new LinkedHashMap<>();
    /** The subset whose templates all resolve. What a floor may actually be built from. */
    private static Map<String, FloorDef> pisos = new LinkedHashMap<>();
    private static Map<String, List<String>> missingRooms = new LinkedHashMap<>();
    private static Map<String, List<String>> mismatched = new LinkedHashMap<>();
    private static Map<String, DungeonDef> dungeons = new LinkedHashMap<>();

    static {
        resetToDefaults();
    }

    public static void load() {
        resetToDefaults();
        Path dir = FMLPaths.CONFIGDIR.get().resolve("teras").resolve("dungeons");
        try {
            loadPisos(dir.resolve("pisos"));
            loadDungeons(dir.resolve("mazmorras.json"));
        } catch (Exception e) {
            Teras.LOGGER.warn("Dungeons: failed to load pisos/mazmorras, using defaults: {}",
                    e.toString());
            resetToDefaults();
        }
        Teras.LOGGER.info("Dungeons: {} pisos, {} mazmorras loaded", pisos.size(), dungeons.size());
    }

    /**
     * Drops pisos whose templates do not exist. Runs at server start, because it is the one check
     * that needs the game — a piso passes its structural validation naming templates that resolve
     * to nothing on disk, and with no fallback between pisos that is fatal to any floor it fills.
     */
    public static void validateTemplates(
            net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager manager) {
        Map<String, FloorDef> usable = new LinkedHashMap<>();
        missingRooms = new LinkedHashMap<>();
        mismatched = new LinkedHashMap<>();
        for (FloorDef piso : declared.values()) {
            List<String> missing =
                    es.boffmedia.teras.dungeon.build.RoomTemplates.missingTemplates(piso, manager);
            if (missing.isEmpty()) {
                usable.put(piso.id(), piso);
                List<String> wrongSize = es.boffmedia.teras.dungeon.build.RoomTemplates
                        .mismatchedTemplates(piso, manager,
                                es.boffmedia.teras.dungeon.build.DungeonsConfig.roomSize(),
                                es.boffmedia.teras.dungeon.build.DungeonsConfig.roomHeight());
                mismatched.put(piso.id(), wrongSize);
                if (!wrongSize.isEmpty()) {
                    Teras.LOGGER.error("Dungeons: piso '{}' has {} template(s) the wrong size for "
                            + "the configured cell. Either config.yml's tamanoSala/alturaSala is "
                            + "stale (a default only applies to a file that does not exist yet), or "
                            + "stale copies in the world's 'generated' folder are shadowing the "
                            + "jar's. {}", piso.id(), wrongSize.size(), String.join("; ", wrongSize));
                }
                continue;
            }
            missingRooms.put(piso.id(), missing);
            Teras.LOGGER.error("Dungeons: piso '{}' is missing {} of its {} rooms and will never be "
                            + "selected — author them or narrow its 'formas'. Missing: {}",
                    piso.id(), missing.size(), piso.requiredRooms().size(),
                    String.join(", ", missing));
        }
        pisos = usable;
        // A dungeon whose tramo has lost every piso can no longer build a floor; say so once here
        // rather than when a party is standing at the entrance.
        for (DungeonDef dungeon : dungeons.values()) {
            List<String> problems = dungeon.problems(pisos);
            if (!problems.isEmpty()) {
                Teras.LOGGER.error("Dungeons: mazmorra '{}' cannot run — {}",
                        dungeon.id(), String.join("; ", problems));
            }
        }
    }

    /** Pisos a floor may be built from. Selection uses only these. */
    public static Map<String, FloorDef> pisos() {
        return Map.copyOf(pisos);
    }

    /** Every piso in the config, usable or not — what the authoring commands work on. */
    public static Map<String, FloorDef> declared() {
        return Map.copyOf(declared);
    }

    public static FloorDef piso(String id) {
        return pisos.get(id);
    }

    /** A piso by id whether or not its templates exist, for authoring. */
    public static FloorDef declaredPiso(String id) {
        return declared.get(id);
    }

    public static boolean isUsable(String id) {
        return pisos.containsKey(id);
    }

    /** Templates of {@code id} whose size disagrees with the configured cell. */
    public static List<String> mismatchedRooms(String id) {
        return List.copyOf(mismatched.getOrDefault(id, List.of()));
    }

    /** The rooms {@code id} still owes, as {@code key -> template}. Empty when it owes none. */
    public static List<String> missingRooms(String id) {
        return List.copyOf(missingRooms.getOrDefault(id, List.of()));
    }

    public static DungeonDef dungeon(String id) {
        return dungeons.get(id);
    }

    /**
     * The dungeon used where a command does not name one. There is one mazmorra today, and the
     * admin commands predate the concept; they gain an explicit argument with the authoring
     * commands rather than guessing forever.
     */
    public static String defaultDungeonId() {
        return dungeons.isEmpty() ? null : dungeons.keySet().iterator().next();
    }

    /**
     * Registers a template as another weighted variant of {@code roomKey} on a piso, rewriting its
     * file. The room editor's save-as-variant: the new room becomes one more draw alongside what
     * was there.
     *
     * <p>Seeds the list with the piso's conventional template first when it had none, because an
     * absent key means "the one conventional room" — writing only the new one would silently drop
     * the original from selection.</p>
     */
    public static String addVariant(String pisoId, String roomKey, String template, int weight) {
        FloorDef piso = declared.get(pisoId);
        if (piso == null) {
            return "No existe el piso '" + pisoId + "'.";
        }
        Path file = FMLPaths.CONFIGDIR.get().resolve("teras").resolve("dungeons")
                .resolve("pisos").resolve(pisoId + ".json");
        try {
            JsonObject json;
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    json = GSON.fromJson(reader, JsonObject.class);
                }
            } else {
                json = render(piso);
            }
            JsonObject salas = json.has("salas") ? json.getAsJsonObject("salas") : new JsonObject();
            json.add("salas", salas);
            JsonArray list = salas.has(roomKey) ? salas.getAsJsonArray(roomKey) : null;
            if (list == null) {
                list = new JsonArray();
                for (RoomVariant existing : piso.variants(roomKey)) {
                    list.add(variantJson(existing));
                }
                salas.add(roomKey, list);
            }
            list.add(variantJson(new RoomVariant(template, weight, 0)));
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(json));
            load();
            return null;
        } catch (Exception e) {
            Teras.LOGGER.error("Dungeons: could not add variant to piso '{}': {}", pisoId, e.toString());
            return "No se pudo escribir el piso: " + e;
        }
    }

    /**
     * Removes one variant of a room key, reloading afterwards. The counterpart to
     * {@link #addVariant}: a room saved from the editor and then judged bad could otherwise only be
     * taken out by hand-editing the file, and it keeps being built in the meantime.
     *
     * <p>The last entry is refused rather than removed. An empty list reads back as "no variants
     * declared", which resolves to the conventional template — so emptying the list would silently
     * restore the original rather than leave the key empty, a confusing way to find out the delete
     * did the opposite of what it said.</p>
     */
    public static String removeVariant(String pisoId, String roomKey, int index) {
        FloorDef piso = declared.get(pisoId);
        if (piso == null) {
            return "No existe el piso '" + pisoId + "'.";
        }
        Path file = FMLPaths.CONFIGDIR.get().resolve("teras").resolve("dungeons")
                .resolve("pisos").resolve(pisoId + ".json");
        try {
            if (!Files.exists(file)) {
                return "El piso '" + pisoId + "' no tiene archivo que editar.";
            }
            JsonObject json;
            try (Reader reader = Files.newBufferedReader(file)) {
                json = GSON.fromJson(reader, JsonObject.class);
            }
            JsonObject salas = json.has("salas") ? json.getAsJsonObject("salas") : null;
            JsonArray list = salas == null || !salas.has(roomKey)
                    ? null : salas.getAsJsonArray(roomKey);
            if (list == null) {
                return "'" + roomKey + "' no tiene variantes declaradas en " + pisoId + ".";
            }
            if (index < 0 || index >= list.size()) {
                return "Variante fuera de rango: '" + roomKey + "' tiene " + list.size() + ".";
            }
            if (list.size() == 1) {
                return "Es la única variante declarada de '" + roomKey + "' — deja al menos una.";
            }
            list.remove(index);
            Files.writeString(file, GSON.toJson(json));
            load();
            return null;
        } catch (Exception e) {
            Teras.LOGGER.error("Dungeons: could not remove variant {} of {} on piso '{}': {}",
                    index, roomKey, pisoId, e.toString());
            return "No se pudo escribir el piso: " + e;
        }
    }

    private static JsonObject variantJson(RoomVariant variant) {
        JsonObject obj = new JsonObject();
        obj.addProperty("template", variant.template());
        obj.addProperty("peso", variant.weight());
        if (variant.rotation() != 0) {
            obj.addProperty("rotacion", variant.rotation());
        }
        return obj;
    }

    /**
     * Writes {@code pisos/<newId>.json} derived from {@code source}, unless it already exists.
     *
     * <p>Copies the source's shapes, light and accepted curses — a variant starts as its parent and
     * is edited from there — but never its name, subtitle or boss override, which are the things
     * that make it a different place and must be chosen deliberately.</p>
     *
     * @return an error to show, or null when the file was written or already existed
     */
    public static String createFrom(String newId, FloorDef source) {
        if (newId == null || !newId.matches("[a-z0-9_]+")) {
            return "Id inválido '" + newId + "' — solo minúsculas, dígitos y '_'.";
        }
        Path file = FMLPaths.CONFIGDIR.get().resolve("teras").resolve("dungeons")
                .resolve("pisos").resolve(newId + ".json");
        if (Files.exists(file)) {
            return null;
        }
        FloorDef seeded = new FloorDef(newId, newId, "", source.formas(), source.luz(),
                source.musica(), source.ambiente(), "", source.maldiciones(),
                List.of(), List.of(), Map.of());
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(render(seeded)));
            return null;
        } catch (Exception e) {
            Teras.LOGGER.error("Dungeons: could not create piso '{}': {}", newId, e.toString());
            return "No se pudo escribir el piso: " + e;
        }
    }

    /** The piso the room editor opens when a command does not name one. */
    public static String defaultPisoId() {
        return pisos.isEmpty() ? null : pisos.keySet().iterator().next();
    }

    public static List<String> dungeonIds() {
        return List.copyOf(dungeons.keySet());
    }

    // --- reading ---------------------------------------------------------------------------------

    private static void loadPisos(Path dir) throws Exception {
        if (!Files.isDirectory(dir)) {
            Files.createDirectories(dir);
            for (FloorDef piso : pisos.values()) {
                Files.writeString(dir.resolve(piso.id() + ".json"), GSON.toJson(render(piso)));
            }
            Teras.LOGGER.info("Dungeons: created default pisos in {}", dir);
            return;
        }
        Map<String, FloorDef> loaded = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                String id = file.getFileName().toString().replaceAll("\\.json$", "");
                FloorDef piso = readPiso(id, file);
                if (piso == null) {
                    continue;
                }
                List<String> problems = piso.problems();
                if (!problems.isEmpty()) {
                    Teras.LOGGER.error("Dungeons: piso '{}' is unusable and will never be "
                            + "selected — {}", id, String.join("; ", problems));
                    continue;
                }
                loaded.put(id, piso);
            }
        }
        if (!loaded.isEmpty()) {
            declared = loaded;
            // Until validateTemplates runs there is nothing better to go on; the server-start pass
            // narrows this to the pisos that can actually build.
            pisos = loaded;
        }
    }

    private static FloorDef readPiso(String id, Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            return new FloorDef(id,
                    string(json, "nombre", id),
                    string(json, "subtitulo", ""),
                    shapes(json.get("formas")),
                    json.has("luz") ? json.get("luz").getAsInt() : 7,
                    string(json, "musica", ""),
                    string(json, "ambiente", ""),
                    string(json, "mecanica", ""),
                    curses(json.get("maldiciones")),
                    strings(json.get("jefes")),
                    strings(json.get("minijefes")),
                    salas(json.get("salas")));
        } catch (Exception e) {
            Teras.LOGGER.error("Dungeons: could not read piso '{}': {}", id, e.toString());
            return null;
        }
    }

    private static void loadDungeons(Path file) throws Exception {
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(renderDungeons()));
            Teras.LOGGER.info("Dungeons: created default {}", file);
            return;
        }
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(file)) {
            root = GSON.fromJson(reader, JsonObject.class);
        }
        if (root == null) {
            return;
        }
        Map<String, DungeonDef> loaded = new LinkedHashMap<>();
        for (String id : root.keySet()) {
            DungeonDef dungeon = readDungeon(id, root.getAsJsonObject(id));
            if (dungeon == null) {
                continue;
            }
            List<String> problems = dungeon.problems(pisos);
            if (!problems.isEmpty()) {
                Teras.LOGGER.error("Dungeons: mazmorra '{}' is unusable — {}",
                        id, String.join("; ", problems));
                continue;
            }
            loaded.put(id, dungeon);
        }
        if (!loaded.isEmpty()) {
            dungeons = loaded;
        }
    }

    private static DungeonDef readDungeon(String id, JsonObject json) {
        try {
            List<TierDef> tramos = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray("tramos")) {
                JsonObject tramo = element.getAsJsonObject();
                List<WeightedRef> refs = new ArrayList<>();
                for (JsonElement ref : tramo.getAsJsonArray("pisos")) {
                    JsonObject obj = ref.getAsJsonObject();
                    refs.add(new WeightedRef(obj.get("id").getAsString(),
                            obj.has("peso") ? obj.get("peso").getAsInt() : 1));
                }
                tramos.add(new TierDef(
                        tramo.has("largo") ? tramo.get("largo").getAsInt() : 2,
                        tramo.has("dificultad") ? tramo.get("dificultad").getAsDouble() : 1.0,
                        refs,
                        strings(tramo.get("jefes")),
                        strings(tramo.get("minijefes"))));
            }
            return new DungeonDef(id, string(json, "nombre", id), tramos);
        } catch (Exception e) {
            Teras.LOGGER.error("Dungeons: could not read mazmorra '{}': {}", id, e.toString());
            return null;
        }
    }

    // --- small readers ---------------------------------------------------------------------------

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }

    private static List<String> strings(JsonElement element) {
        List<String> values = new ArrayList<>();
        if (element != null && element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                values.add(item.getAsString());
            }
        }
        return values;
    }

    /**
     * Unknown names are skipped rather than failing the piso: a shape or curse added to the enums
     * later must not brick a config written before it existed.
     */
    /**
     * Reads {@code formas}, accepting the per-orientation names files were written with before
     * shapes collapsed into families.
     *
     * <p>Without the migration a config listing {@code horizontal, vertical, quad, l_top_left…}
     * parses down to {@code SINGLE} alone — every one of those names is a {@link RoomShape}, none is
     * a {@link ShapeFamily} — and the floor silently stops generating anything but single cells,
     * boss rooms included. A file being quietly reinterpreted as something far smaller is the worst
     * shape a config change can take.</p>
     */
    private static Set<ShapeFamily> shapes(JsonElement element) {
        Set<ShapeFamily> families = EnumSet.noneOf(ShapeFamily.class);
        List<String> names = strings(element);
        int migrated = 0;
        for (String name : names) {
            String key = name.trim().toUpperCase(Locale.ROOT);
            try {
                families.add(ShapeFamily.valueOf(key));
                continue;
            } catch (IllegalArgumentException ignored) {
                // not a family; try the orientation names that predate them
            }
            try {
                families.add(RoomShape.valueOf(key).family());
                migrated++;
            } catch (IllegalArgumentException e) {
                Teras.LOGGER.warn("Dungeons: unknown forma '{}' skipped — "
                        + "expected one of single, large, l, big", name);
            }
        }
        if (migrated > 0) {
            Teras.LOGGER.info("Dungeons: a piso lists {} forma(s) by orientation ({}); read as "
                    + "families {}. Rewrite them as single/large/l/big when convenient.",
                    migrated, names, families);
        }
        if (families.isEmpty() && !names.isEmpty()) {
            Teras.LOGGER.error("Dungeons: a piso declared formas {} and none of them parsed — the "
                    + "piso is being rejected rather than silently reduced to single cells", names);
        }
        return families;
    }

    /** Authored variants per room key. Absent keys resolve to the piso's conventional template. */
    private static Map<String, List<RoomVariant>> salas(JsonElement element) {
        Map<String, List<RoomVariant>> salas = new LinkedHashMap<>();
        if (element == null || !element.isJsonObject()) {
            return salas;
        }
        JsonObject json = element.getAsJsonObject();
        for (String key : json.keySet()) {
            List<RoomVariant> variants = new ArrayList<>();
            for (JsonElement item : json.getAsJsonArray(key)) {
                JsonObject obj = item.getAsJsonObject();
                variants.add(new RoomVariant(obj.get("template").getAsString(),
                        obj.has("peso") ? obj.get("peso").getAsInt() : 1,
                        obj.has("rotacion") ? obj.get("rotacion").getAsInt() : 0));
            }
            if (!variants.isEmpty()) {
                salas.put(key, variants);
            }
        }
        return salas;
    }

    private static Set<Curse> curses(JsonElement element) {
        Set<Curse> curses = EnumSet.noneOf(Curse.class);
        for (String name : strings(element)) {
            try {
                curses.add(Curse.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                Teras.LOGGER.warn("Dungeons: unknown maldicion '{}' skipped", name);
            }
        }
        return curses;
    }

    // --- defaults --------------------------------------------------------------------------------

    /**
     * Cuevas and Cuevas Infestadas, and the one tramo they fill. La Cripta ships as a <b>two-floor
     * dungeon</b> on purpose: tramos 2 and 3 are not designed yet, and a dungeon padded with
     * placeholder tramos would look finished while playing as three copies of the same floor.
     * Length is emergent, so adding a tramo later lengthens it with no other change.
     */
    private static void resetToDefaults() {
        pisos = new LinkedHashMap<>();
        missingRooms = new LinkedHashMap<>();
        pisos.put("cuevas", new FloorDef("cuevas", "Cuevas", "el aire huele a piedra húmeda",
                EnumSet.allOf(ShapeFamily.class), 7,
                "minecraft:music.overworld.dripstone_caves", "minecraft:ambient.cave", "",
                EnumSet.of(Curse.LABYRINTH, Curse.LOST), List.of(), List.of(), Map.of()));
        // Two shapes only: tight and choked is the identity, and it excuses six of the 21 rooms.
        // LABYRINTH is refused for the same reason — at two shapes it would sprawl to the room cap
        // in one repeated footprint.
        pisos.put("cuevas_infestadas", new FloorDef("cuevas_infestadas", "Cuevas Infestadas",
                "algo se mueve en la oscuridad",
                EnumSet.of(ShapeFamily.SINGLE, ShapeFamily.LARGE), 4,
                "minecraft:music.overworld.dripstone_caves", "minecraft:ambient.cave", "infestacion",
                EnumSet.of(Curse.LOST), List.of("reina_cria"), List.of(), Map.of()));

        declared = pisos;
        dungeons = new LinkedHashMap<>();
        dungeons.put("cripta", new DungeonDef("cripta", "La Cripta", List.of(
                new TierDef(2, 1.0,
                        List.of(new WeightedRef("cuevas", 3),
                                new WeightedRef("cuevas_infestadas", 1)),
                        List.of("coloso_guardian"), List.of("centinela_hueso")))));
    }

    private static JsonObject render(FloorDef piso) {
        JsonObject json = new JsonObject();
        json.addProperty("nombre", piso.nombre());
        json.addProperty("subtitulo", piso.subtitulo());
        json.add("formas", names(piso.formas().stream().map(Enum::name).toList()));
        json.addProperty("luz", piso.luz());
        json.addProperty("musica", piso.musica());
        json.addProperty("ambiente", piso.ambiente());
        json.addProperty("mecanica", piso.mecanica());
        json.add("maldiciones", names(piso.maldiciones().stream().map(Enum::name).toList()));
        json.add("jefes", names(piso.jefes()));
        json.add("minijefes", names(piso.minijefes()));
        JsonObject salas = new JsonObject();
        for (Map.Entry<String, List<RoomVariant>> entry : piso.salas().entrySet()) {
            JsonArray list = new JsonArray();
            for (RoomVariant variant : entry.getValue()) {
                list.add(variantJson(variant));
            }
            salas.add(entry.getKey(), list);
        }
        json.add("salas", salas);
        return json;
    }

    private static JsonObject renderDungeons() {
        JsonObject root = new JsonObject();
        for (DungeonDef dungeon : dungeons.values()) {
            JsonObject json = new JsonObject();
            json.addProperty("nombre", dungeon.nombre());
            JsonArray tramos = new JsonArray();
            for (TierDef tier : dungeon.tramos()) {
                JsonObject tramo = new JsonObject();
                tramo.addProperty("largo", tier.largo());
                tramo.addProperty("dificultad", tier.dificultad());
                JsonArray refs = new JsonArray();
                for (WeightedRef ref : tier.pisos()) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", ref.id());
                    obj.addProperty("peso", ref.weight());
                    refs.add(obj);
                }
                tramo.add("pisos", refs);
                tramo.add("jefes", names(tier.jefes()));
                tramo.add("minijefes", names(tier.minijefes()));
                tramos.add(tramo);
            }
            json.add("tramos", tramos);
            root.add(dungeon.id(), json);
        }
        return root;
    }

    private static JsonArray names(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value.toLowerCase(Locale.ROOT));
        }
        return array;
    }
}
