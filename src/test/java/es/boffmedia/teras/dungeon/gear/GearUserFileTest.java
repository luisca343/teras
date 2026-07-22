package es.boffmedia.teras.dungeon.gear;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The exact {@code gear.json} from the server, as written by hand.
 *
 * <p>Kept verbatim because it is the file that found two real defects, and a paraphrase would not
 * have found either: {@code escudo_hyliano} declared no {@code tipo} — it did not need to, having
 * already said {@code "skinType": "shield"} — and several pieces write {@code "habilidad": ""} for
 * "no ability", which the parser treated as a typo and warned about.</p>
 */
class GearUserFileTest {

    private static final String FILE = """
            {
              "espada_abisal": {
                "habilidad": "VAMPIRISMO", "magnitud": 0.1,
                "skin": "25992 - Vampire Sword.armour", "skinType": "sword",
                "stats": { "attack_damage": 6.0, "attack_speed": 0.2 }
              },
              "yelmo_laberinto": {
                "habilidad": "NINGUNA", "magnitud": 0.0, "skin": "", "skinType": "head",
                "stats": { "armor": 2.0, "movement_speed": 0.1 }
              },
              "talisman_sangre": {
                "habilidad": "VAMPIRISMO", "magnitud": 0.05, "skin": "", "skinType": "item",
                "stats": {}
              },
              "escudo_hyliano": {
                "habilidad": "", "magnitud": 1.0,
                "skin": "27207 - Hylian Shield.armour", "skinType": "shield",
                "stats": {}
              }
            }
            """;

    @Test
    void theFileLoadsWithoutComplaint() {
        GearDefs.Merge merge = GearDefs.merge(JsonParser.parseString(FILE).getAsJsonObject());
        assertTrue(merge.warnings().isEmpty(), merge.warnings().toString());
    }

    /** The piece that started this: a new shield, its kind inferred from the skin type. */
    @Test
    void theNewShieldExists() {
        GearDef shield = GearDefs.merge(JsonParser.parseString(FILE).getAsJsonObject())
                .defs().get("escudo_hyliano");

        assertNotNull(shield, "a piece the code has never heard of has to come out of the file");
        assertEquals(GearKind.SHIELD, shield.kind(), "inferred from skinType");
        assertEquals("gear_escudo", shield.kind().itemPath());
        assertTrue(shield.hasSkin());
        assertTrue(shield.abilities().isEmpty(), "a blank habilidad is no ability, not a typo");
    }

    /**
     * A piece defined only in the file has no lang entry, so it used to render in a player's hand as
     * the raw key {@code item.teras.escudo_hyliano}. The derived name is the floor under that.
     */
    @Test
    void aConfigOnlyPieceIsNeverNameless() {
        GearDef shield = GearDefs.merge(JsonParser.parseString(FILE).getAsJsonObject())
                .defs().get("escudo_hyliano");
        assertFalse(shield.hasName(), "the file sets no nombre");
        assertEquals("Escudo Hyliano", shield.derivedName());
    }

    /** An explicit nombre wins over both the lang key and the derived name. */
    @Test
    void anExplicitNombreWins() {
        JsonObject root = JsonParser.parseString(FILE).getAsJsonObject();
        root.getAsJsonObject("escudo_hyliano").addProperty("nombre", "Escudo de Hyrule");
        GearDef shield = GearDefs.merge(root).defs().get("escudo_hyliano");
        assertTrue(shield.hasName());
        assertEquals("Escudo de Hyrule", shield.nombre());
    }

    @Test
    void theExistingPiecesAreStillRetuned() {
        JsonObject root = JsonParser.parseString(FILE).getAsJsonObject();
        GearDef sword = GearDefs.merge(root).defs().get("espada_abisal");
        assertEquals(0.1, sword.ability(GearAbility.VAMPIRISMO).magnitude(0));
        assertEquals(6.0, sword.stats().stream()
                .filter(s -> s.stat() == GearStat.ATTACK_DAMAGE).findFirst().orElseThrow().amount());
        assertTrue(sword.hasSkin());
    }
}
