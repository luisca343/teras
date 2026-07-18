package es.boffmedia.teras.karts.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.karts.vehicle.KartLoadout;
import es.boffmedia.teras.karts.vehicle.KartSpec;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Which karts each player owns, in {@code config/teras/karts/garage.json}.
 *
 * <p>A garage entry records <i>which model</i> a player owns, not a snapshot of a particular
 * vehicle — see {@link KartSpec} for why. A race spawns a fresh kart of that model, so the player's
 * ownership is never at risk from a crash on the last corner.</p>
 *
 * <p>{@link #add} is the seam a kart shop would use: buying a kart is crediting a garage entry, and
 * nothing else about the race path needs to know where the entry came from.</p>
 */
public final class GarageStore {
    private GarageStore() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * @param id       short handle used in commands, unique within one player's garage
     * @param origin   how it was acquired ("admin", "tienda", "premio"), for support questions
     */
    public record GarageEntry(String id, String name, KartLoadout loadout, long obtainedAt, String origin) {
        public KartSpec spec() {
            return loadout.model();
        }
    }

    private static Map<UUID, List<GarageEntry>> garages;
    private static Map<UUID, String> defaultChoice;
    private static boolean dirty;

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("teras").resolve("karts").resolve("garage.json");
    }

    public static List<GarageEntry> listFor(UUID player) {
        ensureLoaded();
        return List.copyOf(garages.getOrDefault(player, List.of()));
    }

    public static Optional<GarageEntry> find(UUID player, String entryId) {
        return listFor(player).stream().filter(entry -> entry.id().equalsIgnoreCase(entryId)).findFirst();
    }

    /** Adds a kart to a player's garage, minting a unique short id within that garage. */
    public static GarageEntry add(UUID player, String name, KartLoadout loadout, String origin) {
        ensureLoaded();
        List<GarageEntry> owned = garages.computeIfAbsent(player, key -> new ArrayList<>());
        GarageEntry entry = new GarageEntry(nextId(owned), name, loadout, System.currentTimeMillis(), origin);
        owned.add(entry);
        dirty = true;
        return entry;
    }

    public static boolean remove(UUID player, String entryId) {
        ensureLoaded();
        List<GarageEntry> owned = garages.get(player);
        if (owned == null) {
            return false;
        }
        boolean removed = owned.removeIf(entry -> entry.id().equalsIgnoreCase(entryId));
        if (removed) {
            dirty = true;
            if (entryId.equalsIgnoreCase(defaultChoice.get(player))) {
                defaultChoice.remove(player);
            }
        }
        return removed;
    }

    /** The kart a player takes into a garage race unless they pick another. */
    public static Optional<GarageEntry> preferred(UUID player) {
        ensureLoaded();
        String chosen = defaultChoice.get(player);
        if (chosen != null) {
            Optional<GarageEntry> entry = find(player, chosen);
            if (entry.isPresent()) {
                return entry;
            }
        }
        return listFor(player).stream().findFirst();
    }

    public static boolean setPreferred(UUID player, String entryId) {
        if (find(player, entryId).isEmpty()) {
            return false;
        }
        defaultChoice.put(player, entryId);
        dirty = true;
        return true;
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

            JsonObject players = new JsonObject();
            garages.forEach((player, owned) -> {
                JsonArray array = new JsonArray();
                for (GarageEntry entry : owned) {
                    JsonObject json = new JsonObject();
                    json.addProperty("id", entry.id());
                    json.addProperty("nombre", entry.name());
                    json.addProperty("kart", entry.spec().toId());
                    if (entry.loadout().hasCustomParts()) {
                        JsonObject parts = new JsonObject();
                        entry.loadout().parts().forEach(
                                (slot, part) -> parts.addProperty(String.valueOf(slot), part.toId()));
                        json.add("partes", parts);
                    }
                    json.addProperty("obtenidoEn", entry.obtainedAt());
                    json.addProperty("origen", entry.origin());
                    array.add(json);
                }
                players.add(player.toString(), array);
            });
            root.add("jugadores", players);

            JsonObject preferred = new JsonObject();
            defaultChoice.forEach((player, entryId) -> preferred.addProperty(player.toString(), entryId));
            root.add("preferidos", preferred);

            Files.writeString(file, GSON.toJson(root));
            dirty = false;
        } catch (Exception e) {
            Teras.LOGGER.warn("Karts: failed to save garages: {}", e.toString());
        }
    }

    public static void reload() {
        garages = null;
        defaultChoice = null;
        dirty = false;
        ensureLoaded();
    }

    private static String nextId(List<GarageEntry> owned) {
        int candidate = owned.size() + 1;
        while (idTaken(owned, String.valueOf(candidate))) {
            candidate++;
        }
        return String.valueOf(candidate);
    }

    private static boolean idTaken(List<GarageEntry> owned, String id) {
        return owned.stream().anyMatch(entry -> entry.id().equalsIgnoreCase(id));
    }

    private static void ensureLoaded() {
        if (garages != null) {
            return;
        }
        garages = new LinkedHashMap<>();
        defaultChoice = new LinkedHashMap<>();
        Path file = path();
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                return;
            }
            if (root.has("jugadores")) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("jugadores").entrySet()) {
                    try {
                        garages.put(UUID.fromString(entry.getKey()), readEntries(entry.getValue()));
                    } catch (Exception e) {
                        Teras.LOGGER.warn("Karts: skipping garage '{}': {}", entry.getKey(), e.toString());
                    }
                }
            }
            if (root.has("preferidos")) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("preferidos").entrySet()) {
                    try {
                        defaultChoice.put(UUID.fromString(entry.getKey()), entry.getValue().getAsString());
                    } catch (Exception e) {
                        Teras.LOGGER.warn("Karts: skipping garage preference: {}", e.toString());
                    }
                }
            }
        } catch (Exception e) {
            Teras.LOGGER.warn("Karts: failed to load garages: {}", e.toString());
        }
    }

    private static List<GarageEntry> readEntries(JsonElement element) {
        List<GarageEntry> owned = new ArrayList<>();
        for (JsonElement item : element.getAsJsonArray()) {
            JsonObject json = item.getAsJsonObject();
            KartSpec spec = KartSpec.parse(json.get("kart").getAsString());
            if (spec == null) {
                continue;
            }
            Map<Integer, KartSpec> parts = new LinkedHashMap<>();
            if (json.has("partes")) {
                for (Map.Entry<String, JsonElement> part : json.getAsJsonObject("partes").entrySet()) {
                    KartSpec partSpec = KartSpec.parse(part.getValue().getAsString());
                    if (partSpec != null) {
                        try {
                            parts.put(Integer.parseInt(part.getKey()), partSpec);
                        } catch (NumberFormatException e) {
                            Teras.LOGGER.warn("Karts: garage entry has a non-numeric part slot '{}'",
                                    part.getKey());
                        }
                    }
                }
            }
            owned.add(new GarageEntry(
                    json.get("id").getAsString(),
                    json.has("nombre") ? json.get("nombre").getAsString() : spec.systemName(),
                    new KartLoadout(spec, parts),
                    json.has("obtenidoEn") ? json.get("obtenidoEn").getAsLong() : 0L,
                    json.has("origen") ? json.get("origen").getAsString() : "desconocido"));
        }
        return owned;
    }
}
