package es.boffmedia.teras.dungeon.gear;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Defining a whole new piece of gear in {@code gear.json}.
 *
 * <p>This did not work, and the documentation said it did. {@code merge} answered an id it did not
 * recognise with "unknown gear, ignoring", so the file was an override layer over a code-authored
 * catalog: a piece could be retuned and never added. That is the opposite of the point of putting
 * one registered item behind each {@link GearKind} — the item exists so that a piece is a component
 * and a config entry, needing no code at all.</p>
 *
 * <p>Found by Luisca adding a piece and finding {@code /teras dungeon gear dar} would not give it.</p>
 */
class GearFromConfigTest {

    private static JsonObject piece(String tipo) {
        JsonObject json = new JsonObject();
        json.addProperty("tipo", tipo);
        return json;
    }

    private static GearDefs.Merge mergeOne(String id, JsonObject entry) {
        JsonObject root = new JsonObject();
        root.add(id, entry);
        return GearDefs.merge(root);
    }

    @Test
    void aNewPieceNeedsOnlyItsTipo() {
        GearDefs.Merge merge = mergeOne("guadana_test", piece("sword"));

        assertTrue(merge.warnings().isEmpty(), merge.warnings().toString());
        GearDef made = merge.defs().get("guadana_test");
        assertNotNull(made, "a new id has to produce a piece");
        assertEquals(GearKind.SWORD, made.kind());
        assertEquals(GearDef.Rarity.COMUN, made.rarity(), "rarity falls back");
        assertEquals("gear_espada", made.kind().itemPath(), "it is built on the sword item");
        assertTrue(made.stats().isEmpty());
        assertTrue(made.abilities().isEmpty());
    }

    @Test
    void aWornTintReadsAsOpaqueArgbAndFallsBackWhenBlank() {
        JsonObject entry = piece("chestplate");
        entry.addProperty("tint", "#6FA8FF");
        GearDef tinted = mergeOne("coraza_test", entry).defs().get("coraza_test");
        assertEquals(0xFF6FA8FF, tinted.tint(), "a #RRGGBB tint is forced opaque");

        JsonObject bare = piece("helmet");
        bare.addProperty("tint", "");
        GearDef untinted = mergeOne("yelmo_test", bare).defs().get("yelmo_test");
        assertEquals(0, untinted.tint(), "blank means unset — the renderer uses the rarity colour");
    }

    @Test
    void aMalformedTintWarnsAndStaysUnset() {
        JsonObject entry = piece("boots");
        entry.addProperty("tint", "not-a-colour");
        GearDefs.Merge merge = mergeOne("botas_test", entry);
        assertEquals(0, merge.defs().get("botas_test").tint());
        assertTrue(merge.warnings().stream().anyMatch(w -> w.contains("tint")),
                "a bad colour is worth a word: " + merge.warnings());
    }

    @Test
    void anExplicitTintSurvivesRetuningAnotherField() {
        JsonObject entry = piece("chestplate");
        entry.addProperty("tint", "#123456");
        JsonObject stats = new JsonObject();
        stats.addProperty("armor", 5.0);
        entry.add("stats", stats);
        GearDef made = mergeOne("coraza_test", entry).defs().get("coraza_test");
        assertEquals(0xFF123456, made.tint(), "a stat retune must not drop the tint");
    }

    /** The built-ins have to survive a file that adds to them. */
    @Test
    void addingAPieceLeavesTheCatalogAlone() {
        GearDefs.Merge merge = mergeOne("guadana_test", piece("axe"));
        assertEquals(GearDefs.defaults().size() + 1, merge.defs().size());
        assertNotNull(merge.defs().get("espada_abisal"));
    }

    @Test
    void aNewPieceCanCarryStatsAndAbilities() {
        JsonObject entry = piece("chestplate");
        entry.addProperty("rareza", "EPICO");
        JsonObject stats = new JsonObject();
        stats.addProperty("armor", 7.0);
        JsonObject fraction = new JsonObject();
        fraction.addProperty("amount", 0.2);
        fraction.addProperty("op", "fraction_of_base");
        stats.add("movement_speed", fraction);
        entry.add("stats", stats);
        JsonArray abilities = new JsonArray();
        abilities.add("ESPINAS");
        entry.add("habilidades", abilities);

        GearDefs.Merge merge = mergeOne("coraza_test", entry);
        assertTrue(merge.warnings().isEmpty(), merge.warnings().toString());
        GearDef made = merge.defs().get("coraza_test");

        assertEquals(GearDef.Rarity.EPICO, made.rarity());
        assertEquals(2, made.stats().size(), "a new piece starts with no stats and gains both");
        assertTrue(made.has(GearAbility.ESPINAS));
        GearDef.Stat speed = made.stats().stream()
                .filter(s -> s.stat() == GearStat.MOVEMENT_SPEED).findFirst().orElseThrow();
        assertEquals(0.2, speed.amount());
        assertEquals(GearOp.FRACTION_OF_BASE, speed.operation(), "the file chose the operation");
    }

    /** An existing piece can now be given a stat line it never had, not only retuned. */
    @Test
    void anExistingPieceCanGainAStatItLacked() {
        JsonObject entry = new JsonObject();
        JsonObject stats = new JsonObject();
        stats.addProperty("max_health", 4.0);
        entry.add("stats", stats);

        GearDef merged = mergeOne("espada_abisal", entry).defs().get("espada_abisal");
        GearDef base = GearDefs.defaults().get("espada_abisal");
        assertEquals(base.stats().size() + 1, merged.stats().size());
        assertTrue(merged.stats().stream().anyMatch(s -> s.stat() == GearStat.MAX_HEALTH));
    }

    /** Tipo is the one field with no default, because it decides the item, the slot and the skin. */
    @Test
    void aNewPieceWithoutATipoIsRefusedLoudly() {
        GearDefs.Merge merge = mergeOne("sin_tipo", new JsonObject());
        assertNull(merge.defs().get("sin_tipo"));
        assertEquals(1, merge.warnings().size());
        assertTrue(merge.warnings().get(0).contains("tipo"));
    }

    @Test
    void anUnknownTipoIsRefusedAndNamesTheValidOnes() {
        GearDefs.Merge merge = mergeOne("raro", piece("varita"));
        assertNull(merge.defs().get("raro"));
        assertTrue(merge.warnings().get(0).contains("varita"));
        assertTrue(merge.warnings().get(0).contains("SWORD"));
    }
}
