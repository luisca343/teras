package es.boffmedia.teras.dungeon.gear;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Abilities as {@code {id, params}}.
 *
 * <p>Two limits of the old shape are what this replaced, and both are worth a test: an ability could
 * carry exactly one number, so anything it needed beyond that was compiled in (the wither level, the
 * shockwave radius); and a piece could carry exactly one ability, so a sword that both burned and
 * stole life was simply not expressible.</p>
 */
class AbilityDefTest {

    @Test
    void aParamlessAbilityFallsBackToItsOwnDefault() {
        AbilityDef bare = new AbilityDef(GearAbility.ONDA, Map.of());
        assertEquals(GearAbility.ONDA.defaultMagnitude(),
                bare.magnitude(GearAbility.ONDA.defaultMagnitude()));
        assertEquals(2.5, bare.doubleParam(GearAbility.P_RADIUS, 2.5),
                "an unset param is the caller's default, not zero");
    }

    /** The numbers that used to be hard-coded now have names and can be written down. */
    @Test
    void theSecondNumberIsReachable() {
        AbilityDef wither = new AbilityDef(GearAbility.DESGARRO,
                Map.of(AbilityDef.MAGNITUDE, "6", GearAbility.P_LEVEL, "3"));
        assertEquals(6.0, wither.magnitude(3));
        assertEquals(3, wither.intParam(GearAbility.P_LEVEL, 1));

        AbilityDef wave = new AbilityDef(GearAbility.ONDA,
                Map.of(AbilityDef.MAGNITUDE, "0.3", GearAbility.P_RADIUS, "6"));
        assertEquals(6.0, wave.doubleParam(GearAbility.P_RADIUS, 2.5));
    }

    /** A bad value costs the tuning, never the ability — the same rule MechanicDef follows. */
    @Test
    void anUnparseableParamFallsBackRatherThanThrowing() {
        AbilityDef broken = new AbilityDef(GearAbility.QUEMAZON,
                Map.of(AbilityDef.MAGNITUDE, "tres segundos", GearAbility.P_LEVEL, ""));
        assertEquals(3.0, broken.magnitude(3.0));
        assertEquals(1, broken.intParam(GearAbility.P_LEVEL, 1));
    }

    @Test
    void nothingIsNotAnAbility() {
        assertTrue(AbilityDef.none().isNone());
        assertTrue(new AbilityDef(null, null).isNone());
        // A def given NINGUNA carries no abilities at all, rather than one that does nothing.
        GearDef stick = new GearDef("x", GearKind.SWORD, GearDef.Rarity.COMUN, List.of(),
                GearAbility.NINGUNA, 0, "", "");
        assertEquals(List.of(), stick.abilities());
        assertFalse(stick.has(GearAbility.NINGUNA));
    }

    /** The whole point of the list: one piece, several abilities. */
    @Test
    void aPieceCanCarrySeveralAbilities() {
        GearDef both = new GearDef("x", GearKind.SWORD, GearDef.Rarity.RARO, List.of(),
                List.of(AbilityDef.of(GearAbility.VAMPIRISMO, 0.1),
                        AbilityDef.of(GearAbility.QUEMAZON, 4)), "", "");
        assertTrue(both.has(GearAbility.VAMPIRISMO));
        assertTrue(both.has(GearAbility.QUEMAZON));
        assertFalse(both.has(GearAbility.ONDA));
        assertEquals(0.1, both.ability(GearAbility.VAMPIRISMO).magnitude(0));
    }

    /** The legacy one-ability keys still tune what they always tuned. */
    @Test
    void theOldSingleAbilityShapeStillWorks() {
        GearDef legacy = new GearDef("x", GearKind.SWORD, GearDef.Rarity.COMUN, List.of(),
                GearAbility.BOTIN, 5, "", "");
        assertEquals(1, legacy.abilities().size());
        assertEquals(5.0, legacy.abilities().get(0).magnitude(0));
        assertEquals(9.0, legacy.withMagnitude(9).abilities().get(0).magnitude(0));
        assertEquals(GearAbility.ONDA, legacy.withAbility(GearAbility.ONDA).abilities().get(0).ability());
    }

    /** gear.json: the list form, and a bare name for an ability that needs no numbers. */
    @Test
    void theFileCanNameAbilitiesAsAListOrAsBareNames() {
        JsonObject entry = new JsonObject();
        JsonArray abilities = new JsonArray();
        abilities.add("ONDA");
        JsonObject withParams = new JsonObject();
        withParams.addProperty("id", "DESGARRO");
        JsonObject params = new JsonObject();
        params.addProperty(AbilityDef.MAGNITUDE, 8);
        params.addProperty(GearAbility.P_LEVEL, 2);
        withParams.add("params", params);
        abilities.add(withParams);
        entry.add("habilidades", abilities);

        JsonObject root = new JsonObject();
        root.add("espada_abisal", entry);
        GearDefs.Merge merge = GearDefs.merge(root);

        assertTrue(merge.warnings().isEmpty(), merge.warnings().toString());
        GearDef merged = merge.defs().get("espada_abisal");
        assertEquals(2, merged.abilities().size());
        assertTrue(merged.has(GearAbility.ONDA));
        assertEquals(8.0, merged.ability(GearAbility.DESGARRO).magnitude(0));
        assertEquals(2, merged.ability(GearAbility.DESGARRO).intParam(GearAbility.P_LEVEL, 1));
    }

    /** An unimplemented ability is dropped with a warning, never silently accepted. */
    @Test
    void anUnknownAbilityInTheListWarns() {
        JsonObject entry = new JsonObject();
        JsonArray abilities = new JsonArray();
        abilities.add("TELETRANSPORTE");
        entry.add("habilidades", abilities);
        JsonObject root = new JsonObject();
        root.add("espada_abisal", entry);

        GearDefs.Merge merge = GearDefs.merge(root);
        assertEquals(1, merge.warnings().size(), merge.warnings().toString());
        assertTrue(merge.warnings().get(0).contains("TELETRANSPORTE"));
    }
}
