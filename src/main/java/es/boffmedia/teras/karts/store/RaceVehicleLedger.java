package es.boffmedia.teras.karts.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.karts.vehicle.KartVehicles;
import es.boffmedia.teras.karts.vehicle.VehicleRef;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Every kart a race has spawned, written to disk so none can outlive the server that made it.
 *
 * <p>A race normally removes its own karts, but a crash, a kill -9 or a power cut leaves them
 * parked on the circuit forever — and unlike a dropped item they never despawn. The ledger is
 * written <i>before</i> a spawn is handed back to the race and cleared when the kart is removed, so
 * the file is only ever pessimistic: it may name a kart that is already gone (harmless) but never
 * misses one that still exists.</p>
 *
 * <p>On {@link ServerStartedEvent} everything still listed is culled.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class RaceVehicleLedger {
    private RaceVehicleLedger() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Set<VehicleRef> TRACKED = new LinkedHashSet<>();
    private static boolean loaded;
    private static boolean dirty;

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("teras").resolve("karts").resolve("race_vehicles.json");
    }

    /**
     * Records a kart before the race is told about it.
     *
     * <p>Writes straight through rather than batching: the whole point of the ledger is to survive
     * a crash between this call and the race ending, and a kart held only in memory when the
     * process dies is exactly the orphan this is meant to prevent. A grid forming does mean one
     * write per kart in a tick, which is why the file holds nothing but ids.</p>
     */
    public static synchronized void track(VehicleRef ref) {
        ensureLoaded();
        if (TRACKED.add(ref)) {
            save();
        }
    }

    /**
     * Forgets a kart that has been removed. Batched: losing this write costs one stale id that the
     * next startup finds already gone and drops, so it is not worth a disk write per kart as a
     * finishing field crosses the line.
     */
    public static synchronized void untrack(VehicleRef ref) {
        ensureLoaded();
        if (TRACKED.remove(ref)) {
            dirty = true;
        }
    }

    /** Flushes pending removals. Called when a race tears down. */
    public static synchronized void flush() {
        if (dirty) {
            save();
            dirty = false;
        }
    }

    public static synchronized List<VehicleRef> tracked() {
        ensureLoaded();
        return List.copyOf(TRACKED);
    }

    /**
     * Removes every kart the ledger still knows about. Runs at server start to clear crash
     * leftovers, and is also what {@code /karts admin limpiarvehiculos} calls.
     *
     * <p>An entry that cannot be resolved is <b>kept</b>, not dropped. Immersive Vehicles only knows
     * about entities in loaded chunks, so a kart parked in an unloaded part of the circuit is
     * invisible at server start — clearing the ledger regardless would forget it and leave it parked
     * there permanently, since karts never despawn. Kept entries are retried on the next start, or
     * immediately by an admin once the circuit has been walked.</p>
     *
     * @return how many karts were actually removed
     */
    public static synchronized int cullAll() {
        ensureLoaded();
        if (TRACKED.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (VehicleRef ref : List.copyOf(TRACKED)) {
            try {
                if (!KartVehicles.get().exists(ref)) {
                    continue;
                }
                KartVehicles.get().remove(ref);
                TRACKED.remove(ref);
                removed++;
            } catch (Exception e) {
                Teras.LOGGER.warn("Karts: could not remove leftover kart {}: {}", ref.id(), e.toString());
            }
        }
        save();
        return removed;
    }

    /**
     * Forgets everything without touching the world. For the case the ledger names karts that no
     * longer exist at all — a world rollback, or an admin who cleared them by hand — where retrying
     * forever would be pointless.
     */
    public static synchronized int forgetAll() {
        ensureLoaded();
        int count = TRACKED.size();
        TRACKED.clear();
        save();
        return count;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        int removed = cullAll();
        int pending = tracked().size();
        if (removed > 0 || pending > 0) {
            Teras.LOGGER.info("Karts: removed {} kart(s) left over from a previous session; "
                    + "{} still unaccounted for (likely in unloaded chunks)", removed, pending);
        }
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file = path();
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("vehiculos")) {
                return;
            }
            for (var element : root.getAsJsonArray("vehiculos")) {
                JsonObject entry = element.getAsJsonObject();
                try {
                    TRACKED.add(new VehicleRef(
                            UUID.fromString(entry.get("uuid").getAsString()),
                            entry.get("dimension").getAsString()));
                } catch (Exception e) {
                    Teras.LOGGER.warn("Karts: skipping malformed ledger entry: {}", e.toString());
                }
            }
        } catch (Exception e) {
            Teras.LOGGER.warn("Karts: failed to read the kart ledger: {}", e.toString());
        }
    }

    private static void save() {
        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            JsonArray array = new JsonArray();
            for (VehicleRef ref : TRACKED) {
                JsonObject entry = new JsonObject();
                entry.addProperty("uuid", ref.id().toString());
                entry.addProperty("dimension", ref.dimension());
                array.add(entry);
            }
            JsonObject root = new JsonObject();
            root.add("vehiculos", array);
            Files.writeString(file, GSON.toJson(root));
        } catch (Exception e) {
            Teras.LOGGER.warn("Karts: failed to write the kart ledger: {}", e.toString());
        }
    }

}
