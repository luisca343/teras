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
 * <p>Replaces the 1.16.5 {@code Wungill.datosUsuario} map + {@code FileHelper} user files. The cache
 * is not optional: Pixelmon's {@code BankAccount#getBalance} is synchronous, and starbank is a network
 * hop, so a balance must already be in memory by the time the game asks.</p>
 *
 * <p>Deliberately free of engine imports — {@code economy.pixelmon.TerasBankAccount} adapts this to
 * Pixelmon's API, so a Cobblemon (or engine-less) server can still load and use the economy.</p>
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

    /**
     * Single-thread lane for every starbank <b>write</b> this class (and the shop sync) originates.
     * Ordering is the point, not throughput: the generic funnel writes absolute balances
     * ({@code /set-balance}), while the shop channel writes deltas ({@code /shop}) — if a set computed
     * after a shop delta could overtake it on the wire, the delta would be applied twice. One thread,
     * FIFO, makes "enqueued after" mean "lands after". {@link Teras#EXECUTOR} is a cached <i>pool</i>
     * and gives no such guarantee.
     */
    private static final ExecutorService SYNC_LANE = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Teras-Starbank-Sync");
        t.setDaemon(true);
        return t;
    });

    /**
     * Players whose <b>next</b> deposit/withdraw must not be mirrored to starbank because a specific
     * reporter owns that write ({@code PixelmonShopSync} arms this at {@code ShopEvent.*.Pre} and posts
     * {@code /shop} itself). One-shot: the single bank mutation a shop transaction performs consumes
     * it, and the {@code Post} handler disarms defensively, so a cancelled Pre can at worst swallow one
     * later sync instead of muting the player forever.
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
     * Notified with a player's uuid whenever their balance changes, so an engine layer can refresh the
     * in-game display — Pixelmon caches the shown PokéDollar count on the client and only updates it
     * when a balance packet is sent, so a cache change is otherwise invisible. Engine-free: the Pixelmon
     * hook registers the actual push ({@code economy.pixelmon.PixelmonEconomyHook}). Port of the 1.16.5
     * Wungill {@code EconomyUpdateEvent} → {@code UpdateListener}.
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
     * Credits {@code amount} in the cache and mirrors the change to starbank. Refuses when the balance
     * is unknown: the local sum would be computed from a fiction.
     *
     * <p>This is the generic funnel: every engine credit without its own backend channel lands here —
     * PayDay/Gold Rush payouts, NPC "give money" interactions (trainer winnings), {@code /givemoney},
     * {@code /transfer}. None of them fire a Pixelmon event, so this is the only place they are all
     * visible; the mirror is a {@code /set-balance} with a generic memo (the funnel sees an amount, not
     * a cause). Flows that <i>do</i> have their own channel opt out: the shop listener arms
     * {@link #skipNextSync} and posts {@code /shop}, and the trainer-defeat backend callback uses
     * {@link #mirrorDeposit}.</p>
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
     * Credits {@code amount} in the cache <b>without</b> the starbank mirror — for changes the backend
     * itself originated and already ledgered (the trainer-defeat callback credits us and diffs the
     * returned total; mirroring it back would double-count).
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
     * Debits {@code amount} in the cache and mirrors the change to starbank (see {@link #deposit});
     * {@code false} (and no mutation) when the balance is unknown or insufficient. Generic debits are
     * daycare fees and the take side of {@code /transfer}.
     *
     * <p>The insufficient-funds check is a <b>fix</b>, not a port: 1.16.5 {@code WungillEconomy.withdraw}
     * subtracted unconditionally and always reported {@code SUCCESS}, so {@code PixelBank#take} told
     * Pixelmon every purchase succeeded and balances could go negative.</p>
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
     * Arms the one-shot "this change has its own backend channel" flag (see {@link #SKIP_NEXT_SYNC}).
     * Call immediately before a bank mutation whose backend write the caller performs itself.
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
     * Explicitly requests a funnel sync of this player's balance with a caller-supplied ledger memo —
     * the escape hatch for an owned flow that mutated the cache but could not complete its own backend
     * write (e.g. a shop transaction whose price could not be resolved).
     */
    public static void requestSync(UUID playerId, String concept) {
        if (playerId != null && isLoaded(playerId)) {
            syncDispatcher.sync(playerId, concept, get(playerId));
        }
    }

    /**
     * Runs {@code task} on the ordered starbank write lane. Backend writes that move money and race the
     * funnel's absolute sets ({@code PixelmonShopSync}'s {@code /shop} posts) must go through here, so
     * a set enqueued after a delta can never overtake it.
     */
    public static void runOrdered(Runnable task) {
        SYNC_LANE.execute(task);
    }

    /**
     * The default {@link SyncDispatcher}: posts {@code /set-balance} from {@link #SYNC_LANE}. The
     * target is re-read <b>at send time</b> — between enqueue and send, an owned backend flow (a
     * trainer-defeat callback, a backend push) may have moved the cache, and an absolute set computed
     * from a stale snapshot would undo it. The enqueue-time value only serves a player who logged out
     * meanwhile (their cache entry is gone, but their credit must still land).
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
     * Sets the balance outright (admin/backend correction). Unlike {@link #deposit}/{@link #withdraw},
     * this <i>does</i> write through — the backend has an absolute-set route that diffs and ledgers the
     * adjustment itself, so there is no double-count and no metadata to carry. The write is dispatched
     * off-thread ({@link SmartRotomService#setBalance} blocks); the cache is set optimistically and the
     * next {@link #load} reconciles if the backend rejected it.
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
