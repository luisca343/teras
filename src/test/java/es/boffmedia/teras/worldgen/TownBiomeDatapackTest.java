package es.boffmedia.teras.worldgen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the town biome datapack, which nothing else can check: these are resources, so a typo or a
 * rename compiles fine and only fails when a world loads.
 *
 * <p>The <b>ids are a wire contract</b>. Pixelmon spawn sets select on biome id, and the towns were
 * painted onto the live map with those ids already baked in, so renaming a file silently empties that
 * town's spawn table. The list below is therefore the 1.16.5 {@code ModBiomes} registry verbatim.</p>
 */
class TownBiomeDatapackTest {

    /** The 25 ids registered by 1.16.5 {@code ModBiomes}, in declaration order. */
    private static final List<String> TOWNS = List.of(
            "arrecife_wingull", "puerto_wingull", "pueblo_tulipan", "pueblo_shiroi", "pueblo_hagane",
            "pueblo_yume", "pueblo_dento", "pueblo_iwa", "pueblo_tsuchi", "pueblo_oasis",
            "pueblo_senshi", "pueblo_kinoko", "pueblo_sakura", "pueblo_takai", "pueblo_doku",
            "pueblo_gaku", "pueblo_lavanda", "pueblo_denki", "pueblo_mizu", "pueblo_olivo",
            "narukami", "akina", "fukitsu", "gansolia", "acento_circunflejo");

    /** 1.21.1's biome codec rejects a file missing any of these. */
    private static final Set<String> REQUIRED = Set.of(
            "carvers", "downfall", "effects", "features", "has_precipitation",
            "spawn_costs", "spawners", "temperature");

    private static JsonObject read(String path) {
        try (InputStream in = TownBiomeDatapackTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing datapack resource: " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    private static JsonObject biome(String id) {
        return read("/data/teras/worldgen/biome/" + id + ".json");
    }

    @Test
    void everyTownBiomeExistsAndSatisfiesTheCodec() {
        for (String id : TOWNS) {
            JsonObject b = biome(id);
            assertEquals(REQUIRED, b.keySet(), id + ": unexpected top-level keys");

            JsonObject effects = b.getAsJsonObject("effects");
            for (String colour : List.of("fog_color", "sky_color", "water_color", "water_fog_color")) {
                assertTrue(effects.has(colour), id + ": effects missing " + colour);
            }

            // One list per GenerationStep.Decoration. These biomes are painted onto finished terrain,
            // so every step is empty — a populated one would mean features generating inside a town.
            assertEquals(11, b.getAsJsonArray("features").size(), id + ": wrong feature step count");
            b.getAsJsonArray("features").forEach(step ->
                    assertTrue(step.getAsJsonArray().isEmpty(), id + ": features must stay empty"));

            b.getAsJsonObject("spawners").asMap().forEach((category, mobs) ->
                    assertTrue(mobs.getAsJsonArray().isEmpty(),
                            id + ": vanilla mobs would spawn in category " + category));
        }
    }

    /**
     * 1.16.5 set {@code RainType.SNOW} independently of temperature, giving Shiroi snowfall that never
     * froze anything. 1.21 derives precipitation from temperature, so the cold value is the only way
     * the town still snows — and it is load-bearing, not incidental.
     */
    @Test
    void shiroiIsColdEnoughToSnow() {
        JsonObject shiroi = biome("pueblo_shiroi");
        assertTrue(shiroi.get("temperature").getAsFloat() < 0.15f,
                "pueblo_shiroi must stay below the snow threshold");
        assertTrue(shiroi.get("has_precipitation").getAsBoolean(),
                "pueblo_shiroi needs precipitation for the snow to fall");
    }

    @Test
    void everyOtherTownStaysWarmEnoughToRain() {
        for (String id : TOWNS) {
            if (id.equals("pueblo_shiroi")) {
                continue;
            }
            assertTrue(biome(id).get("temperature").getAsFloat() >= 0.15f,
                    id + ": would freeze water and snow over, which only Shiroi should do");
        }
    }

    /**
     * The void dimension is three files that reference each other by id; a mismatch is only caught when
     * the dimension fails to load.
     */
    @Test
    void voidDimensionReferencesResolve() {
        JsonObject dimension = read("/data/teras/dimension/vacio.json");
        assertEquals("teras:vacio", dimension.get("type").getAsString());

        JsonObject generator = dimension.getAsJsonObject("generator");
        assertEquals("minecraft:flat", generator.get("type").getAsString());

        JsonObject settings = generator.getAsJsonObject("settings");
        assertEquals("teras:vacio", settings.get("biome").getAsString());
        assertFalse(settings.get("features").getAsBoolean(), "an arena must not generate features");
        assertFalse(settings.get("lakes").getAsBoolean(), "an arena must not generate lakes");
        assertTrue(settings.getAsJsonArray("structure_overrides").isEmpty(),
                "an arena must not generate structures");

        assertNotNull(read("/data/teras/dimension_type/vacio.json"));
        assertEquals(REQUIRED, biome("vacio").keySet());
    }

    /**
     * Both dimension_type and biome carry the arena decisions; a later edit that re-enables mob
     * spawning or beds would change how events play out without touching any Java.
     */
    @Test
    void voidDimensionTypeKeepsTheArenaRules() {
        JsonObject type = read("/data/teras/dimension_type/vacio.json");
        assertTrue(type.get("has_skylight").getAsBoolean(), "arena keeps a day/night sky");
        assertFalse(type.get("bed_works").getAsBoolean(), "beds must not set spawn in the arena");
        assertFalse(type.get("respawn_anchor_works").getAsBoolean(),
                "respawn anchors must not set spawn in the arena");
        assertFalse(type.get("has_raids").getAsBoolean(), "raids must not trigger in the arena");

        assertEquals(0, type.get("height").getAsInt() % 16, "height must be a multiple of 16");
        assertEquals(0, type.get("min_y").getAsInt() % 16, "min_y must be a multiple of 16");
        assertTrue(type.get("logical_height").getAsInt() <= type.get("height").getAsInt(),
                "logical_height cannot exceed height");
    }

    @Test
    void everyBiomeHasADisplayNameInBothLanguages() {
        for (String lang : List.of("en_us", "es_es")) {
            JsonObject entries = read("/assets/teras/lang/" + lang + ".json");
            for (String id : TOWNS) {
                assertTrue(entries.has("biome.teras." + id), lang + ": missing name for " + id);
            }
            assertTrue(entries.has("biome.teras.vacio"), lang + ": missing name for vacio");
        }
    }
}
