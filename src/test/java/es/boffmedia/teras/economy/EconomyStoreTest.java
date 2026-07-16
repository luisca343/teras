package es.boffmedia.teras.economy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the cache's <b>refusal</b> rules — the ones that decide whether a player may spend.
 *
 * <p>Scope note: only paths that refuse are covered, because they short-circuit before the
 * write-through and so touch no network. The success paths of {@code deposit}/{@code withdraw}/
 * {@code set} POST to starbank via a real socket, so they are not unit-testable as written and are
 * unverified — see WUNGILL_MIGRATION.md.</p>
 */
class EconomyStoreTest {

    private final UUID player = UUID.randomUUID();

    @AfterEach
    void clearCache() {
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
}
