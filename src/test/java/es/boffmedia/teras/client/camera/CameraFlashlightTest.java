package es.boffmedia.teras.client.camera;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the flashlight's switch and, more importantly, for {@code isOurs} — the guard deciding
 * whether a night-vision effect may be removed. Getting it wrong silently deletes a player's potion,
 * and nothing else would catch that.
 */
class CameraFlashlightTest {

    /** The duration the flashlight refreshes at; anything longer is someone else's. */
    private static final int EFFECT_TICKS = 210;

    @AfterEach
    void reset() {
        CameraFlashlight.setEnabled(false);
    }

    @Test
    void togglesTheSwitch() {
        CameraFlashlight.setEnabled(false);
        assertTrue(CameraFlashlight.toggle());
        assertTrue(CameraFlashlight.isEnabled());
        assertFalse(CameraFlashlight.toggle());
        assertFalse(CameraFlashlight.isEnabled());
    }

    @Test
    void claimsAnEffectItCouldHaveApplied() {
        assertTrue(CameraFlashlight.isOurs(0, EFFECT_TICKS), "a freshly refreshed flashlight");
        assertTrue(CameraFlashlight.isOurs(0, 1), "one ticking out");
    }

    /**
     * A real potion is 3–8 minutes; vanilla merges a duplicate effect by keeping the longer duration, so
     * once a potion is involved the surviving instance is always longer than anything we apply.
     */
    @Test
    void neverClaimsARealPotion() {
        assertFalse(CameraFlashlight.isOurs(0, EFFECT_TICKS + 1), "just longer than ours");
        assertFalse(CameraFlashlight.isOurs(0, 3600), "a 3-minute potion");
        assertFalse(CameraFlashlight.isOurs(0, 9600), "an 8-minute extended potion");
    }

    /** We only ever apply amplifier 0, so a stronger effect is someone else's whatever its duration. */
    @Test
    void neverClaimsAStrongerEffect() {
        assertFalse(CameraFlashlight.isOurs(1, EFFECT_TICKS));
        assertFalse(CameraFlashlight.isOurs(1, 10));
    }
}
