package es.boffmedia.teras.economy;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.net.SmartRotomService;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The player-balance cache in front of <b>starbank</b>, which is the source of truth.
 *
 * <p>Replaces the 1.16.5 {@code Wungill.datosUsuario} map + {@code FileHelper} user files. The cache
 * is not optional: Pixelmon's {@code BankAccount#getBalance} is synchronous, and starbank is a network
 * hop, so a balance must already be in memory by the time the game asks.</p>
 *
 * <p>Deliberately free of Pixelmon imports — {@link TerasBankAccount} adapts this to Pixelmon's API,
 * so a server without Pixelmon can still load and use the economy.</p>
 *
 * <h2>Unknown is not zero</h2>
 * A player whose balance has not been loaded is <b>unknown</b>, not broke. Every read fails closed:
 * {@link #has} is {@code false} and {@link #withdraw} refuses, rather than letting someone spend
 * against a balance we never confirmed and then writing that fiction back to starbank. The 1.16.5 code
 * had no such state — {@code getBalance} did {@code datosUsuario.get(uuid).getDinero()} and threw a
 * {@link NullPointerException} if the player wasn't loaded.
 *
 * <p>This means a starbank outage leaves players unable to spend. That is the intended trade: refusing
 * a purchase is recoverable, corrupting the ledger is not.</p>
 */
public final class EconomyStore {
    private EconomyStore() {}

    /** Loaded balances. Absence means "unknown", which is distinct from a loaded zero. */
    private static final Map<UUID, BigDecimal> BALANCES = new ConcurrentHashMap<>();

    /** True once this player's balance has been read from starbank and may be spent against. */
    public static boolean isLoaded(UUID playerId) {
        return playerId != null && BALANCES.containsKey(playerId);
    }

    /**
     * The cached balance, or {@link BigDecimal#ZERO} when unknown.
     *
     * <p>Zero is the only answer available to a synchronous caller that must return a number, so it is
     * <b>not</b> a safe basis for a spend decision — use {@link #has}, which distinguishes unknown from
     * broke.</p>
     */
    public static BigDecimal get(UUID playerId) {
        BigDecimal balance = playerId == null ? null : BALANCES.get(playerId);
        return balance == null ? BigDecimal.ZERO : balance;
    }

    /** True only when the balance is <b>known</b> and covers {@code amount}. Unknown is false. */
    public static boolean has(UUID playerId, BigDecimal amount) {
        if (playerId == null || amount == null) {
            return false;
        }
        BigDecimal balance = BALANCES.get(playerId);
        return balance != null && balance.compareTo(amount) >= 0;
    }

    /**
     * Loads this player's balance from starbank off-thread. Safe to call on the server thread; the
     * cache is populated once the fetch lands. A failed fetch leaves the player unknown (see the class
     * note) rather than defaulting them to zero.
     */
    public static void load(UUID playerId) {
        if (playerId == null) {
            return;
        }
        Teras.EXECUTOR.execute(() -> {
            BigDecimal balance = SmartRotomService.fetchBalance(playerId);
            if (balance == null) {
                Teras.LOGGER.error("Could not load the starbank balance for {} — their balance stays "
                        + "unknown and they will be unable to spend until it loads. Retry with "
                        + "/teras economia recargar once starbank is reachable.", playerId);
                return;
            }
            BALANCES.put(playerId, balance);
            Teras.LOGGER.info("Loaded starbank balance for {}: {}", playerId, balance);
        });
    }

    /** Drops the cached balance (logout). starbank keeps the authoritative value. */
    public static void unload(UUID playerId) {
        if (playerId != null) {
            BALANCES.remove(playerId);
        }
    }

    /**
     * Applies a balance starbank told us about (an out-of-game purchase, an admin edit). Does not
     * write back — this <i>is</i> the write coming in.
     */
    public static void accept(UUID playerId, BigDecimal balance) {
        if (playerId == null || balance == null) {
            return;
        }
        BALANCES.put(playerId, balance);
    }

    /**
     * Credits {@code amount} and writes through to starbank. Refuses when the balance is unknown: the
     * local sum would be computed from a fiction.
     */
    public static boolean deposit(UUID playerId, BigDecimal amount) {
        if (!isPositive(amount) || !isLoaded(playerId)) {
            return false;
        }
        BALANCES.merge(playerId, amount, BigDecimal::add);
        SmartRotomService.deposit(playerId, amount);
        return true;
    }

    /**
     * Debits {@code amount} and writes through to starbank; {@code false} (and no mutation) when the
     * balance is unknown or insufficient.
     *
     * <p>The insufficient-funds check is a <b>fix</b>, not a port: 1.16.5 {@code WungillEconomy.withdraw}
     * subtracted unconditionally and always reported {@code SUCCESS}, so {@code PixelBank#take} told
     * Pixelmon every purchase succeeded and balances could go negative.</p>
     */
    public static boolean withdraw(UUID playerId, BigDecimal amount) {
        if (!isPositive(amount) || !has(playerId, amount)) {
            return false;
        }
        BALANCES.computeIfPresent(playerId, (id, balance) -> balance.subtract(amount));
        SmartRotomService.withdraw(playerId, amount);
        return true;
    }

    /** Sets the balance outright (admin/backend correction) and writes through. */
    public static boolean set(UUID playerId, BigDecimal amount) {
        if (playerId == null || amount == null || amount.signum() < 0) {
            return false;
        }
        BALANCES.put(playerId, amount);
        SmartRotomService.setBalance(playerId, amount);
        return true;
    }

    static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.signum() > 0;
    }
}
