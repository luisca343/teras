package es.boffmedia.teras.dungeon.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The light chain and the committed heavy — the two verbs a weapon has. */
class SwingStateTest {

    @Test
    void theChainRunsAndThenStartsOver() {
        SwingState swing = new SwingState();
        assertEquals(1, swing.light(0));
        assertEquals(2, swing.light(5));
        assertEquals(3, swing.light(10));
        assertEquals(1, swing.light(15), "a fourth hit inside the window restarts the chain");
    }

    @Test
    void droppingOutOfTheWindowLosesTheProgress() {
        SwingState swing = new SwingState();
        swing.light(0);
        swing.light(5);
        assertEquals(1, swing.light(5 + SwingState.COMBO_WINDOW_TICKS + 1),
                "a late hit continued a chain that should have lapsed");
    }

    @Test
    void theFirstSwingOfASessionStartsAFreshChain() {
        assertEquals(1, new SwingState().light(0), "tick zero must not read as mid-chain");
    }

    /** The chain's point: the finisher is what threatens a guard, the openers barely scratch it. */
    @Test
    void theFinisherIsWhatThreatensAGuard() {
        assertTrue(SwingState.poiseChip(3) > SwingState.poiseChip(1));
        assertTrue(SwingState.damageMultiplier(3) > SwingState.damageMultiplier(2));
        assertEquals(SwingState.damageMultiplier(1), SwingState.damageMultiplier(2), 1e-9,
                "the two openers should read the same; only the finisher pays");
    }

    /**
     * Poise is chipped by a flat amount per verb, never by damage. Tying it to damage made the best
     * damage build automatically the best stagger build, and made three ordinary swings break
     * anything — which is how the stagger stopped being an event.
     */
    @Test
    void chipIsAFlatAmountPerVerbAndTakesNoDamageArgument() {
        assertEquals(SwingState.OPENER_CHIP, SwingState.poiseChip(1), 1e-9);
        assertEquals(SwingState.OPENER_CHIP, SwingState.poiseChip(2), 1e-9);
        assertEquals(SwingState.FINISHER_CHIP, SwingState.poiseChip(SwingState.COMBO_LENGTH), 1e-9);
    }

    /**
     * The chain is the whole stagger tool now that PESADO is gone, so what it is worth against a guard
     * has to stay ahead of what a guard regains while you keep it up. A chain is 5; a chaff enemy's
     * fallback poise is about 8, so two chains break one — sustained pressure rather than one blow.
     */
    @Test
    void afullChainIsWorthTheSumOfItsSteps() {
        double chain = SwingState.poiseChip(1) + SwingState.poiseChip(2)
                + SwingState.poiseChip(SwingState.COMBO_LENGTH);
        assertEquals(SwingState.OPENER_CHIP * 2 + SwingState.FINISHER_CHIP, chain, 1e-9);
        assertTrue(chain > SwingState.FINISHER_CHIP,
                "the finisher alone must not be the whole chain, or the openers are decoration");
    }

    @Test
    void resetDropsTheChain() {
        SwingState swing = new SwingState();
        swing.light(0);
        swing.light(1);
        swing.reset();
        assertEquals(0, swing.step());
        assertEquals(1, swing.light(2), "a reset chain starts over rather than continuing");
    }
}
