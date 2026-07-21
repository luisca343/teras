package es.boffmedia.teras.dungeon.gen;

import es.boffmedia.teras.dungeon.model.ShapeFamily;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Per-piso shape odds.
 *
 * <p>The distinction this exists to make: <b>declaring</b> a shape and <b>meeting</b> one are
 * different things. Infestadas used to feel tight because it could not build a 2×2 at all, which
 * also meant it could never surprise anyone with one, and cost it three rooms it now gets free.
 * Weighting says "big rooms exist here, and they are rare" — the same feel, without the absence.
 */
class ShapeWeightsTest {

    @Test
    void weightsScaleTheMultiCellChancesAndLeaveEverythingElseAlone() {
        GenConfig base = GenConfig.defaults();
        GenConfig tight = base.withShapeWeights(Map.of(
                ShapeFamily.LARGE, 0.7, ShapeFamily.L, 0.6, ShapeFamily.BIG, 0.3));

        assertEquals(base.chanceQuad() * 0.3, tight.chanceQuad(), 1e-9);
        assertEquals(base.chanceHorizontal() * 0.7, tight.chanceHorizontal(), 1e-9);
        assertEquals(base.chanceVertical() * 0.7, tight.chanceVertical(), 1e-9);
        assertEquals(base.chanceLShape() * 0.6, tight.chanceLShape(), 1e-9);

        // Everything that is not a shape chance has to survive untouched: this is a lens over the
        // odds, not a second place to tune the generator.
        assertEquals(base.gridSize(), tight.gridSize());
        assertEquals(base.largeShapeDecay(), tight.largeShapeDecay());
        assertEquals(base.labyrinthMultiplier(), tight.labyrinthMultiplier());
        assertEquals(base.labyrinthRoomCap(), tight.labyrinthRoomCap());
        assertEquals(base.lostRoomBonus(), tight.lostRoomBonus());
        assertEquals(base.finalStageRooms(), tight.finalStageRooms());
        assertEquals(base.maxAttempts(), tight.maxAttempts());
        assertEquals(base.referenceLength(), tight.referenceLength());
    }

    /** No weights is the normal case, and must cost nothing. */
    @Test
    void anEmptyOrAbsentMapReturnsTheSameConfig() {
        GenConfig base = GenConfig.defaults();
        assertSame(base, base.withShapeWeights(Map.of()));
        assertSame(base, base.withShapeWeights(null));
    }

    /** An unlisted family is 1.0 — a piso weights the shapes it cares about and no others. */
    @Test
    void anUnlistedFamilyIsUnchanged() {
        GenConfig base = GenConfig.defaults();
        GenConfig only = base.withShapeWeights(Map.of(ShapeFamily.BIG, 0.25));
        assertEquals(base.chanceQuad() * 0.25, only.chanceQuad(), 1e-9);
        assertEquals(base.chanceHorizontal(), only.chanceHorizontal(), 1e-9);
        assertEquals(base.chanceLShape(), only.chanceLShape(), 1e-9);
    }

    /**
     * A negative weight is clamped rather than flipping the odds. Zero is left alone, because
     * "declared but never rolled" is a legitimate thing to say — {@code formas} is how you remove
     * the shape, and this is how you make it vanishingly rare without losing its rooms.
     */
    @Test
    void negativeWeightsAreClampedAndZeroIsAllowed() {
        GenConfig base = GenConfig.defaults();
        assertTrue(base.withShapeWeights(Map.of(ShapeFamily.BIG, -2.0)).chanceQuad() >= 0);
        assertEquals(0.0, base.withShapeWeights(Map.of(ShapeFamily.BIG, 0.0)).chanceQuad(), 1e-9);
    }

    /** SINGLE is not scalable: it is the fallback, and weighting it would say nothing new. */
    @Test
    void singleIsNotScaled() {
        GenConfig base = GenConfig.defaults();
        GenConfig same = base.withShapeWeights(Map.of(ShapeFamily.SINGLE, 0.1));
        assertEquals(base.chanceQuad(), same.chanceQuad(), 1e-9);
        assertEquals(base.chanceHorizontal(), same.chanceHorizontal(), 1e-9);
        assertEquals(base.chanceVertical(), same.chanceVertical(), 1e-9);
        assertEquals(base.chanceLShape(), same.chanceLShape(), 1e-9);
    }
}
