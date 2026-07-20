package es.boffmedia.teras.dungeon.gear;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gear catalog against the files that have to agree with it. Gear is authored in three places
 * at once — a definition in {@link GearDefs}, an entry in a loot table, and a model/texture/lang
 * triple in the resources — and only the definition is compiled. A typo in either of the others
 * produces a piece that silently never drops, or drops as a missing texture: exactly the class of
 * bug that survives until someone plays a run.
 */
class GearDefsTest {

    private static final List<String> LOOT_TABLES =
            List.of("treasure", "curse", "devil", "boss");

    @Test
    void catalogIsComplete() {
        Map<String, GearDef> defs = GearDefs.defaults();
        assertEquals(12, defs.size(), "the shipped set is twelve pieces");

        for (Map.Entry<String, GearDef> entry : defs.entrySet()) {
            GearDef def = entry.getValue();
            assertEquals(entry.getKey(), def.id(), "map key and id must agree");
            assertNotNull(def.kind(), def.id() + " has no slot");
            assertNotNull(def.rarity(), def.id() + " has no rarity");
            assertNotNull(def.ability(), def.id() + " has no ability");
            assertNotNull(def.stats(), def.id() + " has no stat list");
            assertFalse(def.effectiveSkinType().isBlank(), def.id() + " has no skin type");
        }
    }

    @Test
    void everyAbilityCarriesItsNumber() {
        for (GearDef def : GearDefs.defaults().values()) {
            if (def.ability() != GearAbility.NINGUNA) {
                assertTrue(def.magnitude() > 0,
                        def.id() + " has ability " + def.ability() + " but no magnitude");
            }
        }
    }

    /** A piece no table lists is a piece no player can ever hold. */
    @Test
    void everyPieceDropsSomewhere() {
        Set<String> dropped = new HashSet<>();
        for (String table : LOOT_TABLES) {
            dropped.addAll(terasItemsIn(table));
        }
        Set<String> missing = new TreeSet<>(GearDefs.defaults().keySet());
        missing.removeAll(dropped);
        assertTrue(missing.isEmpty(), "gear that drops from no loot table: " + missing);
    }

    /** And a table naming gear that does not exist is a loot roll that fails at runtime. */
    @Test
    void everyGearLootEntryIsReal() {
        for (String table : LOOT_TABLES) {
            for (String id : terasItemsIn(table)) {
                boolean known = GearDefs.defaults().containsKey(id) || NON_GEAR_ITEMS.contains(id);
                assertTrue(known,
                        table + ".json drops 'teras:" + id + "', which is neither gear nor a "
                                + "known dungeon item");
            }
        }
    }

    /** The non-gear Teras items the dungeon tables are allowed to name. */
    private static final Set<String> NON_GEAR_ITEMS = Set.of(
            "moneda_mazmorra", "carga_rompemuros", "pocion_vital", "pocion_vital_mayor");

    @Test
    void everyPieceHasItsAssets() {
        for (GearDef def : GearDefs.defaults().values()) {
            // Gear renders as its base item (plus the AW skin) and must NOT ship a model or
            // texture — dead assets are how sprite drift starts.
            assertFalse(resource("assets/teras/models/item/" + def.id() + ".json") != null,
                    def.id() + " ships a model it cannot use");
            assertFalse(resource("assets/teras/textures/item/" + def.id() + ".png") != null,
                    def.id() + " ships a texture it cannot use");
        }
        for (String lang : List.of("es_es", "en_us")) {
            JsonObject json = readJson("assets/teras/lang/" + lang + ".json").getAsJsonObject();
            for (String id : GearDefs.defaults().keySet()) {
                assertTrue(json.has("item.teras." + id), id + " is not named in " + lang);
            }
            for (GearAbility ability : GearAbility.values()) {
                if (ability != GearAbility.NINGUNA) {
                    String key = "gear.teras." + ability.name().toLowerCase(Locale.ROOT);
                    assertTrue(json.has(key), key + " is missing from " + lang);
                }
            }
        }
    }

    // ── gear.json merge ──────────────────────────────────────────────────────

    @Test
    void mergeOverridesOnlyWhatTheFileNames() {
        JsonObject root = new JsonObject();
        JsonObject entry = new JsonObject();
        entry.addProperty("magnitud", 0.5);
        JsonObject stats = new JsonObject();
        stats.addProperty("attack_damage", 99.0);
        entry.add("stats", stats);
        root.add("espada_abisal", entry);

        GearDef merged = GearDefs.merge(root).defs().get("espada_abisal");
        GearDef base = GearDefs.defaults().get("espada_abisal");

        assertEquals(0.5, merged.magnitude(), "magnitude comes from the file");
        assertEquals(99.0, statOf(merged, GearStat.ATTACK_DAMAGE), "stat comes from the file");
        assertEquals(statOf(base, GearStat.ATTACK_SPEED), statOf(merged, GearStat.ATTACK_SPEED),
                "a stat the file omits keeps its built-in value");
        assertEquals(base.ability(), merged.ability(),
                "an ability the file omits keeps its built-in value");
    }

    /**
     * The ability is overridable, not just its magnitude.
     *
     * <p>While it was not, a piece's effect was fixed in Java and any ability the catalog did not
     * happen to use was unreachable however the file was written — {@code QUEMAZON} was implemented,
     * translated and tested, and could never fire on anything. Retuning also stopped at "how much"
     * without ever reaching "of what".</p>
     */
    @Test
    void theFileCanChangeAnAbility() {
        JsonObject root = new JsonObject();
        JsonObject entry = new JsonObject();
        entry.addProperty("habilidad", "quemazon");
        entry.addProperty("magnitud", 4.0);
        root.add("espada_abisal", entry);

        GearDef merged = GearDefs.merge(root).defs().get("espada_abisal");

        assertEquals(GearAbility.QUEMAZON, merged.ability(), "the file names the ability");
        assertEquals(4.0, merged.magnitude());
        assertEquals(GearDefs.defaults().get("espada_abisal").stats(), merged.stats(),
                "changing the ability leaves the stat line alone");
    }

    /** Every ability is reachable from the file — that is what makes the enum the whole vocabulary. */
    @Test
    void everyAbilityCanBeNamedInTheFile() {
        for (GearAbility ability : GearAbility.values()) {
            JsonObject root = new JsonObject();
            JsonObject entry = new JsonObject();
            entry.addProperty("habilidad", ability.name());
            root.add("espada_abisal", entry);

            GearDefs.Merge merge = GearDefs.merge(root);

            assertTrue(merge.warnings().isEmpty(), ability + " warned: " + merge.warnings());
            assertEquals(ability, merge.defs().get("espada_abisal").ability());
        }
    }

    /** A typo keeps the built-in ability rather than silently disarming the piece. */
    @Test
    void anUnknownAbilityWarnsAndKeepsTheBuiltIn() {
        JsonObject root = new JsonObject();
        JsonObject entry = new JsonObject();
        entry.addProperty("habilidad", "no_existe");
        root.add("espada_abisal", entry);

        GearDefs.Merge merge = GearDefs.merge(root);

        assertEquals(1, merge.warnings().size(), merge.warnings().toString());
        assertEquals(GearDefs.defaults().get("espada_abisal").ability(),
                merge.defs().get("espada_abisal").ability());
    }

    /** The rendered file shows the ability, or nobody discovers they can change it. */
    @Test
    void theDefaultFileNamesEveryPiecesAbility() {
        JsonObject rendered = GearDefs.renderDefaults();
        for (GearDef def : GearDefs.defaults().values()) {
            assertEquals(def.ability().name(),
                    rendered.getAsJsonObject(def.id()).get("habilidad").getAsString(), def.id());
        }
    }

    @Test
    void mergeKeepsDefaultsWhenTheFileIsAbsentOrEmpty() {
        assertEquals(GearDefs.defaults().size(), GearDefs.merge(null).defs().size());
        assertEquals(GearDefs.defaults(), GearDefs.merge(new JsonObject()).defs());
    }

    @Test
    void mergeWarnsInsteadOfFailingOnBadEntries() {
        JsonObject root = new JsonObject();
        root.add("no_existe", new JsonObject());
        JsonObject bad = new JsonObject();
        JsonObject stats = new JsonObject();
        stats.addProperty("no_es_un_stat", 5.0);
        bad.add("stats", stats);
        root.add("espada_abisal", bad);

        GearDefs.Merge merge = GearDefs.merge(root);

        assertEquals(GearDefs.defaults().size(), merge.defs().size(),
                "an unknown id adds nothing to the catalog");
        assertEquals(2, merge.warnings().size(), "both problems are reported: " + merge.warnings());
        assertEquals(GearDefs.defaults().get("espada_abisal"), merge.defs().get("espada_abisal"),
                "an unknown stat leaves the piece untouched");
    }

    @Test
    void skinIsBlankUntilAuthoredAndOverridable() {
        for (GearDef def : GearDefs.defaults().values()) {
            assertFalse(def.hasSkin(), def.id() + " ships with a skin id that cannot exist yet");
        }
        JsonObject root = new JsonObject();
        JsonObject entry = new JsonObject();
        entry.addProperty("skin", "ws:/espada.armour");
        root.add("espada_abisal", entry);

        GearDef merged = GearDefs.merge(root).defs().get("espada_abisal");
        assertTrue(merged.hasSkin());
        assertEquals("sword", merged.effectiveSkinType(),
                "the kind's default must be a name AW actually registers");
    }

    // ── skin identifier / type normalization ────────────────────────────────
    //
    // AW resolves a server-library skin as `ws:` + path relative to skin-library/, leading slash
    // and .armour extension included, and skin types by bare lowercase registry name. None of that
    // is guessable from the config file, so the merge has to meet admins halfway. Pinned against
    // the shapes read out of AW 3.4.0-beta.3's bytecode (SkinLibraryFile, SkinTypes).

    @Test
    void skinIdsAreNormalizedToWhatAwResolves() {
        List<String> warnings = new java.util.ArrayList<>();
        assertEquals("ws:/29467.armour",
                GearDefs.normalizeSkinId("ws:29467", "x", warnings));
        assertEquals("ws:/docs/29467 - lord knight-greatsword.armour",
                GearDefs.normalizeSkinId("docs/29467 - lord knight-greatsword.armour", "x", warnings));
        assertEquals("ws:/docs/espada.armour",
                GearDefs.normalizeSkinId("ws:/docs/espada.armour", "x", warnings),
                "an already-exact id passes through untouched");
        assertEquals("db:12345", GearDefs.normalizeSkinId("db:12345", "x", warnings),
                "database ids are not paths and are left alone");
        assertTrue(warnings.isEmpty(), "none of those deserve a warning: " + warnings);

        GearDefs.normalizeSkinId("xx:whatever", "espada_abisal", warnings);
        assertEquals(1, warnings.size(), "an unknown domain is called out");
    }

    @Test
    void skinTypesAcceptTheNamesPeopleWrite() {
        assertEquals("sword", GearDefs.canonicalSkinType("item_sword"),
                "the name our own generated gear.json used to ship");
        assertEquals("chest", GearDefs.canonicalSkinType("ARMOR_CHEST"));
        assertEquals("head", GearDefs.canonicalSkinType("helmet"));
        assertEquals("wings", GearDefs.canonicalSkinType("wings"),
                "valid AW names we do not alias pass through");
        assertEquals("", GearDefs.canonicalSkinType(""), "blank keeps meaning kind-default");
    }

    /** The full path: a stale generated file with old type names must still come out right. */
    @Test
    void mergeRepairsAStaleGeneratedConfig() {
        JsonObject root = new JsonObject();
        JsonObject entry = new JsonObject();
        entry.addProperty("skin", "ws:29467");
        entry.addProperty("skinType", "item_sword");
        root.add("espada_abisal", entry);

        GearDef merged = GearDefs.merge(root).defs().get("espada_abisal");
        assertEquals("ws:/29467.armour", merged.skinId());
        assertEquals("sword", merged.effectiveSkinType());
    }

    /**
     * Gear registers no items: every piece is a vanilla base plus a {@code teras:gear_id}
     * component. The base id has to be well-formed (it goes through {@code ResourceLocation.parse}
     * at give time), must not be a {@code teras:} id, and must not be one of the vanilla items
     * whose coded behaviour would leak into gear — a mace base smash-attacks, a totem base saves
     * from death, an elytra base flies.
     */
    @Test
    void everyPieceRidesAWellFormedVanillaBase() {
        Set<String> behaviorful = Set.of("minecraft:mace", "minecraft:totem_of_undying",
                "minecraft:elytra", "minecraft:shield", "minecraft:trident", "minecraft:crossbow",
                "minecraft:bow");
        for (GearDef def : GearDefs.defaults().values()) {
            assertTrue(def.hasBaseItem(), def.id() + " has no base item");
            assertTrue(def.baseItem().matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"),
                    def.id() + " has a malformed base item id: " + def.baseItem());
            assertFalse(def.baseItem().startsWith("teras:"),
                    def.id() + " cannot use a teras: item as a base — gear registers no items");
            assertFalse(behaviorful.contains(def.baseItem()),
                    def.id() + " rides " + def.baseItem() + ", whose coded behaviour would leak");
        }
        assertEquals("minecraft:diamond_sword",
                GearDefs.defaults().get("espada_abisal").baseItem());
    }

    /**
     * The stamp that reaches gear already in the world. Every load has to move the generation, or
     * a retune never re-cuts existing pieces and the client keeps rendering the old numbers —
     * which is exactly how the first version of this shipped.
     */
    @Test
    void everyLoadMovesTheGeneration() {
        int before = GearDefs.generation();
        GearDefs.replaceAll(GearDefs.defaults());
        assertNotEquals(before, GearDefs.generation(), "a load must invalidate stamped gear");

        int after = GearDefs.generation();
        GearDefs.replaceAll(GearDefs.merge(new JsonObject()).defs());
        assertNotEquals(after, GearDefs.generation(), "even a no-op load re-cuts, cheaply");
    }

    private static double statOf(GearDef def, GearStat stat) {
        return def.stats().stream()
                .filter(s -> s.stat() == stat)
                .findFirst()
                .orElseThrow()
                .amount();
    }

    /**
     * Every Teras item one dungeon loot table can produce, minus the namespace. Gear appears two
     * ways: a registered {@code teras:} item named directly, or a vanilla item tagged with a
     * {@code teras:gear_id} component via {@code minecraft:set_components} — the sprite-less shape.
     */
    private static Set<String> terasItemsIn(String table) {
        JsonObject root = readJson("data/teras/loot_table/dungeon/" + table + ".json")
                .getAsJsonObject();
        Set<String> ids = new HashSet<>();
        for (JsonElement pool : root.getAsJsonArray("pools")) {
            JsonArray entries = pool.getAsJsonObject().getAsJsonArray("entries");
            for (JsonElement entry : entries) {
                JsonObject obj = entry.getAsJsonObject();
                if (obj.has("name") && obj.get("name").getAsString().startsWith("teras:")) {
                    ids.add(obj.get("name").getAsString().substring("teras:".length()));
                }
                if (!obj.has("functions")) {
                    continue;
                }
                for (JsonElement fn : obj.getAsJsonArray("functions")) {
                    JsonObject fnObj = fn.getAsJsonObject();
                    if (fnObj.get("function").getAsString().endsWith("set_components")
                            && fnObj.getAsJsonObject("components").has("teras:gear_id")) {
                        ids.add(fnObj.getAsJsonObject("components").get("teras:gear_id").getAsString());
                    }
                }
            }
        }
        return ids;
    }

    private static InputStream resource(String path) {
        return GearDefsTest.class.getClassLoader().getResourceAsStream(path);
    }

    private static JsonElement readJson(String path) {
        InputStream in = resource(path);
        assertNotNull(in, "missing resource " + path);
        return JsonParser.parseReader(new InputStreamReader(in));
    }
}
