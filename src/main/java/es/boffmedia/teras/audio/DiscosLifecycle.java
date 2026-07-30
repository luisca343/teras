package es.boffmedia.teras.audio;

import es.boffmedia.teras.Teras;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Shuts the record library down with the server.
 *
 * <p>Playback is owned by world positions rather than by any block entity, and the decode threads
 * outlive a level: without this a shutdown leaves audio players holding channels on a voice server
 * that is going away, and the cache holding hundreds of megabytes of samples for a world nobody is
 * in any more.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class DiscosLifecycle {
    private DiscosLifecycle() {}

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        DiscPlayback.stopAll();
        TrackCache.clear();
    }
}
