package es.boffmedia.teras.region;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.plot.sql.TerasDatabase;
import es.boffmedia.teras.region.model.TerasRegion;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The authoritative region catalog, backed by the {@code region} table and mirrored to
 * {@code config/teras/regions.json} after every change.
 *
 * <p><b>Why the database owns this.</b> The SmartRotom backend shares it, so regions and plots are
 * read the same way instead of one over HTTP and one from a file, and {@code plot.region_name} can
 * finally be a real foreign key rather than a name that hopefully matches something.</p>
 *
 * <p><b>Why the file survives anyway.</b> It keeps {@code git diff} answering "who changed which
 * town", and it is the fallback when the database is unreachable. That fallback matters more here
 * than it does for plots: losing ownership costs ownership, but losing the region catalog costs
 * protection, banners, GPS and the map at once. Hand-edits to it are <b>overwritten</b> on the next
 * mutation — the mirror is an export, not an input, except during the one-time seed import.</p>
 *
 * <p>Mutations are server-thread only (commands). Reads from other threads (the HTTP server) go
 * through {@link #all()}, the immutable snapshot republished after every mutation;
 * {@link #generation()} lets {@link RegionIndex} notice changes without a listener wiring.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class RegionStore {
    private RegionStore() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STORE_TYPE = new TypeToken<Map<String, TerasRegion>>() {}.getType();

    private static RegionDatabase database;
    private static Map<String, TerasRegion> regions = new LinkedHashMap<>();
    private static volatile Map<String, TerasRegion> view = Map.of();
    private static volatile long generation = 0;
    private static volatile boolean servingFromMirror = false;

    private static Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve("teras");
    }

    private static Path mirrorPath() {
        return directory().resolve("regions.json");
    }

    /** The current immutable name→region snapshot. Safe to read from any thread. */
    public static Map<String, TerasRegion> all() {
        return view;
    }

    public static TerasRegion get(String name) {
        return view.get(name);
    }

    public static Set<String> names() {
        return view.keySet();
    }

    /** Bumped on every mutation/reload; {@link RegionIndex} rebuilds when it changes. */
    public static long generation() {
        return generation;
    }

    /** Whether the durable store is available; {@code false} means every mutation will refuse. */
    public static boolean isAvailable() {
        return database != null;
    }

    /**
     * Whether the snapshot came from {@code regions.json} rather than the database — regions are
     * still enforced, but from a possibly stale mirror and nothing can be changed.
     */
    public static boolean isServingFromMirror() {
        return servingFromMirror;
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        open();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        close();
    }

    static synchronized void open() {
        database = null;
        try {
            database = new RegionDatabase(TerasDatabase.ensureOpen(directory().resolve("teras.db")));
            seedFromMirrorIfEmpty();
            republish();
            Teras.LOGGER.info("RegionStore: loaded {} regions from the database", view.size());
        } catch (SQLException e) {
            database = null;
            fallBackToMirror(e);
        }
    }

    static synchronized void close() {
        database = null;
        regions = new LinkedHashMap<>();
        view = Map.of();
        servingFromMirror = false;
        generation++;
    }

    /**
     * One-time import of a pre-database {@code regions.json}. Only runs against an empty table, so
     * it cannot resurrect a region an admin deleted: once the catalog lives in the database, the
     * file is an export.
     */
    private static void seedFromMirrorIfEmpty() throws SQLException {
        if (!database.isEmpty()) return;
        Map<String, TerasRegion> fromFile = readMirror();
        if (fromFile.isEmpty()) return;
        database.replaceAll(fromFile.values());
        Teras.LOGGER.info("RegionStore: imported {} regions from regions.json into the database; "
                + "the file is now a mirror and hand-edits to it will be overwritten", fromFile.size());
    }

    /** Keeps enforcing from the mirror. Loud: no region can change until the database is back. */
    private static void fallBackToMirror(SQLException cause) {
        Map<String, TerasRegion> mirrored = readMirror();
        regions = new LinkedHashMap<>(mirrored);
        view = Map.copyOf(mirrored);
        generation++;
        servingFromMirror = !mirrored.isEmpty();
        if (servingFromMirror) {
            Teras.LOGGER.error("RegionStore: cannot reach the database — falling back to the {} "
                    + "regions in regions.json. Protection, banners and the map still work, but the "
                    + "catalog may be stale and NO region can be changed until this is fixed. "
                    + "Cause: {}", mirrored.size(), redact(cause));
        } else {
            Teras.LOGGER.error("RegionStore: cannot reach the database and regions.json is empty or "
                    + "missing — there are NO regions, so nothing is protected. Cause: {}",
                    redact(cause));
        }
    }

    /** Adds or replaces {@code region}. Returns whether the write reached the database. */
    public static synchronized boolean put(TerasRegion region) {
        if (database == null) {
            Teras.LOGGER.warn("RegionStore: refusing to save '{}' — database unavailable",
                    region.getName());
            return false;
        }
        try {
            database.put(region);
            republish();
            return true;
        } catch (SQLException e) {
            Teras.LOGGER.error("RegionStore: failed to save '{}': {}", region.getName(), redact(e));
            return false;
        }
    }

    /**
     * Removes the region, returning whether it existed. Fails when a plot still references it: the
     * foreign key is {@code RESTRICT}, so deleting the ground under an owner is refused rather
     * than silently orphaning their plot.
     */
    public static synchronized boolean remove(String name) {
        if (database == null) {
            Teras.LOGGER.warn("RegionStore: refusing to delete '{}' — database unavailable", name);
            return false;
        }
        try {
            boolean existed = database.remove(name);
            if (existed) republish();
            return existed;
        } catch (SQLException e) {
            Teras.LOGGER.error("RegionStore: failed to delete '{}': {}", name, redact(e));
            return false;
        }
    }

    /** Whether deleting {@code name} would be refused because a plot still references it. */
    public static boolean isReferencedByPlot(String name) {
        return es.boffmedia.teras.plot.PlotStore.isPlot(name);
    }

    /**
     * Kept for the command surface: writes are already durable when {@link #put} returns, so this
     * only refreshes the on-disk mirror.
     */
    public static synchronized void saveIfDirty() {
        writeMirror();
    }

    /** Re-reads the catalog from the database, reopening it if it had been lost. */
    public static synchronized void reload() {
        if (database == null) {
            open();
            return;
        }
        republish();
    }

    private static void republish() {
        try {
            Map<String, TerasRegion> loaded = database.loadAll();
            regions = new LinkedHashMap<>(loaded);
            view = Map.copyOf(loaded);
            generation++;
            servingFromMirror = false;
            writeMirror();
        } catch (SQLException e) {
            servingFromMirror = true;
            Teras.LOGGER.error("RegionStore: failed to reload regions, keeping the {} already in "
                    + "memory (the catalog may now be stale): {}", view.size(), redact(e));
        }
    }

    // ---- The on-disk mirror ----

    /**
     * Writes the catalog to {@code regions.json} via a temp file and {@code ATOMIC_MOVE}, so a
     * crash mid-write cannot leave a truncated fallback — which would be worse than none, since it
     * is what the server falls back to when the database is gone.
     */
    private static void writeMirror() {
        try {
            Path file = mirrorPath();
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(regions, STORE_TYPE));
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            Teras.LOGGER.warn("RegionStore: could not update regions.json: {}", e.toString());
        }
    }

    /** Reads the mirror, dropping invalid entries individually. */
    private static Map<String, TerasRegion> readMirror() {
        Map<String, TerasRegion> loaded = new LinkedHashMap<>();
        Path file = mirrorPath();
        if (!Files.exists(file)) return loaded;
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, TerasRegion> parsed = GSON.fromJson(reader, STORE_TYPE);
            if (parsed != null) parsed.forEach((key, region) -> accept(loaded, key, region));
        } catch (Exception e) {
            Teras.LOGGER.warn("RegionStore: failed to read regions.json: {}", e.toString());
        }
        return loaded;
    }

    private static void accept(Map<String, TerasRegion> into, String key, TerasRegion region) {
        if (region == null) {
            Teras.LOGGER.warn("RegionStore: skipping null entry '{}'", key);
            return;
        }
        region.normalize();
        String error = region.validationError();
        if (error != null) {
            Teras.LOGGER.warn("RegionStore: skipping region '{}': {}", key, error);
            return;
        }
        if (!key.equals(region.getName())) {
            Teras.LOGGER.warn("RegionStore: skipping region '{}': key does not match name '{}'",
                    key, region.getName());
            return;
        }
        into.put(key, region);
    }

    /** A JDBC message can carry the connection url, and a bad dsn can carry credentials. */
    private static String redact(SQLException e) {
        return e.toString().replaceAll("(?i)(password|pwd)=([^&\\s;\"']+)", "$1=***");
    }
}
