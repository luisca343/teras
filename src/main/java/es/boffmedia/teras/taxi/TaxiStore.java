package es.boffmedia.teras.taxi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import es.boffmedia.teras.Teras;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The taxi's destinations, in {@code config/teras/taxi.json}.
 *
 * <p>Held as an <b>immutable snapshot</b>, republished on every change. That is what lets
 * {@code GET /taxi/stops} answer straight from an HTTP thread with no hop to the server thread —
 * the same reason {@code GET /regions} can, and it matters here because the web polls this list.</p>
 *
 * <p>There was no taxi in 1.16.5 to port: the stop list lived in the Wungill plugin. Authoring is
 * therefore modelled on regions and kart circuits — an admin stands where the stop belongs and names
 * it, rather than hand-editing coordinates.</p>
 */
public final class TaxiStore {
    private TaxiStore() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Published snapshot; replaced wholesale, never mutated in place. */
    private static volatile List<TaxiStop> stops = List.of();
    private static volatile boolean loaded;

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("teras").resolve("taxi.json");
    }

    /** Every stop, in creation order. Safe to read from any thread. */
    public static List<TaxiStop> all() {
        ensureLoaded();
        return stops;
    }

    /** The stop with this id, or {@code null}. Id matching is normalized, so the web's case cannot miss. */
    public static TaxiStop find(String id) {
        String wanted = TaxiStop.normalizeId(id);
        for (TaxiStop stop : all()) {
            if (stop.id().equals(wanted)) {
                return stop;
            }
        }
        return null;
    }

    /**
     * Adds or replaces a stop. Returns false if an existing stop of that id was overwritten, so the
     * command can tell the admin which of the two things they just did.
     */
    public static synchronized boolean put(TaxiStop stop) {
        ensureLoaded();
        Map<String, TaxiStop> byId = new LinkedHashMap<>();
        for (TaxiStop existing : stops) {
            byId.put(existing.id(), existing);
        }
        boolean isNew = byId.put(stop.id(), stop) == null;
        stops = List.copyOf(byId.values());
        save();
        return isNew;
    }

    /** Removes a stop. Returns false if there was nothing to remove. */
    public static synchronized boolean remove(String id) {
        ensureLoaded();
        String wanted = TaxiStop.normalizeId(id);
        List<TaxiStop> kept = new ArrayList<>(stops.size());
        boolean found = false;
        for (TaxiStop stop : stops) {
            if (stop.id().equals(wanted)) {
                found = true;
            } else {
                kept.add(stop);
            }
        }
        if (found) {
            stops = List.copyOf(kept);
            save();
        }
        return found;
    }

    /** Re-reads the file, discarding the snapshot. For {@code /teras taxi recargar}. */
    public static synchronized void reload() {
        loaded = false;
        ensureLoaded();
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (TaxiStore.class) {
            if (loaded) {
                return;
            }
            stops = read();
            loaded = true;
        }
    }

    private static List<TaxiStop> read() {
        Path path = path();
        if (!Files.exists(path)) {
            return List.of();
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            List<TaxiStop> read = GSON.fromJson(reader, new TypeToken<List<TaxiStop>>() {}.getType());
            if (read == null) {
                return List.of();
            }
            // A hand-edited file is the expected way to bulk-move stops, so a bad row is dropped with
            // a name rather than taking the whole list down with it.
            List<TaxiStop> valid = new ArrayList<>(read.size());
            for (TaxiStop stop : read) {
                String problem = stop == null ? "entrada vacía" : TaxiStop.idProblem(stop.id());
                if (problem != null) {
                    Teras.LOGGER.warn("Taxi: ignorando una parada de config/teras/taxi.json ({})", problem);
                } else {
                    valid.add(stop);
                }
            }
            return List.copyOf(valid);
        } catch (Exception e) {
            // Keeping the previous snapshot would be worse: the admin edited the file and would be
            // told nothing. Empty plus a loud line is the honest state.
            Teras.LOGGER.error("Taxi: no se pudo leer config/teras/taxi.json; no habrá paradas "
                    + "hasta que se arregle el archivo", e);
            return List.of();
        }
    }

    private static void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp)) {
                GSON.toJson(stops, writer);
            }
            try {
                Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            Teras.LOGGER.error("Taxi: no se pudo guardar config/teras/taxi.json", e);
        }
    }

    /** Replaces the whole list without touching disk. Tests only. */
    static synchronized void setForTesting(List<TaxiStop> replacement) {
        stops = List.copyOf(replacement);
        loaded = true;
    }
}
