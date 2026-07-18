package es.boffmedia.teras.karts.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.karts.model.KartTrack;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The circuit catalog, backed by {@code config/teras/karts/circuitos.json}. Same discipline as
 * {@link es.boffmedia.teras.region.RegionStore}: lazy {@code ensureLoaded()}, a dirty flag with
 * {@link #saveIfDirty()}, lenient per-entry loading, and an immutable snapshot republished after
 * every mutation so off-thread readers (the HTTP server) never see a half-written map.
 *
 * <p><b>Fixes the 1.16.5 bug where tracks were never written back.</b> There, {@code circuitos.json}
 * was read once at startup and the editor's {@code guardar} only put the track in a map with a
 * {@code // Here you would also save to file} comment — every circuit an admin built was lost on
 * restart. Here every mutation marks the store dirty and {@link #saveIfDirty()} runs after each
 * editor command.</p>
 *
 * <p>Mutations are server-thread only (commands).</p>
 */
public final class TrackStore {
    private TrackStore() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Map<String, KartTrack> tracks;
    private static boolean dirty = false;
    private static volatile Map<String, KartTrack> view = Map.of();
    private static volatile long generation = 0;

    private static Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve("teras").resolve("karts");
    }

    private static Path path() {
        return directory().resolve("circuitos.json");
    }

    /** The current immutable name→track snapshot. Safe to read from any thread. */
    public static Map<String, KartTrack> all() {
        ensureLoaded();
        return view;
    }

    public static KartTrack get(String name) {
        ensureLoaded();
        return tracks.get(name);
    }

    public static Set<String> names() {
        ensureLoaded();
        return view.keySet();
    }

    public static boolean exists(String name) {
        ensureLoaded();
        return tracks.containsKey(name);
    }

    /** Bumped on every mutation/reload, so callers can cache derived state and notice changes. */
    public static long generation() {
        return generation;
    }

    /** Adds or replaces a circuit and marks the store dirty. */
    public static void put(KartTrack track) {
        ensureLoaded();
        tracks.put(track.name(), track);
        dirty = true;
        publish();
    }

    /** Records that an already-stored circuit was edited in place, so it gets written back. */
    public static void markDirty() {
        ensureLoaded();
        dirty = true;
        publish();
    }

    public static boolean remove(String name) {
        ensureLoaded();
        boolean existed = tracks.remove(name) != null;
        if (existed) {
            dirty = true;
            publish();
        }
        return existed;
    }

    public static void saveIfDirty() {
        if (!dirty) {
            return;
        }
        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(KartsJson.write(tracks)));
            dirty = false;
            Teras.LOGGER.info("Karts: saved {} circuits", tracks.size());
        } catch (Exception e) {
            Teras.LOGGER.warn("Karts: failed to save circuits: {}", e.toString());
        }
    }

    /** Discards the in-memory catalog and re-reads the file (admin hand-edits + {@code recargar}). */
    public static void reload() {
        tracks = null;
        dirty = false;
        ensureLoaded();
    }

    private static void ensureLoaded() {
        if (tracks != null) {
            return;
        }
        tracks = new LinkedHashMap<>();
        Path file = path();
        if (Files.exists(file)) {
            boolean migrated = false;
            try (Reader reader = Files.newBufferedReader(file)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                migrated = KartsJson.isLegacy(root);
                tracks.putAll(KartsJson.read(root));
            } catch (Exception e) {
                Teras.LOGGER.warn("Karts: failed to load circuits from disk: {}", e.toString());
            }
            Teras.LOGGER.info("Karts: loaded {} circuits from disk", tracks.size());
            if (migrated) {
                backupLegacyFile(file);
                // Rewrite in the current format straight away, so the legacy path runs exactly once.
                dirty = true;
                publish();
                saveIfDirty();
            }
        }
        publish();
    }

    /**
     * Keeps the original 1.16.5 file next to the migrated one. The conversion is one-way and drops
     * information (the track-wide compass direction becomes a per-slot yaw), so the source is worth
     * keeping in case a migration turns out wrong.
     */
    private static void backupLegacyFile(Path file) {
        try {
            Path backup = file.resolveSibling("circuitos.json.legacy.bak");
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            Teras.LOGGER.info("Karts: migrated circuits from the 1.16.5 format; original kept at {}", backup);
        } catch (Exception e) {
            Teras.LOGGER.warn("Karts: could not back up the legacy circuits file: {}", e.toString());
        }
    }

    private static void publish() {
        view = Map.copyOf(tracks);
        generation++;
    }
}
