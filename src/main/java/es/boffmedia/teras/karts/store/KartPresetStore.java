package es.boffmedia.teras.karts.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.karts.vehicle.KartLoadout;
import es.boffmedia.teras.karts.vehicle.KartSpec;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Named karts, in {@code config/teras/karts/presets.json}.
 *
 * <p>A preset gives an admin-chosen name to a pack coordinate, so circuits, selections and race
 * commands refer to "kart_estandar" rather than to {@code oamp:hatchback:red}. That indirection is
 * what lets the karts a server races be swapped — a new content pack, a rebalanced model — without
 * touching every circuit that mentions it.</p>
 */
public final class KartPresetStore {
    private KartPresetStore() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** @param displayName what players see; falls back to the preset name. */
    public record Preset(String name, KartLoadout loadout, String displayName) {
        public String display() {
            return displayName == null || displayName.isBlank() ? name : displayName;
        }

        public KartSpec spec() {
            return loadout.model();
        }
    }

    private static Map<String, Preset> presets;
    private static boolean dirty;

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("teras").resolve("karts").resolve("presets.json");
    }

    public static Preset get(String name) {
        ensureLoaded();
        return presets.get(name);
    }

    public static boolean exists(String name) {
        ensureLoaded();
        return presets.containsKey(name);
    }

    public static Set<String> names() {
        ensureLoaded();
        return Set.copyOf(presets.keySet());
    }

    public static List<Preset> all() {
        ensureLoaded();
        return List.copyOf(presets.values());
    }

    public static void put(Preset preset) {
        ensureLoaded();
        presets.put(preset.name(), preset);
        dirty = true;
    }

    public static boolean remove(String name) {
        ensureLoaded();
        boolean existed = presets.remove(name) != null;
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
            presets.forEach((name, preset) -> {
                JsonObject json = new JsonObject();
                json.addProperty("kart", preset.spec().toId());
                json.addProperty("displayName", preset.display());
                if (preset.loadout().hasCustomParts()) {
                    JsonObject parts = new JsonObject();
                    preset.loadout().parts().forEach(
                            (slot, part) -> parts.addProperty(String.valueOf(slot), part.toId()));
                    json.add("partes", parts);
                }
                entries.add(name, json);
            });
            root.add("presets", entries);
            Files.writeString(file, GSON.toJson(root));
            dirty = false;
            Teras.LOGGER.info("Karts: saved {} presets", presets.size());
        } catch (Exception e) {
            Teras.LOGGER.warn("Karts: failed to save presets: {}", e.toString());
        }
    }

    public static void reload() {
        presets = null;
        dirty = false;
        ensureLoaded();
    }

    private static void ensureLoaded() {
        if (presets != null) {
            return;
        }
        presets = new LinkedHashMap<>();
        Path file = path();
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("presets")) {
                return;
            }
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("presets").entrySet()) {
                JsonObject json = entry.getValue().getAsJsonObject();
                KartSpec spec = KartSpec.parse(json.get("kart").getAsString());
                if (spec == null) {
                    Teras.LOGGER.warn("Karts: skipping preset '{}': unreadable kart id", entry.getKey());
                    continue;
                }
                java.util.Map<Integer, KartSpec> parts = new java.util.LinkedHashMap<>();
                if (json.has("partes")) {
                    for (Map.Entry<String, JsonElement> part : json.getAsJsonObject("partes").entrySet()) {
                        KartSpec partSpec = KartSpec.parse(part.getValue().getAsString());
                        if (partSpec == null) {
                            Teras.LOGGER.warn("Karts: preset '{}' names an unreadable part in slot {}",
                                    entry.getKey(), part.getKey());
                            continue;
                        }
                        try {
                            parts.put(Integer.parseInt(part.getKey()), partSpec);
                        } catch (NumberFormatException e) {
                            Teras.LOGGER.warn("Karts: preset '{}' has a non-numeric part slot '{}'",
                                    entry.getKey(), part.getKey());
                        }
                    }
                }
                presets.put(entry.getKey(), new Preset(entry.getKey(), new KartLoadout(spec, parts),
                        json.has("displayName") ? json.get("displayName").getAsString() : entry.getKey()));
            }
            Teras.LOGGER.info("Karts: loaded {} presets", presets.size());
        } catch (Exception e) {
            Teras.LOGGER.warn("Karts: failed to load presets: {}", e.toString());
        }
    }
}
