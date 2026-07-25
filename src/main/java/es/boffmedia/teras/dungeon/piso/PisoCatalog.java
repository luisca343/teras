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
    /**
     * The factory defaults, kept apart from {@code pisos}/{@code declared} because those are
     * overwritten by whatever is on disk. This is what {@link #resyncPiso} rewrites a config back
     * to — the answer to "a shipped content change never reaches a file that already exists".
     */
    private static Map<String, FloorDef> shipped = new LinkedHashMap<>();
    private static Map<String, List<String>> missingRooms = new LinkedHashMap<>();
    private static Map<String, List<String>> mismatched = new LinkedHashMap<>();
    private static Map<String, DungeonDef> dungeons = new LinkedHashMap<>();

    /**
     * How deep the canonical sequence goes, so a dungeon window past its end is refused here.
     * Read per call, not captured at class-init: the curve comes from config.yml, and this class
     * can load before or after it.
     */
    private static int canonicalFloors() {
        return es.boffmedia.teras.dungeon.build.DungeonsConfig.genConfig().canonicalFloors();
    }

    /** One entry of the retired {@code salas} block. */
    private record LegacyVariant(String template, double weight) {}

    /**
     * What a migrated room with no name of its own becomes. The old layout had one template per key
     * so the file was named after the key; a folder full of files called {@code normal} would say
     * nothing, and this is the name an admin sees in {@code sala listar} from then on.
     */
    private static final String MIGRATED_NAME = "original";

    /**
     * The {@code salas} blocks still on disk, {@code piso -> key -> variants}. Nothing reads these
     * to build anything — room membership comes from the folder layout now — they are kept only so
     * {@code piso migrar} can carry an old file's weights across, and so load can say out loud that
     * the block is being ignored.
     */
    private static Map<String, Map<String, List<LegacyVariant>>> legacySalas = new LinkedHashMap<>();

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
        // The folder layout is the only record of which templates exist, so it is read before
        // anything asks a piso what it can build.
        es.boffmedia.teras.dungeon.build.RoomPools.rebuild(manager);
        for (FloorDef piso : declared.values()) {
            for (String problem : es.boffmedia.teras.dungeon.build.RoomPools.index()
                    .problems(piso)) {
                Teras.LOGGER.warn("Dungeons: piso '{}' {}", piso.id(), problem);
            }
            // A mechanic id that does not exist used to be indistinguishable from having none.
            es.boffmedia.teras.dungeon.mecanica.Mechanics.report(piso);
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
            List<String> problems = dungeon.problems(pisos, canonicalFloors());
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
     * Sets the draw weight of one variant of one room key, rewriting the piso's file.
     *
     * <p>This is the whole of what a piso may say about its rooms. Membership comes from the folder
     * and nothing else, so this can make a room rarer, commoner, or (at 0) switch off one it
     * inherits from a shared set — but it can never conjure one that is not on disk, nor hide one
     * that is by omission. That asymmetry is the point: the folder and the weights answer different
     * questions, so they cannot disagree the way the old {@code salas} list could.</p>
     *
     * @param weight relative; 1.0 is the default every unlisted variant already has, so setting it
     *               removes the entry rather than writing a line that says nothing
     * @return an error to show, or null on success
     */
    public static String setWeight(String pisoId, String roomKey, String variantName,
                                   double weight) {
        FloorDef piso = declared.get(pisoId);
        if (piso == null) {
            return "No existe el piso '" + pisoId + "'.";
        }
        if (weight < 0) {
            return "El peso no puede ser negativo (0 = desactivada).";
        }
        Path file = pisoFile(pisoId);
        try {
            JsonObject json = Files.exists(file) ? readJson(file) : render(piso);
            JsonObject pesos = json.has("pesos") ? json.getAsJsonObject("pesos") : new JsonObject();
            json.add("pesos", pesos);
            JsonObject forKey = pesos.has(roomKey)
                    ? pesos.getAsJsonObject(roomKey) : new JsonObject();
            if (weight == FloorDef.DEFAULT_WEIGHT) {
                forKey.remove(variantName);
            } else {
                forKey.addProperty(variantName, weight);
            }
            if (forKey.size() == 0) {
                pesos.remove(roomKey);
            } else {
                pesos.add(roomKey, forKey);
            }
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(json));
            load();
            return null;
        } catch (Exception e) {
            Teras.LOGGER.error("Dungeons: could not set weight on piso '{}': {}", pisoId, e.toString());
            return "No se pudo escribir el piso: " + e;
        }
    }

    /**
     * Moves a piso's templates from the old flat layout into per-key folders and converts any
     * {@code salas} block into {@code pesos}.
     *
     * <p>Needed exactly once per server. Room templates used to be {@code dungeon/<piso>/<key>.nbt}
     * with the variants listed in the piso's json; they are now {@code dungeon/<piso>/<key>/<name>.nbt}
     * with the folder as the list. The jar's own templates ship in the new layout, but every room an
     * admin authored lives in the world's {@code generated} folder and would simply stop being
     * found — not an error anywhere, just work quietly falling out of the rotation. This moves it.
     * </p>
     *
     * <p>The old file is <b>moved</b>, not copied: leaving it would keep shadowing nothing while
     * looking like a template that still matters.</p>
     *
     * @return a human-readable report, or an error
     */
    public static String migratePiso(net.minecraft.server.MinecraftServer server, String pisoId) {
        FloorDef piso = declared.get(pisoId);
        if (piso == null) {
            return "No existe el piso '" + pisoId + "'.";
        }
        var manager = server.getStructureManager();
        List<String> moved = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        Map<String, Map<String, Double>> pesos = new LinkedHashMap<>();

        for (String key : piso.requiredRooms()) {
            // The flat conventional path, plus anything the old salas block named for this key.
            List<String> legacy = new ArrayList<>();
            legacy.add(RoomPoolIndex.NAMESPACE + ":" + RoomPoolIndex.ROOT + pisoId + "/" + key);
            for (LegacyVariant variant : legacySalas.getOrDefault(pisoId, Map.of())
                    .getOrDefault(key, List.of())) {
                if (!legacy.contains(variant.template())) {
                    legacy.add(variant.template());
                }
            }
            for (String template : legacy) {
                var id = net.minecraft.resources.ResourceLocation.tryParse(template);
                if (id == null) {
                    continue;
                }
                String flat = id.getPath();
                String name = flat.substring(flat.lastIndexOf('/') + 1);
                // A legacy name of exactly the key is the conventional room and has no identity of
                // its own; everything else keeps the name it was saved under.
                String target = name.equals(key) ? MIGRATED_NAME : name;
                var to = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        id.getNamespace(), RoomPoolIndex.ROOT + pisoId + "/" + key + "/" + target);
                try {
                    Path from = manager.createAndValidatePathToGeneratedStructure(id, ".nbt");
                    if (!Files.exists(from)) {
                        continue;
                    }
                    Path into = manager.createAndValidatePathToGeneratedStructure(to, ".nbt");
                    Files.createDirectories(into.getParent());
                    Files.move(from, into, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    manager.remove(id);
                    manager.remove(to);
                    moved.add(key + "/" + target);
                    double weight = weightOf(pisoId, key, template);
                    if (weight != FloorDef.DEFAULT_WEIGHT) {
                        pesos.computeIfAbsent(key, k -> new LinkedHashMap<>()).put(target, weight);
                    }
                } catch (Exception e) {
                    Teras.LOGGER.error("Dungeons: could not migrate {} -> {}: {}", id, to, e.toString());
                    failed.add(key + " (" + e + ")");
                }
            }
        }

        Path file = pisoFile(pisoId);
        try {
            JsonObject json = Files.exists(file) ? readJson(file) : render(piso);
            json.remove("salas");
            JsonObject block = new JsonObject();
            for (Map.Entry<String, Map<String, Double>> entry : pesos.entrySet()) {
                JsonObject forKey = new JsonObject();
                entry.getValue().forEach(forKey::addProperty);
                block.add(entry.getKey(), forKey);
            }
            json.add("pesos", block);
            if (!json.has("hereda")) {
                json.add("hereda", names(List.of(RoomPoolIndex.DEFAULT_SET)));
            }
            json.addProperty(es.boffmedia.teras.dungeon.build.ConfigVersion.KEY,
                    es.boffmedia.teras.dungeon.build.ConfigVersion.CURRENT);
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(json));
        } catch (Exception e) {
            Teras.LOGGER.error("Dungeons: could not rewrite piso '{}' during migration: {}",
                    pisoId, e.toString());
            return "Plantillas movidas pero no se pudo reescribir el piso: " + e;
        }
        load();
        validateTemplates(manager);
        String report = moved.size() + " plantilla(s) movidas" + (moved.isEmpty() ? "" : ": "
                + String.join(", ", moved));
        return failed.isEmpty() ? "§a" + report : "§e" + report + " · fallaron: "
                + String.join("; ", failed);
    }

    private static double weightOf(String pisoId, String roomKey, String template) {
        for (LegacyVariant variant : legacySalas.getOrDefault(pisoId, Map.of())
                .getOrDefault(roomKey, List.of())) {
            if (variant.template().equals(template)) {
                return variant.weight();
            }
        }
        return FloorDef.DEFAULT_WEIGHT;
    }

    private static Path pisoFile(String pisoId) {
        return FMLPaths.CONFIGDIR.get().resolve("teras").resolve("dungeons")
                .resolve("pisos").resolve(pisoId + ".json");
    }

    private static JsonObject readJson(Path file) throws Exception {
        try (Reader reader = Files.newBufferedReader(file)) {
            return GSON.fromJson(reader, JsonObject.class);
        }
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
                source.musica(), source.ambiente(), MechanicDef.NONE, source.maldiciones(),
                List.of(), List.of(), source.hereda(), source.pesoFormas(), Map.of(),
                EnemyTable.EMPTY, DecorTables.EMPTY);
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
                es.boffmedia.teras.dungeon.build.ConfigVersion.warnIfStale(
                        "pisos/" + id + ".json", versionOf(file),
                        "Run '/teras dungeon piso resync " + id + "' to take the shipped content "
                                + "(authored room variants are preserved).");
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
        warnAboutOutdatedContent(loaded);
    }

    /**
     * Says out loud when a config file on disk is older than the content the mod ships.
     *
     * <p>A shipped default only ever seeds a file that does <b>not</b> exist, so every content
     * change since a server's first boot sits in the jar unread — §22 (`alturaSala`), §23
     * (`formas`) and §26 (the ambient bat) were all this, and the enemy tables are the worst case
     * yet: a piso written before §25 has no {@code enemigos} block at all, so its floors quietly
     * draw from the global {@code enemies.json} stage curve instead of the piso's own bestiary.
     * Nothing looks wrong — a wave still spawns — it is simply not the wave the piso describes,
     * which is how a whole first-party bestiary can be built, shipped and never once seen.</p>
     */
    private static void warnAboutOutdatedContent(Map<String, FloorDef> loaded) {
        for (FloorDef piso : loaded.values()) {
            FloorDef factory = shipped.get(piso.id());
            if (factory == null) {
                continue;
            }
            if (piso.enemigos().isEmpty() && !factory.enemigos().isEmpty()) {
                Teras.LOGGER.error("Dungeons: piso '{}' has no 'enemigos' block, so its floors "
                        + "spawn from the global enemies.json curve and NOT from the piso's own "
                        + "bestiary — its config predates per-piso enemy tables. Run "
                        + "'/teras dungeon piso resync {}' to take the shipped table.",
                        piso.id(), piso.id());
            }
            if (piso.decoracion().isEmpty() && !factory.decoracion().isEmpty()) {
                Teras.LOGGER.warn("Dungeons: piso '{}' has no 'decoracion' block, so its "
                        + "decoracion:* markers build as nothing. '/teras dungeon piso resync {}'",
                        piso.id(), piso.id());
            }
        }
    }

    /** The stamp a config carries, or 0 when it predates stamping. */
    private static int versionOf(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            return json != null && json.has(es.boffmedia.teras.dungeon.build.ConfigVersion.KEY)
                    ? json.get(es.boffmedia.teras.dungeon.build.ConfigVersion.KEY).getAsInt() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static FloorDef readPiso(String id, Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            Map<String, List<LegacyVariant>> legacy = legacySalas(json.get("salas"));
            if (!legacy.isEmpty()) {
                legacySalas.put(id, legacy);
                Teras.LOGGER.warn("Dungeons: piso '{}' still has a 'salas' block. Rooms are read "
                        + "from dungeon/{}/<sala>/ folders now, so it is ignored — run "
                        + "'/teras dungeon piso migrar {}' to move the templates it names into "
                        + "folders and carry its weights over to 'pesos'.", id, id, id);
            }
            return new FloorDef(id,
                    string(json, "nombre", id),
                    string(json, "subtitulo", ""),
                    shapes(json.get("formas")),
                    json.has("luz") ? json.get("luz").getAsInt() : 7,
                    string(json, "musica", ""),
                    string(json, "ambiente", ""),
                    mecanica(json.get("mecanica")),
                    curses(json.get("maldiciones")),
                    strings(json.get("jefes")),
                    strings(json.get("minijefes")),
                    // Absent means the default set; an explicit [] means "share nothing", which is
                    // a different statement and has to survive the round trip.
                    json.has("hereda") ? strings(json.get("hereda")) : null,
                    pesoFormas(json.get("pesosFormas")),
                    pesos(json.get("pesos")),
                    enemyTable(json.get("enemigos")),
                    decorTables(json.get("decoracion")));
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
            List<String> problems = dungeon.problems(pisos, canonicalFloors());
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
            return new DungeonDef(id, string(json, "nombre", id),
                    json.has("primerPiso") ? json.get("primerPiso").getAsInt() : 1, tramos);
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
     * Reads {@code enemigos}. A bad line is skipped with its reason rather than failing the piso:
     * one mistyped id should cost that enemy, not the place.
     */
    private static EnemyTable enemyTable(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return EnemyTable.EMPTY;
        }
        JsonObject json = element.getAsJsonObject();
        int min = json.has("countMin") ? json.get("countMin").getAsInt() : 0;
        int max = json.has("countMax") ? json.get("countMax").getAsInt() : min;
        List<SpawnRef> oleada = new ArrayList<>();
        if (json.has("oleada") && json.get("oleada").isJsonArray()) {
            for (JsonElement item : json.getAsJsonArray("oleada")) {
                SpawnRef ref = spawnRef(item);
                if (ref != null) {
                    oleada.add(ref);
                }
            }
        }
        List<EnemyTable.AmbientRef> ambientales = new ArrayList<>();
        if (json.has("ambientales") && json.get("ambientales").isJsonArray()) {
            for (JsonElement item : json.getAsJsonArray("ambientales")) {
                try {
                    JsonObject obj = item.getAsJsonObject();
                    ambientales.add(new EnemyTable.AmbientRef(
                            obj.has("kind") ? obj.get("kind").getAsString() : null,
                            obj.get("id").getAsString(),
                            obj.has("tab") ? obj.get("tab").getAsInt() : 0,
                            obj.has("cantidad") ? obj.get("cantidad").getAsInt() : 1));
                } catch (Exception e) {
                    Teras.LOGGER.warn("Dungeons: skipping bad ambiental entry {}: {}",
                            item, e.toString());
                }
            }
        }
        return new EnemyTable(min, max, oleada, ambientales);
    }

    private static SpawnRef spawnRef(JsonElement element) {
        try {
            JsonObject obj = element.getAsJsonObject();
            return new SpawnRef(
                    obj.has("kind") ? obj.get("kind").getAsString() : null,
                    obj.get("id").getAsString(),
                    obj.has("tab") ? obj.get("tab").getAsInt() : 0,
                    obj.has("peso") ? obj.get("peso").getAsInt() : 1,
                    obj.has("elite") && obj.get("elite").getAsBoolean(),
                    obj.has("vida") ? obj.get("vida").getAsDouble() : 1.0,
                    obj.has("dano") ? obj.get("dano").getAsDouble() : 1.0,
                    obj.has("escala") ? obj.get("escala").getAsDouble() : 1.0);
        } catch (Exception e) {
            Teras.LOGGER.warn("Dungeons: skipping bad enemy entry {}: {}", element, e.toString());
            return null;
        }
    }

    /** Reads {@code decoracion}: one weighted table per surface. */
    private static DecorTables decorTables(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return DecorTables.EMPTY;
        }
        Map<String, List<DecorTables.DecorRef>> bySurface = new LinkedHashMap<>();
        JsonObject json = element.getAsJsonObject();
        for (String surface : json.keySet()) {
            if (!json.get(surface).isJsonArray()) {
                continue;
            }
            List<DecorTables.DecorRef> refs = new ArrayList<>();
            for (JsonElement item : json.getAsJsonArray(surface)) {
                try {
                    JsonObject obj = item.getAsJsonObject();
                    refs.add(new DecorTables.DecorRef(
                            obj.has("bloque") ? obj.get("bloque").getAsString() : null,
                            obj.has("estructura") ? obj.get("estructura").getAsString() : null,
                            obj.has("peso") ? obj.get("peso").getAsInt() : 1));
                } catch (Exception e) {
                    Teras.LOGGER.warn("Dungeons: skipping bad decoracion entry {}: {}",
                            item, e.toString());
                }
            }
            bySurface.put(surface.trim().toLowerCase(Locale.ROOT), refs);
        }
        return new DecorTables(bySurface);
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

    /**
     * Weight overrides per room key: {@code {"normal": {"geoda": 0.2}}}. Tuning only — a name here
     * that is not in the folder is reported at load and does nothing, because a weight can never
     * add a room.
     */
    private static Map<String, Map<String, Double>> pesos(JsonElement element) {
        Map<String, Map<String, Double>> pesos = new LinkedHashMap<>();
        if (element == null || !element.isJsonObject()) {
            return pesos;
        }
        JsonObject json = element.getAsJsonObject();
        for (String key : json.keySet()) {
            if (!json.get(key).isJsonObject()) {
                Teras.LOGGER.warn("Dungeons: 'pesos.{}' is not an object of "
                        + "variante -> peso; skipped", key);
                continue;
            }
            Map<String, Double> forKey = new LinkedHashMap<>();
            JsonObject obj = json.getAsJsonObject(key);
            for (String name : obj.keySet()) {
                try {
                    forKey.put(name, obj.get(name).getAsDouble());
                } catch (Exception e) {
                    Teras.LOGGER.warn("Dungeons: 'pesos.{}.{}' is not a number; skipped", key, name);
                }
            }
            if (!forKey.isEmpty()) {
                pesos.put(key, forKey);
            }
        }
        return pesos;
    }

    /**
     * The signature mechanic. Two accepted shapes, because the bare string predates params and
     * every existing config on every server still uses it:
     *
     * <pre>
     * "mecanica": "infestacion"
     * "mecanica": { "id": "infestacion", "params": { "retrasoTicks": 120 } }
     * </pre>
     */
    private static MechanicDef mecanica(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return MechanicDef.NONE;
        }
        if (element.isJsonPrimitive()) {
            return MechanicDef.of(element.getAsString());
        }
        if (!element.isJsonObject()) {
            Teras.LOGGER.warn("Dungeons: 'mecanica' is neither a name nor an object; ignored");
            return MechanicDef.NONE;
        }
        JsonObject json = element.getAsJsonObject();
        String id = json.has("id") ? json.get("id").getAsString() : "";
        Map<String, String> params = new LinkedHashMap<>();
        if (json.has("params") && json.get("params").isJsonObject()) {
            JsonObject raw = json.getAsJsonObject("params");
            for (String key : raw.keySet()) {
                try {
                    params.put(key, raw.get(key).getAsString());
                } catch (Exception e) {
                    Teras.LOGGER.warn("Dungeons: 'mecanica.params.{}' is not a value; skipped", key);
                }
            }
        }
        return new MechanicDef(id, params);
    }

    /**
     * Per-piso multipliers over the global shape odds: {@code {"big": 0.3}}. Not a way to forbid a
     * shape — that is what {@code formas} is for — only to make one rarer or commoner than the
     * generator's baseline.
     */
    private static Map<ShapeFamily, Double> pesoFormas(JsonElement element) {
        Map<ShapeFamily, Double> weights = new LinkedHashMap<>();
        if (element == null || !element.isJsonObject()) {
            return weights;
        }
        JsonObject json = element.getAsJsonObject();
        for (String name : json.keySet()) {
            try {
                weights.put(ShapeFamily.valueOf(name.trim().toUpperCase(Locale.ROOT)),
                        json.get(name).getAsDouble());
            } catch (Exception e) {
                Teras.LOGGER.warn("Dungeons: 'pesosFormas.{}' is not a forma and a number; skipped",
                        name);
            }
        }
        return weights;
    }

    /** The retired {@code salas} block, read only so {@code piso migrar} can convert it. */
    private static Map<String, List<LegacyVariant>> legacySalas(JsonElement element) {
        Map<String, List<LegacyVariant>> salas = new LinkedHashMap<>();
        if (element == null || !element.isJsonObject()) {
            return salas;
        }
        JsonObject json = element.getAsJsonObject();
        for (String key : json.keySet()) {
            List<LegacyVariant> variants = new ArrayList<>();
            try {
                for (JsonElement item : json.getAsJsonArray(key)) {
                    JsonObject obj = item.getAsJsonObject();
                    variants.add(new LegacyVariant(obj.get("template").getAsString(),
                            obj.has("peso") ? obj.get("peso").getAsDouble()
                                    : FloorDef.DEFAULT_WEIGHT));
                }
            } catch (Exception e) {
                Teras.LOGGER.warn("Dungeons: could not read the legacy 'salas.{}': {}", key, e.toString());
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
        legacySalas = new LinkedHashMap<>();
        pisos.put("cuevas", new FloorDef("cuevas", "Cuevas", "el aire huele a piedra húmeda",
                EnumSet.allOf(ShapeFamily.class), 7,
                "minecraft:music.overworld.dripstone_caves", "minecraft:ambient.cave", "",
                // Its own set-pieces, both drawn from the floor's own bestiary. The boss is the
                // slime the floor has been showing you all along, and the mini-boss is the one
                // enemy on it that has a rule — so both fights are about something already learned.
                EnumSet.of(Curse.LABYRINTH, Curse.LOST, Curse.PLOMO),
                List.of("gran_limo"), List.of("cristalero_mayor"),
                cuevasEnemies(), cuevasDecor()));
        // Every family, but not at every piso's odds. This used to be two families, on the argument
        // that "tight and choked is the identity" and that it excused six rooms of authoring. The
        // second half stopped being true when infest() started deriving Infestadas from Cuevas —
        // its rooms cost nothing to add. The first half was real but the instrument was wrong:
        // forbidding BIG also deletes the room, so a 2x2 chamber could never be a shock because it
        // could never happen. It declares all four now and weights the big shapes down instead, so
        // the floor still reads as choked and a wide chamber lands as an event. LABYRINTH comes
        // with it: the reason for refusing it was the two-shape sprawl, and that reason is gone.
        pisos.put("cuevas_infestadas", new FloorDef("cuevas_infestadas", "Cuevas Infestadas",
                "algo se mueve en la oscuridad",
                EnumSet.allOf(ShapeFamily.class), 4,
                "minecraft:music.overworld.dripstone_caves", "minecraft:ambient.cave",
                new MechanicDef("infestacion", Map.of()),
                // Its own mini-boss, declared rather than inherited: the tramo's is a bone humanoid,
                // and the fallback that now promotes a piso's own elite would give the floor a
                // second tejedora — correct by construction, and still one of the two fights the
                // floor already has.
                EnumSet.of(Curse.LABYRINTH, Curse.LOST, Curse.PLOMO),
                List.of("reina_madre"), List.of("cazadora"),
                Map.of(ShapeFamily.LARGE, 0.7, ShapeFamily.L, 0.6, ShapeFamily.BIG, 0.3),
                infestadasEnemies(), infestadasDecor()));

        declared = pisos;
        // A snapshot taken before loadPisos overwrites pisos/declared with the disk versions, so it
        // holds the factory content even after a server has its own files. FloorDef is immutable, so
        // sharing the instances is safe.
        shipped = new LinkedHashMap<>(pisos);
        dungeons = new LinkedHashMap<>();
        dungeons.put("cripta", new DungeonDef("cripta", "La Cripta", 1, List.of(
                new TierDef(2, 1.0,
                        List.of(new WeightedRef("cuevas", 3),
                                new WeightedRef("cuevas_infestadas", 1)),
                        // The tramo's own pools are floor-1 defaults now, not crypt wardens. Both
                        // pisos declare their own, so these are only what a *future* member of this
                        // pool inherits if it says nothing — and what it should inherit is the
                        // stage's register, which at tramo 1 is a cave. `coloso_guardian` and
                        // `centinela_hueso` stay registered for the bone-crypt pool at tramo 2.
                        List.of("gran_limo"), List.of("cristalero_mayor")))));
    }

    /**
     * Rewrites {@code pisos/<id>.json} back to the mod's factory content — enemy table, decoration,
     * shapes, light, everything. This exists because a shipped-default change never reaches a config
     * file that already exists on disk: the defaults only seed a file that is absent, so a server
     * created before a content change keeps the old content until someone rewrites it. This is the
     * one-command rewrite.
     *
     * <p><b>Authored room variants are preserved.</b> A piso's {@code salas} are managed by
     * {@code sala guardar} and represent real building work; only the shipped content fields reset.
     * A piso with no factory default (one made with {@code piso crear}) is refused — there is
     * nothing to resync it to.</p>
     *
     * @return an error to show, or null on success
     */
    public static String resyncPiso(String pisoId) {
        FloorDef def = shipped.get(pisoId);
        if (def == null) {
            return "'" + pisoId + "' no es un piso de fábrica — resync solo aplica a los que trae el "
                    + "mod (" + String.join(", ", shipped.keySet()) + ").";
        }
        // Keep what the operator chose about rooms — which sets this piso shares and how it has
        // weighted them — so a content resync never undoes tuning. It could not discard authored
        // rooms even if it tried: those are files in a folder, and nothing in the config names them.
        FloorDef current = declared.get(pisoId);
        FloorDef merged = new FloorDef(def.id(), def.nombre(), def.subtitulo(), def.formas(),
                def.luz(), def.musica(), def.ambiente(), def.mecanica(), def.maldiciones(),
                def.jefes(), def.minijefes(),
                current != null ? current.hereda() : def.hereda(),
                current != null && !current.pesoFormas().isEmpty()
                        ? current.pesoFormas() : def.pesoFormas(),
                current != null ? current.pesos() : def.pesos(),
                def.enemigos(), def.decoracion());
        Path file = FMLPaths.CONFIGDIR.get().resolve("teras").resolve("dungeons")
                .resolve("pisos").resolve(pisoId + ".json");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(render(merged)));
            return null;
        } catch (Exception e) {
            Teras.LOGGER.error("Dungeons: could not resync piso '{}': {}", pisoId, e.toString());
            return "No se pudo escribir el piso: " + e;
        }
    }

    /** The tab {@code enemigos instalar} writes the CNPC bestiary into ({@code DungeonEnemyPacks.TAB}). */
    private static final int CNPC_TAB = 7;

    /**
     * Cuevas' roster: <b>one enemy per verb, and nothing else.</b>
     *
     * <p>This is the first fight anyone has with the system, so the floor's job is to teach what
     * the verbs are — things walk at you, things shoot from the ledges, things come in numbers,
     * things bounce, and one thing must not be hit. Five lessons, each answerable with a sword and
     * your feet, and every wave a combination of things already understood.</p>
     *
     * <p>{@code husk_guardian} and {@code bone_sentinel} used to be the elites here. They are the
     * bone-crypt's bestiary — see the Catacumbas/Osario pool in {@code DUNGEONS_TRAMOS_DRAFT.md} —
     * sitting one stage too early, and with them the floor's roster was two thirds humanoid and
     * two thirds borrowed. They stay registered, because that is what tramo 2 is built from.</p>
     *
     * <p>The humanoids that remain are the smugglers, not raiders in general: La Guarida promotes
     * this roster to a floor of its own later, so spending human variety here spends that floor's
     * identity. Two is the slice Cuevas needs.</p>
     *
     * <p>Every id here is a {@code GeoEnemyVariant} — never a {@code minecraft:} id, since on a
     * Pixelmon server a real vanilla monster is deleted the instant it spawns. Weights are relative
     * composition only; the tramo's dificultad supplies the depth.</p>
     *
     * <p>The ambient entry is a bat, and it took a first-party flyer to get one. The last attempt
     * was a CustomNPCs clone, which uses NPC navigation and therefore <i>walked</i> — a bat on foot
     * read worse than no bat at all, and the entry was pulled. {@code FLYER} now has the half of
     * its implementation it was missing, so the mechanism finally has a mob that suits it.</p>
     */
    private static EnemyTable cuevasEnemies() {
        return new EnemyTable(3, 5,
                List.of(// The smugglers: the people who got here first, and the roster La Guarida
                        // will one day promote to a floor of its own.
                        SpawnRef.of("saqueador_cuevas", 4),
                        // Three shooters, one per projectile shape (CONTENIDO §3.1) — the arc, the
                        // line and the lob. Their combined weight is still small: perches should be
                        // a threat to answer, not the shape of every fight. What the trio buys is
                        // that "things shoot" stops being one lesson and becomes three answers, and
                        // that the ranged class debuts as something the floor does to you a tramo
                        // before you are handed it.
                        SpawnRef.of("arquero_gruta", 2),
                        SpawnRef.of("ballestero_gruta", 1),
                        // The dog closes a gap the party opened on purpose, which is what keeps
                        // backing off from being the answer to everything else on this floor.
                        SpawnRef.of("mastin", 2),
                        // Kill-me-first, and the only threat here that is a clock. Rare: two
                        // lookouts is two clocks, and a wave nobody can read.
                        SpawnRef.of("vigia", 1),
                        // Does not fight. Leaves, with the money.
                        SpawnRef.of("carronero", 1),
                        // The cave's own. The beetle teaches the rule where getting it wrong is
                        // free; the Cristalero Mayor is where it costs — the gólem used to be the
                        // second half of that sentence and is retired game-wide (CONTENIDO §0).
                        SpawnRef.of("escarabajo", 2),
                        SpawnRef.of("cristal_rastrero", 2),
                        // The lob, and the crystal family's walking member. Scarce: two of these in
                        // one room is a floor of overlapping ground-marks, which is a different
                        // game rather than a harder one.
                        SpawnRef.of("cristalero", 1),
                        // Slow, weak, and worse dead than alive.
                        SpawnRef.of("hongo_bombardero", 1),
                        // It never moves, so its weight is not a share of the threat — it is how
                        // often a room has one already sitting in it.
                        SpawnRef.of("musgo_agarrador", 1),
                        // First-party now, not CustomNPCs clones — see GeoEnemyVariant. As clones
                        // these could not be sized or made to bounce; as geo variants they are both,
                        // and they need no `enemigos instalar` to exist at all.
                        SpawnRef.of("lepisma_cueva", 3),
                        SpawnRef.of("limo_cueva", 2),
                        // The slime appears at three sizes across the floor — chaff, elite, and the
                        // boss it splits back into. That is the one repetition worth having: by the
                        // time the boss lands, a player has already been taught what it is and how
                        // it moves, so the fight can be about scale instead of about explanation.
                        SpawnRef.of("limo_mayor", 1).asElite()),
                // Atmosphere, and the first thing in Cuevas that lives above head height. Outside
                // the kill ledger on purpose: a bat inside it seals the room until the party has
                // hunted down every one, which is the fight nobody wants to have.
                List.of(new EnemyTable.AmbientRef(null, "murcielago", 0, 2)));
    }

    /**
     * Infestadas fights the same floor plan with a different bestiary — which is the whole point of
     * a variant piso. No archer: its shooters climb instead, so height is contested rather than
     * held.
     */
    private static EnemyTable infestadasEnemies() {
        return new EnemyTable(4, 6,
                List.of(SpawnRef.of("cria", 5),
                        SpawnRef.of("tejedora", 2).asElite(),
                        SpawnRef.of("lepisma_cueva", 2)),
                List.of());
    }

    /** Carved natural cavern: dripstone above, mushrooms and rubble below, moss and lichen across. */
    private static DecorTables cuevasDecor() {
        return new DecorTables(Map.of(
                DecorTables.TECHO, List.of(
                        DecorTables.DecorRef.block("minecraft:pointed_dripstone[vertical_direction=down]", 3),
                        DecorTables.DecorRef.block("minecraft:hanging_roots", 2),
                        DecorTables.DecorRef.block("minecraft:cave_vines", 1)),
                DecorTables.SUELO, List.of(
                        DecorTables.DecorRef.block("minecraft:brown_mushroom", 2),
                        DecorTables.DecorRef.block("minecraft:cobblestone", 2),
                        DecorTables.DecorRef.block("minecraft:pointed_dripstone[vertical_direction=up]", 1)),
                DecorTables.PARED, List.of(
                        DecorTables.DecorRef.block("minecraft:moss_carpet", 2),
                        DecorTables.DecorRef.block("minecraft:glow_lichen", 2),
                        DecorTables.DecorRef.block("minecraft:coal_ore", 1))));
    }

    /** The same surfaces, dressed: webbing above, sculk below, a damper palette throughout. */
    private static DecorTables infestadasDecor() {
        return new DecorTables(Map.of(
                DecorTables.TECHO, List.of(
                        DecorTables.DecorRef.block("minecraft:cobweb", 4),
                        DecorTables.DecorRef.block("minecraft:hanging_roots", 1)),
                DecorTables.SUELO, List.of(
                        DecorTables.DecorRef.block("minecraft:sculk", 3),
                        DecorTables.DecorRef.block("minecraft:cobweb", 2),
                        DecorTables.DecorRef.block("minecraft:bone_block", 1)),
                DecorTables.PARED, List.of(
                        DecorTables.DecorRef.block("minecraft:sculk_vein", 3),
                        DecorTables.DecorRef.block("minecraft:cobweb", 2))));
    }

    private static JsonObject render(FloorDef piso) {
        JsonObject json = new JsonObject();
        json.addProperty(es.boffmedia.teras.dungeon.build.ConfigVersion.KEY,
                es.boffmedia.teras.dungeon.build.ConfigVersion.CURRENT);
        json.addProperty("nombre", piso.nombre());
        json.addProperty("subtitulo", piso.subtitulo());
        json.add("formas", names(piso.formas().stream().map(Enum::name).toList()));
        json.addProperty("luz", piso.luz());
        json.addProperty("musica", piso.musica());
        json.addProperty("ambiente", piso.ambiente());
        if (piso.mecanica().isNone()) {
            json.addProperty("mecanica", "");
        } else if (piso.mecanica().params().isEmpty()) {
            // The bare-string form still round-trips, so a piso that tunes nothing keeps the file
            // it has always had rather than growing an empty object.
            json.addProperty("mecanica", piso.mecanica().id());
        } else {
            JsonObject mecanica = new JsonObject();
            mecanica.addProperty("id", piso.mecanica().id());
            JsonObject params = new JsonObject();
            piso.mecanica().params().forEach(params::addProperty);
            mecanica.add("params", params);
            json.add("mecanica", mecanica);
        }
        json.add("maldiciones", names(piso.maldiciones().stream().map(Enum::name).toList()));
        json.add("jefes", names(piso.jefes()));
        json.add("minijefes", names(piso.minijefes()));
        json.add("hereda", names(piso.hereda()));
        JsonObject pesosFormas = new JsonObject();
        for (Map.Entry<ShapeFamily, Double> entry : piso.pesoFormas().entrySet()) {
            pesosFormas.addProperty(entry.getKey().name().toLowerCase(Locale.ROOT), entry.getValue());
        }
        json.add("pesosFormas", pesosFormas);
        JsonObject pesos = new JsonObject();
        for (Map.Entry<String, Map<String, Double>> entry : piso.pesos().entrySet()) {
            JsonObject forKey = new JsonObject();
            entry.getValue().forEach(forKey::addProperty);
            pesos.add(entry.getKey(), forKey);
        }
        // Written even when empty, because an empty block is the honest description of the normal
        // case: every room in the folder, all at the same odds.
        json.add("pesos", pesos);
        json.add("enemigos", renderEnemies(piso.enemigos()));
        json.add("decoracion", renderDecor(piso.decoracion()));
        return json;
    }

    private static JsonObject renderEnemies(EnemyTable table) {
        JsonObject json = new JsonObject();
        json.addProperty("countMin", table.countMin());
        json.addProperty("countMax", table.countMax());
        JsonArray oleada = new JsonArray();
        for (SpawnRef ref : table.oleada()) {
            JsonObject obj = new JsonObject();
            if (ref.kind() != null) {
                obj.addProperty("kind", ref.kind());
            }
            obj.addProperty("id", ref.id());
            if (ref.tab() != 0) {
                obj.addProperty("tab", ref.tab());
            }
            obj.addProperty("peso", ref.peso());
            if (ref.elite()) {
                obj.addProperty("elite", true);
            }
            // Only written when they say something: a table full of "vida": 1.0 reads as tuning
            // that was done, and invites editing the wrong number.
            if (ref.vida() != 1.0) {
                obj.addProperty("vida", ref.vida());
            }
            if (ref.dano() != 1.0) {
                obj.addProperty("dano", ref.dano());
            }
            if (ref.escala() != 1.0) {
                obj.addProperty("escala", ref.escala());
            }
            oleada.add(obj);
        }
        json.add("oleada", oleada);
        JsonArray ambientales = new JsonArray();
        for (EnemyTable.AmbientRef ref : table.ambientales()) {
            JsonObject obj = new JsonObject();
            if (ref.kind() != null) {
                obj.addProperty("kind", ref.kind());
            }
            obj.addProperty("id", ref.id());
            if (ref.tab() != 0) {
                obj.addProperty("tab", ref.tab());
            }
            obj.addProperty("cantidad", ref.cantidad());
            ambientales.add(obj);
        }
        json.add("ambientales", ambientales);
        return json;
    }

    private static JsonObject renderDecor(DecorTables tables) {
        JsonObject json = new JsonObject();
        for (Map.Entry<String, List<DecorTables.DecorRef>> entry : tables.bySurface().entrySet()) {
            JsonArray list = new JsonArray();
            for (DecorTables.DecorRef ref : entry.getValue()) {
                JsonObject obj = new JsonObject();
                if (ref.isStructure()) {
                    obj.addProperty("estructura", ref.estructura());
                } else {
                    obj.addProperty("bloque", ref.bloque());
                }
                obj.addProperty("peso", ref.peso());
                list.add(obj);
            }
            json.add(entry.getKey(), list);
        }
        return json;
    }

    private static JsonObject renderDungeons() {
        JsonObject root = new JsonObject();
        for (DungeonDef dungeon : dungeons.values()) {
            JsonObject json = new JsonObject();
            json.addProperty("nombre", dungeon.nombre());
            json.addProperty("primerPiso", dungeon.primerPiso());
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
