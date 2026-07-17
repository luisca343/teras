package es.boffmedia.teras.client.camera;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the zoom level arithmetic. Pure state — {@code isActive()} and the FOV event need a client,
 * but the level, clamping and factors don't.
 */
class CameraZoomTest {

    private int original;

    @BeforeEach
    void remember() {
        original = CameraZoom.level();
    }

    @AfterEach
    void restore() {
        CameraZoom.setLevel(original);
    }

    @Test
    void clampsToValidLevels() {
        CameraZoom.setLevel(-5);
        assertEquals(0, CameraZoom.level());
        CameraZoom.setLevel(99);
        assertEquals(CameraZoom.levelCount() - 1, CameraZoom.level());
    }

    @Test
    void stepsUpAndDown() {
        CameraZoom.setLevel(1);
        assertTrue(CameraZoom.step(1));
        assertEquals(2, CameraZoom.level());
        assertTrue(CameraZoom.step(-1));
        assertEquals(1, CameraZoom.level());
    }

    /**
     * The key handler only notifies the page when this returns true, so a step that clamps must report
     * false — otherwise holding {@code +} at max zoom spams the browser with an unchanged value.
     */
    @Test
    void reportsNoMovementWhenAlreadyAtAnEnd() {
        CameraZoom.setLevel(0);
        assertFalse(CameraZoom.step(-1));
        assertEquals(0, CameraZoom.level());

        CameraZoom.setLevel(CameraZoom.levelCount() - 1);
        assertFalse(CameraZoom.step(1));
        assertEquals(CameraZoom.levelCount() - 1, CameraZoom.level());
    }

    /** A multi-level jump past the end still lands on the end, and counts as movement. */
    @Test
    void clampsAMultiLevelStep() {
        CameraZoom.setLevel(CameraZoom.levelCount() - 2);
        assertTrue(CameraZoom.step(10));
        assertEquals(CameraZoom.levelCount() - 1, CameraZoom.level());
    }

    /** Level 0 is "off": the factor the web shows must read 1×, not a zoom. */
    @Test
    void levelZeroIsNoZoom() {
        CameraZoom.setLevel(0);
        assertEquals(1.0, CameraZoom.currentFactor(), 0.001);
        assertEquals(1.0, CameraZoom.multiplierForLevel(0), 0.001);
    }

    /** Factor is the inverse of the FOV multiplier: a 0.5 multiplier is 2× zoom. */
    @Test
    void factorIsTheInverseOfTheMultiplier() {
        for (int i = 0; i < CameraZoom.levelCount(); i++) {
            assertEquals(1.0 / CameraZoom.multiplierForLevel(i), CameraZoom.zoomFactorForLevel(i), 0.001);
        }
    }

    /** Zooming in must never widen the view — each level is strictly tighter than the last. */
    @Test
    void higherLevelsZoomStrictlyFurtherIn() {
        for (int i = 1; i < CameraZoom.levelCount(); i++) {
            assertTrue(CameraZoom.multiplierForLevel(i) < CameraZoom.multiplierForLevel(i - 1),
                    "level " + i + " is not tighter than " + (i - 1));
        }
    }

    /** Out-of-range indices are asked for by the page's level list; they must not throw. */
    @Test
    void unknownLevelsFallBackToNoZoom() {
        assertEquals(1.0, CameraZoom.multiplierForLevel(-1), 0.001);
        assertEquals(1.0, CameraZoom.multiplierForLevel(CameraZoom.levelCount()), 0.001);
    }

    /**
     * Factors are exact, because the page prints them: deriving them from stored FOV multipliers
     * (1.16.5 stored 0.67 and 0.33) yields 1.4925373134328357 and 3.0303030303030303 in its level list.
     */
    @Test
    void factorsAreExactForDisplay() {
        double[] expected = {1.0, 1.5, 2.0, 3.0, 4.0};
        assertEquals(expected.length, CameraZoom.levelCount());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], CameraZoom.zoomFactorForLevel(i), 0.0,
                    "level " + i + " must print exactly " + expected[i]);
        }
    }
}
