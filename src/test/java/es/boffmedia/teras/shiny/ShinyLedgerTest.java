package es.boffmedia.teras.shiny;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The cue's cadence: who has been shown what, and whether they may be shown it again. */
class ShinyLedgerTest {

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BEA = UUID.randomUUID();
    private static final UUID PIKACHU = UUID.randomUUID();
    private static final UUID EEVEE = UUID.randomUUID();

    /** The shipped default: one cue per Pokémon, per player. */
    private static final int ONCE = 0;
    private static final int INTERVAL = 120;

    @Test
    @DisplayName("the first sighting always fires")
    void firstSightingFires() {
        assertTrue(new ShinyLedger().claim(ANA, PIKACHU, 0, ONCE));
    }

    @Test
    @DisplayName("once ever means once ever, however long the player stays near it")
    void onceEverIsOnceEver() {
        ShinyLedger ledger = new ShinyLedger();
        assertTrue(ledger.claim(ANA, PIKACHU, 0, ONCE));
        assertFalse(ledger.claim(ANA, PIKACHU, 20, ONCE));
        assertFalse(ledger.claim(ANA, PIKACHU, 20L * 60 * 60 * 24, ONCE));
    }

    @Test
    @DisplayName("a shiny the player keeps meeting is never evicted, so it cannot chime twice")
    void repeatedSightingsDoNotReArm() {
        ShinyLedger ledger = new ShinyLedger();
        assertTrue(ledger.claim(ANA, PIKACHU, 0, ONCE));
        // The scan considers it again every 10 ticks for an hour. Every one of those is a refusal,
        // and none of them may quietly become permission.
        for (long tick = 10; tick < 20L * 60 * 60; tick += 10) {
            assertFalse(ledger.claim(ANA, PIKACHU, tick, ONCE), "re-armed at tick " + tick);
        }
        assertTrue(ledger.hasSeen(ANA, PIKACHU));
    }

    @Test
    @DisplayName("with an interval set it stays silent until the interval passes, then fires")
    void repeatsOnInterval() {
        ShinyLedger ledger = new ShinyLedger();
        assertTrue(ledger.claim(ANA, PIKACHU, 100, INTERVAL));
        assertFalse(ledger.claim(ANA, PIKACHU, 100 + INTERVAL - 1, INTERVAL));
        assertTrue(ledger.claim(ANA, PIKACHU, 100 + INTERVAL, INTERVAL));
    }

    @Test
    @DisplayName("a refused claim does not push the next one further out")
    void refusedClaimDoesNotSlide() {
        ShinyLedger ledger = new ShinyLedger();
        ledger.claim(ANA, PIKACHU, 0, INTERVAL);
        ledger.claim(ANA, PIKACHU, INTERVAL - 1, INTERVAL); // refused
        assertTrue(ledger.claim(ANA, PIKACHU, INTERVAL, INTERVAL));
    }

    @Test
    @DisplayName("one player's sighting does not silence another's")
    void perPlayer() {
        ShinyLedger ledger = new ShinyLedger();
        assertTrue(ledger.claim(ANA, PIKACHU, 0, ONCE));
        assertTrue(ledger.claim(BEA, PIKACHU, 0, ONCE));
    }

    @Test
    @DisplayName("different Pokemon are tracked separately for the same player")
    void perPokemon() {
        ShinyLedger ledger = new ShinyLedger();
        assertTrue(ledger.claim(ANA, PIKACHU, 0, ONCE));
        assertTrue(ledger.claim(ANA, EEVEE, 0, ONCE));
    }

    @Test
    @DisplayName("hasSeen reports only what was actually shown")
    void hasSeen() {
        ShinyLedger ledger = new ShinyLedger();
        ledger.claim(ANA, PIKACHU, 0, ONCE);
        assertTrue(ledger.hasSeen(ANA, PIKACHU));
        assertFalse(ledger.hasSeen(ANA, EEVEE));
        assertFalse(ledger.hasSeen(BEA, PIKACHU));
    }

    @Test
    @DisplayName("forget drops one player's rows and nobody else's")
    void forgetIsPerPlayer() {
        ShinyLedger ledger = new ShinyLedger();
        ledger.claim(ANA, PIKACHU, 0, ONCE);
        ledger.claim(BEA, PIKACHU, 0, ONCE);
        ledger.forget(ANA);
        assertEquals(1, ledger.size());
        assertTrue(ledger.claim(ANA, PIKACHU, 1, ONCE));
        assertFalse(ledger.claim(BEA, PIKACHU, 1, ONCE));
    }

    @Test
    @DisplayName("the ledger is bounded, and drops the least recently seen first")
    void capEvictsLeastRecentlySeen() {
        ShinyLedger ledger = new ShinyLedger();
        UUID first = UUID.randomUUID();
        ledger.claim(ANA, first, 0, ONCE);
        for (int i = 0; i < ShinyLedger.MAX_ENTRIES; i++) {
            ledger.claim(ANA, UUID.randomUUID(), 100 + i, ONCE);
        }
        assertEquals(ShinyLedger.MAX_ENTRIES, ledger.size());
        assertFalse(ledger.hasSeen(ANA, first), "the oldest row should have gone first");
    }

    @Test
    @DisplayName("clear empties everything")
    void clear() {
        ShinyLedger ledger = new ShinyLedger();
        ledger.claim(ANA, PIKACHU, 0, ONCE);
        ledger.claim(BEA, EEVEE, 0, ONCE);
        ledger.clear();
        assertEquals(0, ledger.size());
    }
}
