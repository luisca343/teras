package es.boffmedia.teras.economy;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.net.SmartRotomService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The player-balance cache in front of <b>starbank</b>, which is the source of truth.
 *
 * <p>The cache is not optional: Pixelmon's {@code BankAccount#getBalance} is synchronous and starbank
 * is a network hop, so a balance must already be in memory when the game asks. Kept free of engine
 * imports so a Cobblemon (or engine-less) server can still load the economy;
 * {@code economy.pixelmon.TerasBankAccount} adapts it to Pixelmon.</p>
 *
 * <p>Unknown is not zero: a player whose balance has not loaded is unknown, and every read fails closed
 * ({@link #has} is {@code false}, {@link #withdraw} refuses) rather than spending against an
 * unconfirmed balance. A starbank outage therefore blocks spending — refusing a purchase is
 * recoverable, corrupting the ledger is not.</p>
 */
public final class EconomyStore {
    private EconomyStore() {}

    /** Loaded balances. Absence means "unknown", which is distinct from a loaded zero. */
    private static final Map<UUID, BigDecimal> BALANCES = new ConcurrentHashMap<>();

    /**
     * Single-thread FIFO lane for every starbank write this class and the shop sync originate. Ordering
     * matters: the funnel writes absolute balances ({@code /set-balance}) and the shop writes deltas
     * ({@code /shop}); a set must not overtake a delta on the wire or the delta applies twice.
     * {@link Teras#EXECUTOR} is a pool and gives no such guarantee.
     */
    private static final ExecutorService SYNC_LANE = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Teras-Starbank-Sync");
        t.setDaemon(true);
        return t;
    });

    /**
     * Players whose next deposit/withdraw must not be mirrored to starbank because a specific reporter
     * owns that write ({@code PixelmonShopSync} arms it and posts {@code /shop} itself). One-shot: the
     * single mutation a shop transaction performs consumes it.
     */
    private static final Set<UUID> SKIP_NEXT_SYNC = ConcurrentHashMap.newKeySet();

    /** Ledger memos for funnel-synced changes; shown as-is in the starbank web history. */
    private static final String CONCEPT_INCOME = "[JUEGO] Ingreso en partida";
    private static final String CONCEPT_EXPENSE = "[JUEGO] Pago en partida";

    /**
     * How a funnel change reaches starbank; replaceable so unit tests can observe sync decisions
     * without a network. The default enqueues a {@code /set-balance} on {@link #SYNC_LANE}.
     */
    private static volatile SyncDispatcher syncDispatcher = EconomyStore::dispatchToBackend;

    @FunctionalInterface
    interface SyncDispatcher {
        void sync(UUID playerId, String concept, BigDecimal fallbackTarget);
    }

    static void setSyncDispatcherForTests(SyncDispatcher dispatcher) {
        syncDispatcher = dispatcher == null ? EconomyStore::dispatchToBackend : dispatcher;
    }

    /**
     * Notified with a player's uuid on every balance change so an engine layer can refresh the in-game
     * display — Pixelmon caches the shown PokéDollar count on the client and only updates it when a
     * balance packet is sent, so a cache change is otherwise invisible. The Pixelmon hook registers the
     * actual push ({@code economy.pixelmon.PixelmonEconomyHook}).
     */
    private static final List<Consumer<UUID>> CHANGE_LISTENERS = new CopyOnWriteArrayList<>();

    /** Registers a balance-change listener; typically called once, when an engine bank is installed. */
    public static void addChangeListener(Consumer<UUID> listener) {
        if (listener != null) {
            CHANGE_LISTENERS.add(listener);
        }
    }

    /** Notifies listeners a balance changed. A listener must not throw; one that does is logged, not fatal. */
    private static void fireChanged(UUID playerId) {
        for (Consumer<UUID> listener : CHANGE_LISTENERS) {
            try {
                listener.accept(playerId);
            } catch (RuntimeException e) {
                Teras.LOGGER.warn("Economy change listener failed for {}: {}", playerId, e.toString());
            }
        }
    }

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
            fireChanged(playerId);
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
        fireChanged(playerId);
    }

    /**
     * Credits {@code amount} in the cache and mirrors it to starbank; refuses when the balance is
     * unknown (the sum would be computed from a fiction). The generic funnel — every engine credit
     * without its own backend channel (payouts, {@code /givemoney}, {@code /transfer}) lands here and
     * syncs a {@code /set-balance}. Flows with their own channel opt out via {@link #skipNextSync} or
     * {@link #mirrorDeposit}.
     */
    public static boolean deposit(UUID playerId, BigDecimal amount) {
        if (!isPositive(amount) || !isLoaded(playerId)) {
            return false;
        }
        BigDecimal newBalance = BALANCES.merge(playerId, amount, BigDecimal::add);
        fireChanged(playerId);
        if (!SKIP_NEXT_SYNC.remove(playerId)) {
            syncDispatcher.sync(playerId, CONCEPT_INCOME, newBalance);
        }
        return true;
    }

    /**
     * Credits {@code amount} in the cache without the starbank mirror — for changes the backend
     * originated and already ledgered (mirroring back would double-count).
     */
    public static boolean mirrorDeposit(UUID playerId, BigDecimal amount) {
        if (!isPositive(amount) || !isLoaded(playerId)) {
            return false;
        }
        BALANCES.merge(playerId, amount, BigDecimal::add);
        fireChanged(playerId);
        return true;
    }

    /**
     * Debits {@code amount} in the cache and mirrors it to starbank (see {@link #deposit}); {@code false}
     * with no mutation when the balance is unknown or insufficient.
     */
    public static boolean withdraw(UUID playerId, BigDecimal amount) {
        if (!isPositive(amount) || !has(playerId, amount)) {
            return false;
        }
        BigDecimal newBalance = BALANCES.computeIfPresent(playerId, (id, balance) -> balance.subtract(amount));
        fireChanged(playerId);
        if (!SKIP_NEXT_SYNC.remove(playerId)) {
            syncDispatcher.sync(playerId, CONCEPT_EXPENSE, newBalance);
        }
        return true;
    }

    /**
     * Arms the one-shot skip flag (see {@link #SKIP_NEXT_SYNC}). Call immediately before a bank
     * mutation whose backend write the caller performs itself.
     */
    public static void skipNextSync(UUID playerId) {
        if (playerId != null) {
            SKIP_NEXT_SYNC.add(playerId);
        }
    }

    /** Disarms {@link #skipNextSync} — call once the owned flow has finished (or was aborted). */
    public static void clearSkipNextSync(UUID playerId) {
        if (playerId != null) {
            SKIP_NEXT_SYNC.remove(playerId);
        }
    }

    /**
     * Requests a funnel sync with a caller-supplied memo — the escape hatch for an owned flow that
     * mutated the cache but could not complete its own backend write.
     */
    public static void requestSync(UUID playerId, String concept) {
        if (playerId != null && isLoaded(playerId)) {
            syncDispatcher.sync(playerId, concept, get(playerId));
        }
    }

    /**
     * Runs {@code task} on the ordered starbank write lane, so a set enqueued after a delta can never
     * overtake it (see {@link #SYNC_LANE}).
     */
    public static void runOrdered(Runnable task) {
        SYNC_LANE.execute(task);
    }

    /**
     * Default dispatcher: posts {@code /set-balance} from {@link #SYNC_LANE}. The target is re-read at
     * send time — an owned backend flow may have moved the cache between enqueue and send, and a set
     * from a stale snapshot would undo it. The enqueue-time value only serves a player who logged out
     * meanwhile.
     */
    private static void dispatchToBackend(UUID playerId, String concept, BigDecimal fallbackTarget) {
        SYNC_LANE.execute(() -> {
            BigDecimal target = isLoaded(playerId) ? get(playerId) : fallbackTarget;
            if (!SmartRotomService.setBalance(playerId, target, concept)) {
                Teras.LOGGER.warn("Starbank sync failed for {}: '{}' target {} — the ledger is missing "
                        + "this change until the next successful sync or /set-balance", playerId, concept, target);
            }
        });
    }

    /**
     * Sets the balance outright (admin/backend correction). Unlike {@link #deposit}/{@link #withdraw}
     * this writes through — the backend's absolute-set route diffs and ledgers the adjustment itself.
     * Cache is set optimistically; the next {@link #load} reconciles if the backend rejected it.
     */
    public static boolean set(UUID playerId, BigDecimal amount) {
        if (playerId == null || amount == null || amount.signum() < 0) {
            return false;
        }
        BALANCES.put(playerId, amount);
        fireChanged(playerId);
        SYNC_LANE.execute(() -> SmartRotomService.setBalance(playerId, amount, null));
        return true;
    }

    static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.signum() > 0;
    }
}
