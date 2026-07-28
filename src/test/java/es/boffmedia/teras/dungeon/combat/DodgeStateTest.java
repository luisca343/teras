package es.boffmedia.teras.dungeon.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The esquiva's two windows: how long nothing can touch you, and how long until you may again. */
class DodgeStateTest {

    @Test
    void theFirstRollIsAlwaysReady() {
        assertTrue(new DodgeState().ready(0));
        assertTrue(new DodgeState().begin(0, 1));
    }

    @Test
    void rollingProtectsThenStopsProtecting() {
        DodgeState state = new DodgeState();
        state.begin(100, 1);
        assertTrue(state.invulnerable(100));
        assertTrue(state.invulnerable(100 + DodgeState.IFRAME_TICKS - 1));
        assertFalse(state.invulnerable(100 + DodgeState.IFRAME_TICKS),
                "the window has to shut, or the dodge is a toggle");
    }

    @Test
    void aSecondRollIsRefusedUntilTheCooldownIsSpent() {
        DodgeState state = new DodgeState();
        state.begin(0, 1);
        assertFalse(state.begin(DodgeState.BASE_COOLDOWN_TICKS - 1, 1));
        assertTrue(state.begin(DodgeState.BASE_COOLDOWN_TICKS, 1));
    }

    /**
     * {@code enfriamiento} is a rate, so it divides the wait. Subtracting would make each point worth
     * more than the last and would eventually reach zero, where the verb stops being a decision.
     */
    @Test
    void enfriamientoDividesTheWaitAndNeverReachesZero() {
        assertEquals(DodgeState.BASE_COOLDOWN_TICKS, DodgeState.cooldownTicks(1));
        assertEquals(DodgeState.BASE_COOLDOWN_TICKS / 2, DodgeState.cooldownTicks(2));
        assertTrue(DodgeState.cooldownTicks(Stat.ENFRIAMIENTO.max()) >= 1,
                "even a maxed sheet must wait a tick");
        assertTrue(DodgeState.cooldownTicks(0) > DodgeState.BASE_COOLDOWN_TICKS,
                "a nonsense rate must slow the roll, not divide by zero");
    }

    @Test
    void refusedRollsDoNotMoveEitherWindow() {
        DodgeState state = new DodgeState();
        state.begin(0, 1);
        long readyAt = state.readyIn(0);
        assertFalse(state.begin(5, 1));
        assertEquals(readyAt - 5, state.readyIn(5), "a refused press extended the cooldown");
        assertFalse(state.invulnerable(5 + DodgeState.IFRAME_TICKS),
                "a refused press granted fresh i-frames");
    }

    @Test
    void resetClearsBothWindows() {
        DodgeState state = new DodgeState();
        state.begin(1000, 1);
        state.reset();
        assertTrue(state.ready(0));
        assertFalse(state.invulnerable(1000));
    }
}
