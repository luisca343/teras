package es.boffmedia.teras.dungeon.run;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The party purse: earning, spending, charges and the death penalty. */
class DungeonWalletTest {

    @Test
    void addsAndTracksLifetimeEarnings() {
        DungeonWallet wallet = new DungeonWallet();
        wallet.add(10);
        wallet.add(5);

        assertEquals(15, wallet.coins());
        assertEquals(15, wallet.totalEarned());
    }

    /** A run's report needs what was earned overall, which spending must not erase. */
    @Test
    void spendingLeavesLifetimeEarningsAlone() {
        DungeonWallet wallet = new DungeonWallet();
        wallet.add(30);
        assertTrue(wallet.trySpend(20));

        assertEquals(10, wallet.coins());
        assertEquals(30, wallet.totalEarned());
        assertEquals(20, wallet.totalSpent());
    }

    /** A refused purchase must cost nothing at all — the shop prices first and acts on the result. */
    @Test
    void refusedPurchaseChangesNothing() {
        DungeonWallet wallet = new DungeonWallet();
        wallet.add(5);

        assertFalse(wallet.trySpend(10));
        assertEquals(5, wallet.coins());
        assertEquals(0, wallet.totalSpent());
    }

    @Test
    void ignoresNonPositiveAmounts() {
        DungeonWallet wallet = new DungeonWallet();
        wallet.add(-10);
        wallet.add(0);

        assertEquals(0, wallet.coins());
        assertEquals(0, wallet.totalEarned());
    }

    @Test
    void chargesAreSpentOneAtATime() {
        DungeonWallet wallet = new DungeonWallet();
        wallet.addCharges(2);

        assertTrue(wallet.tryUseCharge());
        assertTrue(wallet.tryUseCharge());
        assertFalse(wallet.tryUseCharge());
        assertEquals(0, wallet.wallCharges());
    }

    @Test
    void deathPenaltyTakesItsShareAndReportsIt() {
        DungeonWallet wallet = new DungeonWallet();
        wallet.add(100);

        assertEquals(20, wallet.applyDeathPenalty(20));
        assertEquals(80, wallet.coins());
    }

    /** Rounds up, so a small purse still feels a death rather than shrugging it off. */
    @Test
    void deathPenaltyRoundsUpOnSmallPurses() {
        DungeonWallet wallet = new DungeonWallet();
        wallet.add(3);

        assertEquals(1, wallet.applyDeathPenalty(20));
        assertEquals(2, wallet.coins());
    }

    @Test
    void deathPenaltyOnAnEmptyPurseCostsNothing() {
        DungeonWallet wallet = new DungeonWallet();

        assertEquals(0, wallet.applyDeathPenalty(20));
        assertEquals(0, wallet.coins());
    }
}
