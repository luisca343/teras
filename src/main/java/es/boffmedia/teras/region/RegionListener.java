package es.boffmedia.teras.region;

import es.boffmedia.teras.region.model.TerasRegion;
import net.minecraft.server.level.ServerPlayer;

/**
 * A consumer of region enter/exit transitions, dispatched by {@link RegionTracker} on the server
 * thread. Enter/exit is a ~10-tick poll, so a listener that gates gameplay must re-check
 * containment itself at the moment it acts.
 */
public interface RegionListener {

    void onEnter(ServerPlayer player, TerasRegion region);

    default void onExit(ServerPlayer player, TerasRegion region) {}
}
