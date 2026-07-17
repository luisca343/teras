package es.boffmedia.teras.economy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the cache's <b>refusal</b> rules — the ones that decide whether a player may spend — and the
 * write-through funnel's sync decisions (which mutations mirror to starbank, and which are owned by a
 * specific channel and must not).
 *
 * <p>Scope note: the network leg is not unit-tested; a test dispatcher captures what <i>would</i> be
 * synced. {@code set} dispatches its backend write on the internal lane; only its refusal path is
 * covered here.</p>
 */
class EconomyStoreTest {

    private final UUID player = UUID.randomUUID();

    /** One entry per funnel sync the store requested: "concept:fallbackTarget". */
    private final List<String> syncs = new ArrayList<>();

    @BeforeEach
    void captureSyncs() {
        EconomyStore.setSyncDispatcherForTests((id, concept, fallback) -> {
            if (player.equals(id)) {
                syncs.add(concept + ":" + fallback);
            }
        });
    }

    @AfterEach
    void clearCache() {
        EconomyStore.setSyncDispatcherForTests(null);
        EconomyStore.clearSkipNextSync(player);
        EconomyStore.unload(player);
    }

    @Test
    void unknownPlayerIsNotLoaded() {
        assertFalse(EconomyStore.isLoaded(player));
    }

    @Test
    void unknownBalanceReadsAsZeroButCannotBeSpent() {
        // The distinction the whole design rests on: get() must return *a* number for Pixelmon's
        // synchronous API, but "unknown" must never be mistaken for "has zero" when deciding a spend.
        assertEquals(BigDecimal.ZERO, EconomyStore.get(player));
        assertFalse(EconomyStore.has(player, BigDecimal.ONE));
        assertFalse(EconomyStore.has(player, BigDecimal.ZERO));
    }

    @Test
    void loadedZeroBalanceCoversAZeroCost() {
        EconomyStore.accept(player, BigDecimal.ZERO);

        assertTrue(EconomyStore.isLoaded(player));
        assertTrue(EconomyStore.has(player, BigDecimal.ZERO));
        assertFalse(EconomyStore.has(player, BigDecimal.ONE));
    }

    @Test
    void acceptPopulatesTheBalanceWithoutWritingBack() {
        EconomyStore.accept(player, new BigDecimal("250"));

        assertTrue(EconomyStore.isLoaded(player));
        assertEquals(new BigDecimal("250"), EconomyStore.get(player));
        assertTrue(EconomyStore.has(player, new BigDecimal("250")));
    }

    @Test
    void withdrawRefusesWhenBalanceIsUnknown() {
        assertFalse(EconomyStore.withdraw(player, BigDecimal.ONE));
        assertEquals(BigDecimal.ZERO, EconomyStore.get(player));
    }

    @Test
    void withdrawRefusesAndDoesNotMutateWhenFundsAreInsufficient() {
        // The 1.16.5 bug this fixes: WungillEconomy.withdraw subtracted unconditionally and always
        // returned SUCCESS, so PixelBank#take reported every purchase as paid and balances went
        // negative.
        EconomyStore.accept(player, new BigDecimal("10"));

        assertFalse(EconomyStore.withdraw(player, new BigDecimal("11")));
        assertEquals(new BigDecimal("10"), EconomyStore.get(player));
    }

    @Test
    void depositRefusesWhenBalanceIsUnknown() {
        assertFalse(EconomyStore.deposit(player, BigDecimal.ONE));
        assertFalse(EconomyStore.isLoaded(player));
    }

    @Test
    void depositCreditsTheCacheWhenLoaded() {
        EconomyStore.accept(player, new BigDecimal("100"));

        assertTrue(EconomyStore.deposit(player, new BigDecimal("50")));
        assertEquals(new BigDecimal("150"), EconomyStore.get(player));
    }

    @Test
    void withdrawDebitsTheCacheWhenFundsCover() {
        EconomyStore.accept(player, new BigDecimal("100"));

        assertTrue(EconomyStore.withdraw(player, new BigDecimal("30")));
        assertEquals(new BigDecimal("70"), EconomyStore.get(player));
    }

    @Test
    void nonPositiveAmountsAreRejected() {
        EconomyStore.accept(player, new BigDecimal("100"));

        assertFalse(EconomyStore.deposit(player, BigDecimal.ZERO));
        assertFalse(EconomyStore.deposit(player, new BigDecimal("-5")));
        assertFalse(EconomyStore.withdraw(player, BigDecimal.ZERO));
        assertFalse(EconomyStore.withdraw(player, new BigDecimal("-5")));
        assertEquals(new BigDecimal("100"), EconomyStore.get(player));
    }

    @Test
    void setRejectsNegativeBalances() {
        assertFalse(EconomyStore.set(player, new BigDecimal("-1")));
        assertFalse(EconomyStore.isLoaded(player));
    }

    @Test
    void unloadMakesTheBalanceUnknownAgain() {
        EconomyStore.accept(player, new BigDecimal("100"));
        EconomyStore.unload(player);

        assertFalse(EconomyStore.isLoaded(player));
        assertFalse(EconomyStore.has(player, BigDecimal.ONE));
    }

    @Test
    void nullPlayerIsHandledEverywhere() {
        assertFalse(EconomyStore.isLoaded(null));
        assertEquals(BigDecimal.ZERO, EconomyStore.get(null));
        assertFalse(EconomyStore.has(null, BigDecimal.ONE));
        assertFalse(EconomyStore.withdraw(null, BigDecimal.ONE));
        assertFalse(EconomyStore.deposit(null, BigDecimal.ONE));
        assertFalse(EconomyStore.set(null, BigDecimal.ONE));
    }

    @Test
    void depositAndWithdrawMirrorToStarbank() {
        EconomyStore.accept(player, new BigDecimal("100"));

        EconomyStore.deposit(player, new BigDecimal("50"));
        EconomyStore.withdraw(player, new BigDecimal("30"));

        assertEquals(2, syncs.size());
        assertEquals("[JUEGO] Ingreso en partida:150", syncs.get(0));
        assertEquals("[JUEGO] Pago en partida:120", syncs.get(1));
    }

    @Test
    void refusedMutationsDoNotSync() {
        EconomyStore.accept(player, new BigDecimal("10"));

        EconomyStore.deposit(player, BigDecimal.ZERO);
        EconomyStore.withdraw(player, new BigDecimal("11"));

        assertTrue(syncs.isEmpty());
    }

    @Test
    void mirrorDepositCreditsTheCacheWithoutSyncing() {
        // The trainer-defeat backend callback: the backend ledgers the credit itself, so mirroring it
        // back would double-count.
        EconomyStore.accept(player, new BigDecimal("100"));

        assertTrue(EconomyStore.mirrorDeposit(player, new BigDecimal("1000")));

        assertEquals(new BigDecimal("1100"), EconomyStore.get(player));
        assertTrue(syncs.isEmpty());
    }

    @Test
    void skipNextSyncSuppressesExactlyOneMutation() {
        // The shop window: Pre arms the flag, the transaction's single take/add consumes it, the /shop
        // route owns that backend write. The mutation after it must sync normally again.
        EconomyStore.accept(player, new BigDecimal("100"));

        EconomyStore.skipNextSync(player);
        EconomyStore.withdraw(player, new BigDecimal("40"));    // the shop take — owned by /shop
        EconomyStore.deposit(player, new BigDecimal("5"));      // unrelated credit — must sync

        assertEquals(1, syncs.size());
        assertEquals("[JUEGO] Ingreso en partida:65", syncs.get(0));
    }

    @Test
    void clearSkipNextSyncDisarmsALeakedFlag() {
        // A cancelled Pre never reaches its mutation; Post (or the next Pre) must be able to disarm so
        // the player is not muted forever.
        EconomyStore.accept(player, new BigDecimal("100"));

        EconomyStore.skipNextSync(player);
        EconomyStore.clearSkipNextSync(player);
        EconomyStore.deposit(player, new BigDecimal("5"));

        assertEquals(1, syncs.size());
    }

    @Test
    void requestSyncUsesTheCallerConceptAndCurrentBalance() {
        EconomyStore.accept(player, new BigDecimal("77"));

        EconomyStore.requestSync(player, "[JUEGO] Operación de tienda (sin precio)");

        assertEquals(1, syncs.size());
        assertEquals("[JUEGO] Operación de tienda (sin precio):77", syncs.get(0));
    }

    /**
     * Every balance change notifies listeners — this is what drives the in-game display refresh
     * (Pixelmon shows a stale/0 counter otherwise). A refused mutation changes nothing, so it must not
     * fire. The listener filters on this test's uuid, so the (unremovable) registration is inert for
     * every other test's random player.
     */
    @Test
    void balanceChangesNotifyListenersOnlyWhenSomethingChanged() {
        List<UUID> notified = new ArrayList<>();
        EconomyStore.addChangeListener(id -> {
            if (player.equals(id)) {
                notified.add(id);
            }
        });

        EconomyStore.accept(player, new BigDecimal("100"));     // fires
        EconomyStore.deposit(player, new BigDecimal("50"));     // fires
        EconomyStore.withdraw(player, new BigDecimal("30"));    // fires

        EconomyStore.deposit(player, BigDecimal.ZERO);          // refused: non-positive
        EconomyStore.withdraw(player, new BigDecimal("9999"));  // refused: insufficient

        assertEquals(3, notified.size());
    }
}
