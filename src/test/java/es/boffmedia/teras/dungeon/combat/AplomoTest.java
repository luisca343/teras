package es.boffmedia.teras.dungeon.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Poise, and the two rules that stop it becoming a stun-lock — which four players make reachable in
 * a way a single-player game never has to consider.
 */
class AplomoTest {

    private static void tick(Aplomo aplomo, int times) {
        for (int i = 0; i < times; i++) {
            aplomo.tick();
        }
    }

    @Test
    void chippingShortOfTheBarDoesNotBreakIt() {
        Aplomo aplomo = new Aplomo(10);
        assertFalse(aplomo.chip(4));
        assertFalse(aplomo.chip(4));
        assertFalse(aplomo.staggered());
        assertEquals(2, aplomo.current(), 1e-9);
    }

    @Test
    void spendingTheBarBreaksItAndOpensAWindow() {
        Aplomo aplomo = new Aplomo(10);
        assertTrue(aplomo.chip(10), "the breaking blow has to say so — the caller owes a tell");
        assertTrue(aplomo.staggered());
        assertEquals(Aplomo.STAGGER_TICKS, aplomo.staggerRemaining());
    }

    /**
     * The anti-stun-lock rule. A boss held permanently open is not a fight, and a party of four can
     * otherwise keep one there indefinitely.
     */
    @Test
    void breakingRefillsTheBarSoTheNextBreakIsEarnedAgain() {
        Aplomo aplomo = new Aplomo(10);
        aplomo.chip(10);
        assertEquals(10, aplomo.current(), 1e-9, "a broken guard came back empty");
        assertFalse(aplomo.chip(10), "a target already open must not break a second time");
    }

    @Test
    void theWindowClosesOnItsOwn() {
        Aplomo aplomo = new Aplomo(10);
        aplomo.chip(10);
        tick(aplomo, Aplomo.STAGGER_TICKS);
        assertFalse(aplomo.staggered());
    }

    /**
     * The other half: without a delay, chip damage alone accumulates into a guaranteed break over a
     * long enough fight, and the heavy attack is redundant again by a slower route.
     */
    @Test
    void poiseWaitsForQuietBeforeItComesBack() {
        Aplomo aplomo = new Aplomo(10);
        aplomo.chip(6);
        tick(aplomo, Aplomo.REGEN_DELAY_TICKS - 1);
        assertEquals(4, aplomo.current(), 1e-9, "it recovered while the fight was still on it");

        tick(aplomo, 1 + Aplomo.REGEN_TICKS);
        assertEquals(10, aplomo.current(), 1e-9, "quiet should have brought the whole bar back");
    }

    @Test
    void aFreshChipRestartsTheWait() {
        Aplomo aplomo = new Aplomo(10);
        aplomo.chip(5);
        tick(aplomo, Aplomo.REGEN_DELAY_TICKS - 5);
        aplomo.chip(1);
        tick(aplomo, 10);
        assertEquals(4, aplomo.current(), 1e-9, "the second chip did not restart the window");
    }

    @Test
    void aBodyWithNoPoiseCannotBeStaggered() {
        Aplomo aplomo = new Aplomo(0);
        assertFalse(aplomo.chip(100));
        assertFalse(aplomo.staggered());
    }

    @Test
    void resetClosesTheWindowAndFillsTheBar() {
        Aplomo aplomo = new Aplomo(10);
        aplomo.chip(10);
        aplomo.reset();
        assertFalse(aplomo.staggered());
        assertEquals(10, aplomo.current(), 1e-9);
    }
}
