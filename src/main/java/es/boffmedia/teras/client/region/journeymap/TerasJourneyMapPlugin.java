package es.boffmedia.teras.client.region.journeymap;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.client.region.ClientRegionStore;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;

/**
 * JourneyMap integration entry point (API v2 — the 1.16.5 code's v1 {@code ClientAPI.INSTANCE} is
 * gone). JourneyMap discovers this class through the annotation in mod scan data and instantiates
 * it itself: no Teras code ever names it, which is the whole class-load guard — without JourneyMap
 * installed nothing here links and the map features simply don't exist.
 */
@JourneyMapPlugin(apiVersion = IClientAPI.API_VERSION)
public class TerasJourneyMapPlugin implements IClientPlugin {

    private static volatile TerasJourneyMapPlugin instance;
    private IClientAPI api;

    /** The live plugin, or {@code null} when JourneyMap is absent or not yet initialized. */
    public static TerasJourneyMapPlugin instance() {
        return instance;
    }

    public IClientAPI api() {
        return api;
    }

    @Override
    public String getModId() {
        return Teras.MOD_ID;
    }

    @Override
    public void initialize(IClientAPI api) {
        this.api = api;
        instance = this;
        // The login sync may have landed before JourneyMap initialized, so draw once immediately.
        ClientRegionStore.addListener(RegionMapDrawer::refresh);
        RegionMapDrawer.refresh();
        Teras.LOGGER.info("JourneyMap plugin initialized; region overlays active");
    }
}
