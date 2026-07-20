package es.boffmedia.teras.client.region;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.region.RegionJson;
import es.boffmedia.teras.region.model.TerasRegion;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The client's copy of the server's region catalog, replaced wholesale on every
 * {@link es.boffmedia.teras.net.RegionSyncPayload} and cleared on logout. Consumers that draw from
 * it (the JourneyMap plugin) subscribe as listeners; no JourneyMap types appear here, so the store
 * loads with or without the map mod.
 */
@EventBusSubscriber(modid = Teras.MOD_ID, value = Dist.CLIENT)
public final class ClientRegionStore {
    private ClientRegionStore() {}

    private static volatile List<TerasRegion> regions = List.of();
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    /** Entry from {@link es.boffmedia.teras.client.ClientNetHandler}; render thread. */
    public static void accept(String json) {
        regions = List.copyOf(RegionJson.fromWebArray(json));
        Teras.LOGGER.debug("ClientRegionStore: {} regions synced", regions.size());
        notifyListeners();
    }

    public static List<TerasRegion> all() {
        return regions;
    }

    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        regions = List.of();
        notifyListeners();
    }

    private static void notifyListeners() {
        for (Runnable listener : LISTENERS) {
            try {
                listener.run();
            } catch (Exception e) {
                Teras.LOGGER.warn("Region store listener failed: {}", e.toString());
            }
        }
    }
}
