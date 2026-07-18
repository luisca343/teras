package es.boffmedia.teras.karts.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Curated kart line-ups, in {@code config/teras/karts/selecciones.json}: an ordered list of preset
 * names a racer may choose from.
 *
 * <p>Stores preset <i>names</i> rather than pack coordinates, so re-pointing a preset re-points
 * every selection that offers it.</p>
 */
public final class KartSelectionStore {
    private KartSelectionStore() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Map<String, List<String>> selections;
    private static boolean dirty;

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("teras").resolve("karts").resolve("selecciones.json");
    }

    /** The preset names in a selection, or an empty list if there is no such selection. */
    public static List<String> get(String name) {
        ensureLoaded();
        return selections.getOrDefault(name, List.of());
    }

    public static boolean exists(String name) {
        ensureLoaded();
        return selections.containsKey(name);
    }

    public static Set<String> names() {
        ensureLoaded();
        return Set.copyOf(selections.keySet());
    }

    public static void put(String name, List<String> presetNames) {
        ensureLoaded();
        selections.put(name, List.copyOf(presetNames));
        dirty = true;
    }

    public static boolean remove(String name) {
        ensureLoaded();
        boolean existed = selections.remove(name) != null;
        dirty |= existed;
        return existed;
    }

    public static void saveIfDirty() {
        if (!dirty) {
            return;
        }
        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            JsonObject entries = new JsonObject();
            selections.forEach((name, presetNames) -> {
                JsonArray array = new JsonArray();
                presetNames.forEach(array::add);
                entries.add(name, array);
            });
            root.add("selecciones", entries);
            Files.writeString(file, GSON.toJson(root));
            dirty = false;
        } catch (Exception e) {
            Teras.LOGGER.warn("Karts: failed to save selections: {}", e.toString());
        }
    }

    public static void reload() {
        selections = null;
        dirty = false;
        ensureLoaded();
    }

    private static void ensureLoaded() {
        if (selections != null) {
            return;
        }
        selections = new LinkedHashMap<>();
        Path file = path();
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("selecciones")) {
                return;
            }
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("selecciones").entrySet()) {
                List<String> presetNames = new ArrayList<>();
                entry.getValue().getAsJsonArray().forEach(element -> presetNames.add(element.getAsString()));
                selections.put(entry.getKey(), List.copyOf(presetNames));
            }
        } catch (Exception e) {
            Teras.LOGGER.warn("Karts: failed to load selections: {}", e.toString());
        }
    }
}
