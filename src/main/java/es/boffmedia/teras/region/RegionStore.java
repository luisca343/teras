package es.boffmedia.teras.region;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.region.model.TerasRegion;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The authoritative region catalog, backed by {@code config/teras/regions.json} and keyed by
 * region name. Same discipline as {@link es.boffmedia.teras.quests.NpcCatalog}: lazy
 * {@code ensureLoaded()}, a dirty flag with {@link #saveIfDirty()}, lenient per-entry loading.
 *
 * <p>Mutations are server-thread only (commands). Reads from other threads (the HTTP server) go
 * through {@link #all()}, which returns the immutable snapshot republished after every mutation;
 * {@link #generation()} lets {@link RegionIndex} notice changes without a listener wiring.</p>
 */
public final class RegionStore {
    private RegionStore() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STORE_TYPE = new TypeToken<Map<String, TerasRegion>>() {}.getType();

    private static Map<String, TerasRegion> regions;
    private static boolean dirty = false;
    private static volatile Map<String, TerasRegion> view = Map.of();
    private static volatile long generation = 0;

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("teras").resolve("regions.json");
    }

    /** The current immutable name→region snapshot. Safe to read from any thread. */
    public static Map<String, TerasRegion> all() {
        ensureLoaded();
        return view;
    }

    public static TerasRegion get(String name) {
        ensureLoaded();
        return regions.get(name);
    }

    public static Set<String> names() {
        ensureLoaded();
        return view.keySet();
    }

    /** Bumped on every mutation/reload; {@link RegionIndex} rebuilds when it changes. */
    public static long generation() {
        return generation;
    }

    /** Adds or replaces {@code region} and marks the store dirty. */
    public static void put(TerasRegion region) {
        ensureLoaded();
        regions.put(region.getName(), region);
        dirty = true;
        publish();
    }

    /** Removes the region, returning whether it existed. Marks dirty when it did. */
    public static boolean remove(String name) {
        ensureLoaded();
        boolean existed = regions.remove(name) != null;
        if (existed) {
            dirty = true;
            publish();
        }
        return existed;
    }

    public static void saveIfDirty() {
        if (!dirty) return;
        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(regions, STORE_TYPE));
            dirty = false;
            Teras.LOGGER.info("RegionStore: saved {} regions", regions.size());
        } catch (Exception e) {
            Teras.LOGGER.warn("RegionStore: failed to save regions: {}", e.toString());
        }
    }

    /** Discards the in-memory catalog and re-reads the file (admin hand-edits + {@code reload}). */
    public static void reload() {
        regions = null;
        dirty = false;
        ensureLoaded();
    }

    private static void ensureLoaded() {
        if (regions != null) return;
        regions = new LinkedHashMap<>();
        Path file = path();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                Map<String, TerasRegion> loaded = GSON.fromJson(reader, STORE_TYPE);
                if (loaded != null) {
                    loaded.forEach(RegionStore::acceptLoaded);
                }
            } catch (Exception e) {
                Teras.LOGGER.warn("RegionStore: failed to load regions from disk: {}", e.toString());
            }
            Teras.LOGGER.info("RegionStore: loaded {} regions from disk", regions.size());
        }
        publish();
    }

    private static void acceptLoaded(String key, TerasRegion region) {
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
        regions.put(key, region);
    }

    private static void publish() {
        view = Map.copyOf(regions);
        generation++;
    }
}
