package es.boffmedia.teras.dungeon.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How far the esquiva carries when a wall is in the way.
 *
 * <p>The raycast itself is Minecraft's and cannot be unit-tested; what it produces is one number —
 * blocks of clear space ahead — and what that number does to the impulse is the whole rule.</p>
 */
class DodgeReachTest {

    private static final double IMPULSE = 0.82;

    @Test
    void openSpaceKeepsTheFullImpulse() {
        assertEquals(IMPULSE, DodgeReach.clamp(IMPULSE, -1));
    }

    @Test
    void aWallBeyondTheRollIsNotAWall() {
        assertEquals(IMPULSE, DodgeReach.clamp(IMPULSE, DodgeReach.travel(IMPULSE)));
        assertEquals(IMPULSE, DodgeReach.clamp(IMPULSE, DodgeReach.travel(IMPULSE) + 3));
    }

    @Test
    void aWallInsideTheRollStopsItShortOfTheFace() {
        double free = 2.0;
        double clamped = DodgeReach.clamp(IMPULSE, free);
        assertTrue(clamped < IMPULSE);
        assertEquals(free - DodgeReach.SKIN, DodgeReach.travel(clamped), 1.0e-9,
                "the roll ends a skin's width short of what the ray hit");
    }

    @Test
    void aWallAgainstYourFaceCancelsTheMovementAndNotTheRoll() {
        // Zero impulse, never negative: the dodge still happens, and its i-frames are the half that
        // was defending you.
        assertEquals(0.0, DodgeReach.clamp(IMPULSE, 0));
        assertEquals(0.0, DodgeReach.clamp(IMPULSE, DodgeReach.SKIN / 2));
    }

    @Test
    void theClampNeverLengthensTheRoll() {
        for (double free = 0; free < DodgeReach.travel(IMPULSE) + 1; free += 0.1) {
            double clamped = DodgeReach.clamp(IMPULSE, free);
            assertTrue(clamped >= 0 && clamped <= IMPULSE, "free=" + free + " gave " + clamped);
            assertTrue(DodgeReach.travel(clamped) <= Math.max(free, DodgeReach.travel(IMPULSE)));
        }
    }

    @Test
    void theRollIsTheImpulseHeldForTheWholeIframeWindow() {
        assertEquals(IMPULSE * DodgeState.IFRAME_TICKS, DodgeReach.travel(IMPULSE), 1.0e-9);
    }
}
