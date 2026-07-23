package es.boffmedia.teras.dungeon.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rear-arc test behind {@link AbilityKind#FLANK_RAGE}. Facing +z at yaw 0, "behind" is −z; the
 * geometry has to hold at other yaws and reject the front, the sides and a shot from too shallow an
 * angle, or the queen rages at hits she is looking straight at.
 */
class FlankTest {

    private static final double ARC = 120.0;

    @Test
    void aHitFromDirectlyBehindIsRear() {
        // Facing +z (yaw 0), the attacker stands at −z.
        assertTrue(Flank.isRear(0, 0, 0f, 0, -5, ARC));
        // Facing −x (yaw 90), behind is +x.
        assertTrue(Flank.isRear(0, 0, 90f, 5, 0, ARC));
    }

    @Test
    void theFrontAndSidesAreNotRear() {
        assertFalse(Flank.isRear(0, 0, 0f, 0, 5, ARC), "straight ahead");
        assertFalse(Flank.isRear(0, 0, 0f, 5, 0, ARC), "dead to the side");
        assertFalse(Flank.isRear(0, 0, 0f, -5, 0, ARC), "the other side");
    }

    @Test
    void theArcHasEdges() {
        // 45° off directly behind is inside a 120° arc (±60°); 75° off is outside it.
        assertTrue(Flank.isRear(0, 0, 0f, Math.sin(Math.toRadians(45)),
                -Math.cos(Math.toRadians(45)), ARC), "just inside");
        assertFalse(Flank.isRear(0, 0, 0f, Math.sin(Math.toRadians(75)),
                -Math.cos(Math.toRadians(75)), ARC), "just outside");
        // A narrower arc rejects what the wide one accepted.
        assertFalse(Flank.isRear(0, 0, 0f, Math.sin(Math.toRadians(45)),
                -Math.cos(Math.toRadians(45)), 60.0), "45° off is outside a 60° arc");
    }

    @Test
    void anAttackerOnTopIsNotRear() {
        assertFalse(Flank.isRear(0, 0, 0f, 0, 0, ARC), "no direction to judge");
    }
}
