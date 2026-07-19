package es.boffmedia.teras.plot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.plot.model.PlotOwnership;

import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * A local mirror of the last ownership snapshot the mod successfully read, so that losing the
 * database does not mean losing protection.
 *
 * <p>This exists because of MySQL. With a local SQLite file, "database unavailable" meant disk
 * failure — near enough to never that starting with no plot data was an acceptable posture. A
 * shared MySQL is unreachable for ordinary reasons: a network blip, a restart, a full connection
 * pool. Without this cache, any of those would start the server with zero known plots, and since
 * a plot is <em>defined</em> as a region with an ownership row, every plot would silently stop
 * being protected — an open invitation on a server where they hold player builds.</p>
 *
 * <p>Falling back to slightly stale ownership is the right failure: the worst case is that a plot
 * sold in the last few minutes protects its previous owner until the database returns. Mutations
 * are refused entirely while running from cache, so nothing new is granted that is not durably
 * recorded.</p>
 *
 * <p>Written with the same temp-file + {@code ATOMIC_MOVE} discipline as
 * {@link es.boffmedia.teras.region.RegionStore}: a cache truncated by a crash would be worse than
 * no cache at all.</p>
 */
final class PlotSnapshotCache {
    private PlotSnapshotCache() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, PlotOwnership>>() {}.getType();

    /** Overwrites the cache with {@code plots}. Failures are logged, never fatal. */
    static void write(Path file, Map<String, PlotOwnership> plots) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(plots, TYPE));
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            Teras.LOGGER.warn("PlotStore: could not update the local ownership cache: {}", e.toString());
        }
    }

    /**
     * The cached snapshot, or an empty map when there is none to read. Entries that fail to parse
     * are dropped individually rather than taking the whole cache down — a partially readable
     * cache still protects the plots it does describe.
     */
    static Map<String, PlotOwnership> read(Path file) {
        if (!Files.exists(file)) return Map.of();
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, PlotOwnership> cached = GSON.fromJson(reader, TYPE);
            if (cached == null) return Map.of();
            cached.entrySet().removeIf(entry ->
                    entry.getValue() == null || entry.getValue().regionName() == null);
            return Map.copyOf(cached);
        } catch (Exception e) {
            Teras.LOGGER.warn("PlotStore: could not read the local ownership cache: {}", e.toString());
            return Map.of();
        }
    }
}
