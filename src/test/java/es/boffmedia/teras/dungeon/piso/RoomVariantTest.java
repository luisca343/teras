package es.boffmedia.teras.dungeon.piso;

import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.ShapeFamily;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a room key resolves to a template. The convention is load-bearing: with no {@code salas}
 * block a piso still has to name a real file for every room it owes, and it has no other piso to
 * borrow from if the name is wrong.
 */
class RoomVariantTest {

    private static FloorDef piso(Map<String, List<RoomVariant>> salas, Set<ShapeFamily> shapes) {
        return new FloorDef("cuevas", "Cuevas", "", shapes, 7, "", "", "",
                EnumSet.of(Curse.LOST), List.of(), List.of(), salas);
    }

    @Test
    void anUndeclaredKeyResolvesToTheConventionalTemplate() {
        FloorDef bare = piso(Map.of(), EnumSet.of(ShapeFamily.SINGLE));
        List<RoomVariant> variants = bare.variants("normal");
        assertEquals(1, variants.size());
        assertEquals("teras:dungeon/cuevas/normal", variants.get(0).template());
        assertEquals(1, variants.get(0).weight());
    }

    @Test
    void declaredVariantsReplaceTheConvention() {
        FloorDef authored = piso(Map.of("normal", List.of(
                new RoomVariant("teras:dungeon/cuevas/normal_a", 3, 0),
                new RoomVariant("teras:dungeon/cuevas/normal_b", 1, 90))),
                EnumSet.of(ShapeFamily.SINGLE));
        List<RoomVariant> variants = authored.variants("normal");
        assertEquals(2, variants.size());
        assertEquals(90, variants.get(1).rotation());
        // Keys it did not declare still fall to the convention, not to nothing.
        assertEquals("teras:dungeon/cuevas/shop", authored.variants("shop").get(0).template());
    }

    /** A weight below one would drop a variant out of the draw entirely. */
    @Test
    void weightsAreClamped() {
        assertEquals(1, new RoomVariant("t", 0, 0).weight());
        assertEquals(1, new RoomVariant("t", -3, 0).weight());
        assertEquals(5, new RoomVariant("t", 5, 0).weight());
    }

    /** Only the four right angles exist; anything else would silently mis-place a room. */
    @Test
    void rotationsAreNormalisedToRightAngles() {
        assertEquals(0, new RoomVariant("t", 1, 0).rotation());
        assertEquals(90, new RoomVariant("t", 1, 90).rotation());
        assertEquals(270, new RoomVariant("t", 1, -90).rotation());
        assertEquals(0, new RoomVariant("t", 1, 360).rotation());
        assertEquals(0, new RoomVariant("t", 1, 45).rotation(), "45 is not a legal rotation");
    }

    /**
     * What a piso owes follows its shapes, so {@code allVariants} is what the load-time existence
     * check walks — a narrowed piso must not be asked for rooms it will never generate.
     */
    @Test
    void allVariantsCoverExactlyWhatIsOwed() {
        FloorDef tight = piso(Map.of(), EnumSet.of(ShapeFamily.SINGLE, ShapeFamily.LARGE));
        assertEquals(tight.requiredRooms().size(), tight.allVariants().size());

        FloorDef wide = piso(Map.of(), EnumSet.allOf(ShapeFamily.class));
        assertEquals(17, wide.allVariants().size());
        assertTrue(wide.allVariants().stream()
                .anyMatch(v -> v.template().endsWith("/boss_big")));
        assertFalse(tight.allVariants().stream()
                .anyMatch(v -> v.template().endsWith("/boss_big")));
    }

    /** Several variants of one key each count toward what must exist on disk. */
    @Test
    void extraVariantsAreAlsoOwed() {
        FloorDef authored = piso(Map.of("normal", List.of(
                new RoomVariant("teras:dungeon/cuevas/normal_a", 1, 0),
                new RoomVariant("teras:dungeon/cuevas/normal_b", 1, 0))),
                EnumSet.of(ShapeFamily.SINGLE));
        assertEquals(authored.requiredRooms().size() + 1, authored.allVariants().size());
    }
}
