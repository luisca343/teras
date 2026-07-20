package es.boffmedia.teras.plot;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.plot.model.PlotOwnership;
import es.boffmedia.teras.plot.model.PlotTransaction;
import es.boffmedia.teras.util.TerasConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The read side of plot ownership: an immutable name→ownership snapshot republished after every
 * mutation, exactly {@link es.boffmedia.teras.region.RegionStore}'s {@code view} +
 * {@code generation} pattern. {@link PlotDatabase} is the durable record and the write path; the
 * block-event hot path reads only from here, because resolving a build attempt must never wait on
 * a disk query.
 *
 * <p>Mutations are server-thread only (commands). {@link #all()} is safe from any thread.</p>
 *
 * <p><b>Failure posture.</b> If the database will not open, the store falls back to
 * {@link PlotSnapshotCache} — the last ownership it successfully read — and keeps enforcing from
 * that, while refusing every mutation. Stale protection is the right failure: nothing new is
 * granted that is not durably recorded (PLOTS.md §5), and plots do not silently stop being
 * protected because MySQL was restarting. Only when there is no cache either do plots go
 * unenforced, and then loudly, because with no ownership data at all there is no way to tell a
 * plot from a town.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class PlotStore {
    private PlotStore() {}

    private static PlotDatabase database;
    private static volatile Map<String, PlotOwnership> view = Map.of();
    private static volatile long generation = 0;
    private static volatile boolean servingFromCache = false;

    private static Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve("teras");
    }

    /** Only used when SQLite is the configured backend; MySQL keeps nothing local but the cache. */
    private static Path sqlitePath() {
        return directory().resolve("teras.db");
    }

    private static Path cachePath() {
        return directory().resolve("plots-cache.json");
    }

    /** The current immutable region-name→ownership snapshot. Safe to read from any thread. */
    public static Map<String, PlotOwnership> all() {
        return view;
    }

    /** Ownership of {@code regionName}, or {@code null} if that region is not a plot. */
    public static PlotOwnership get(String regionName) {
        return view.get(regionName);
    }

    /** Whether {@code regionName} is a plot at all — i.e. whether it has an ownership row. */
    public static boolean isPlot(String regionName) {
        return view.containsKey(regionName);
    }

    /** Bumped on every mutation/reload, for consumers that cache derived views. */
    public static long generation() {
        return generation;
    }

    /** Whether the durable store is available; {@code false} means every mutation will refuse. */
    public static boolean isAvailable() {
        return database != null;
    }

    /**
     * Whether the snapshot came from {@link PlotSnapshotCache} rather than the database — i.e.
     * plots are still enforced, but from possibly stale ownership and nothing can be changed.
     */
    public static boolean isServingFromCache() {
        return servingFromCache;
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        open();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        close();
    }

    /** Package-visible for tests and for {@link #onServerStarting}. */
    static synchronized void open() {
        close();
        TerasConfig.SqlSettings settings = TerasConfig.sql();
        try {
            database = new PlotDatabase(
                    es.boffmedia.teras.plot.sql.TerasDatabase.ensureOpen(sqlitePath()));
            servingFromCache = false;
            republish();
            Teras.LOGGER.info("PlotStore: loaded {} plots from {}", view.size(), settings.describe());
        } catch (SQLException e) {
            database = null;
            fallBackToCache(settings, e);
        }
    }

    /**
     * Keeps enforcing from the last snapshot we managed to read. Deliberately loud: running on
     * stale ownership is a state an admin has to know about, because no plot can change hands
     * until the database is back.
     */
    private static void fallBackToCache(TerasConfig.SqlSettings settings, SQLException cause) {
        Map<String, PlotOwnership> cached = PlotSnapshotCache.read(cachePath());
        view = cached;
        generation++;
        servingFromCache = !cached.isEmpty();
        if (servingFromCache) {
            Teras.LOGGER.error("PlotStore: cannot reach the plot database ({}) — falling back to the "
                            + "local cache of {} plots. They are still protected, but ownership may be "
                            + "stale and NOTHING can be sold or granted until this is fixed. Cause: {}",
                    settings.describe(), cached.size(), redact(cause));
        } else {
            Teras.LOGGER.error("PlotStore: cannot reach the plot database ({}) and there is no local "
                            + "cache — plots are NOT enforced and none can be sold or granted until "
                            + "this is fixed. Cause: {}",
                    settings.describe(), redact(cause));
        }
    }

    static synchronized void close() {
        if (database != null) {
            database.close();
            database = null;
            es.boffmedia.teras.plot.sql.TerasDatabase.closeShared();
        }
        view = Map.of();
        servingFromCache = false;
        generation++;
    }

    /** Lists a region for sale, making it a plot. */
    public static synchronized Result register(String regionName, String dimension) {
        return mutate("register " + regionName, () -> {
            database.register(regionName, dimension);
            return true;
        });
    }

    /** Stops a region being a plot. The ledger is kept; see {@link PlotDatabase#unregister}. */
    public static synchronized Result unregister(String regionName) {
        return mutate("unregister " + regionName, () -> database.unregister(regionName));
    }

    /** Sets or (with a null {@code owner}) clears the owner, writing the matching ledger row. */
    public static synchronized Result setOwner(String regionName, String dimension, UUID owner,
                                               Long expiresAt, PlotTransaction.Kind kind,
                                               UUID seller, long price) {
        return mutate("set owner of " + regionName, () -> {
            database.setOwner(regionName, dimension, owner, expiresAt, kind, seller, price,
                    System.currentTimeMillis());
            return true;
        });
    }

    public static synchronized Result addMember(String regionName, UUID player, String addedBy) {
        return mutate("add member to " + regionName, () -> {
            database.addMember(regionName, player, addedBy, System.currentTimeMillis());
            return true;
        });
    }

    public static synchronized Result removeMember(String regionName, UUID player) {
        return mutate("remove member from " + regionName,
                () -> database.removeMember(regionName, player));
    }

    /** The ledger for one plot, newest first; empty when the database is unavailable. */
    public static synchronized List<PlotTransaction> transactionsFor(String regionName) {
        if (database == null) return List.of();
        try {
            return database.transactionsFor(regionName);
        } catch (SQLException e) {
            Teras.LOGGER.warn("PlotStore: failed to read ledger for '{}': {}", regionName, e.toString());
            return List.of();
        }
    }

    /** Re-reads every plot from the database and republishes the snapshot. */
    public static synchronized void reload() {
        if (database == null) {
            open();
            return;
        }
        republish();
    }

    /**
     * What a mutation did. {@link #UNCHANGED} has to be distinguishable from {@link #FAILED}, or
     * removing a player who was never a member reports the same thing as a database error — the
     * caller would tell them "done" either way.
     */
    public enum Result {
        FAILED,
        UNCHANGED,
        CHANGED;

        public boolean ok() {
            return this != FAILED;
        }
    }

    /** Returns whether the write changed anything; {@code false} also covers "no such row". */
    private interface Write {
        boolean run() throws SQLException;
    }

    /**
     * Runs a durable write and republishes, or refuses. A failed write must not move the snapshot:
     * the in-memory view is only ever allowed to reflect what is already committed to disk.
     */
    private static Result mutate(String description, Write write) {
        if (database == null) {
            Teras.LOGGER.warn("PlotStore: refusing to {} — database unavailable", description);
            return Result.FAILED;
        }
        try {
            boolean changed = write.run();
            republish();
            return changed ? Result.CHANGED : Result.UNCHANGED;
        } catch (SQLException e) {
            Teras.LOGGER.error("PlotStore: failed to {}: {}", description, redact(e));
            return Result.FAILED;
        }
    }

    /**
     * Republishes the snapshot and mirrors it to disk. A read that fails mid-session leaves the
     * previous snapshot in place rather than emptying it — the same reasoning as the startup
     * fallback, since dropping to zero plots would unprotect every one of them.
     */
    private static void republish() {
        try {
            Map<String, PlotOwnership> loaded = Map.copyOf(database.loadAll());
            view = loaded;
            generation++;
            servingFromCache = false;
            PlotSnapshotCache.write(cachePath(), loaded);
        } catch (SQLException e) {
            servingFromCache = true;
            Teras.LOGGER.error("PlotStore: failed to reload plots, keeping the {} already in memory "
                    + "(ownership may now be stale): {}", view.size(), redact(e));
        }
    }

    /**
     * A JDBC failure message can carry the connection url, and a misconfigured dsn can carry
     * credentials in its query string. Nothing here is worth leaking a password into a log file
     * that gets pasted into a bug report.
     */
    private static String redact(SQLException e) {
        String message = e.toString();
        return message.replaceAll("(?i)(password|pwd)=([^&\\s;\"']+)", "$1=***");
    }
}
