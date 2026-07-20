package es.boffmedia.teras.dungeon.piso;

import es.boffmedia.teras.dungeon.model.RoomShape;
import es.boffmedia.teras.dungeon.model.ShapeFamily;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The room vocabulary, which is the whole of the authoring cost. These tests are really about the
 * shape opt-out: it is the one lever that reduces what a piso must build without letting it borrow
 * from another piso, so what it does and does not excuse has to be exact.
 */
class RoomKeysTest {

    @Test
    void thirteenAreRequiredOfEveryPiso() {
        assertEquals(13, RoomKeys.REQUIRED.size());
        Set<String> minimal = RoomKeys.requiredFor(EnumSet.of(ShapeFamily.SINGLE));
        assertEquals(13, minimal.size(), "SINGLE alone must add nothing beyond the required set");
        assertTrue(minimal.containsAll(RoomKeys.REQUIRED));
    }

    /** The full vocabulary: 13 required + large, l, big normals + boss_big. */
    @Test
    void everyFamilyGivesSeventeen() {
        Set<String> all = RoomKeys.requiredFor(EnumSet.allOf(ShapeFamily.class));
        assertEquals(17, all.size(), all.toString());
        assertTrue(all.contains("normal_large"));
        assertTrue(all.contains("normal_l"));
        assertTrue(all.contains("normal_big"));
        assertTrue(all.contains("boss_big"));
    }

    /** The cost lever: declining a family must genuinely excuse its rooms. */
    @Test
    void decliningFamiliesExcusesTheirRooms() {
        Set<String> tight = RoomKeys.requiredFor(
                EnumSet.of(ShapeFamily.SINGLE, ShapeFamily.LARGE));
        assertEquals(14, tight.size());
        assertTrue(tight.contains("normal_large"));
        assertFalse(tight.contains("normal_big"));
        assertFalse(tight.contains("normal_l"));
        assertFalse(tight.contains("boss_big"));
    }

    /** boss_big rides the BIG opt-out: no 2x2 family means no 2x2 boss chamber to build. */
    @Test
    void bossBigFollowsTheBigOptOut() {
        assertFalse(RoomKeys.requiredFor(EnumSet.of(ShapeFamily.SINGLE, ShapeFamily.LARGE))
                .contains("boss_big"));
        assertTrue(RoomKeys.requiredFor(EnumSet.of(ShapeFamily.SINGLE, ShapeFamily.BIG))
                .contains("boss_big"));
    }

    /** Every orientation of a family resolves to the same template — the point of the change. */
    @Test
    void keyIsPerFamilyNotPerOrientation() {
        assertEquals("normal", RoomKeys.keyFor("normal", RoomShape.SINGLE));
        assertEquals("normal_big", RoomKeys.keyFor("normal", RoomShape.QUAD));
        assertEquals("boss_big", RoomKeys.keyFor("boss", RoomShape.QUAD));
        assertEquals(RoomKeys.keyFor("normal", RoomShape.HORIZONTAL),
                RoomKeys.keyFor("normal", RoomShape.VERTICAL));
        for (RoomShape shape : List.of(RoomShape.L_TOP_LEFT, RoomShape.L_TOP_RIGHT,
                RoomShape.L_BOTTOM_LEFT, RoomShape.L_BOTTOM_RIGHT)) {
            assertEquals("normal_l", RoomKeys.keyFor("normal", shape));
        }
    }

    /** Keys are the on-disk template names, so a rename here silently orphans authored rooms. */
    @Test
    void keysMatchTheFamilySpelling() {
        for (ShapeFamily family : ShapeFamily.values()) {
            if (family == ShapeFamily.SINGLE) {
                continue;
            }
            String expected = "normal_" + family.name().toLowerCase(java.util.Locale.ROOT);
            assertTrue(RoomKeys.requiredFor(EnumSet.of(ShapeFamily.SINGLE, family)).contains(expected),
                    "no key for " + family);
        }
    }

    @Test
    void requiredSetHasNoDuplicates() {
        assertEquals(RoomKeys.REQUIRED.size(), Set.copyOf(RoomKeys.REQUIRED).size());
    }

    /** A piso must declare SINGLE; RoomKeys names it so validation and the docs cannot drift. */
    @Test
    void singleIsTheMandatoryFamily() {
        assertEquals(ShapeFamily.SINGLE, RoomKeys.MANDATORY_FAMILY);
    }

    @Test
    void requiredNamesAreStable() {
        assertEquals(List.of("start", "normal", "boss", "mini_boss", "shop", "treasure",
                "secret", "super_secret", "challenge", "curse", "sacrifice",
                "arcade", "devil_deal"), RoomKeys.REQUIRED);
    }
}
