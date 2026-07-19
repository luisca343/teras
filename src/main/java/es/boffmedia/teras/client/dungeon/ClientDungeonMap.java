package es.boffmedia.teras.client.dungeon;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.net.DungeonMapPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * The client's copy of its dungeon minimap, as last sent by the server. Pure display, same
 * contract as {@link es.boffmedia.teras.client.karts.ClientRaceHud}: nothing here feeds back
 * into the run.
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ClientDungeonMap {
    private ClientDungeonMap() {}

    private static DungeonMapPayload state = DungeonMapPayload.hidden();

    public static void accept(DungeonMapPayload payload) {
        state = payload == null ? DungeonMapPayload.hidden() : payload;
        DungeonMapLock.setLocked(state.active());
    }

    public static DungeonMapPayload state() {
        return state;
    }

    public static boolean isVisible() {
        return state.active();
    }

    /**
     * Cleared on disconnect, so a stale map never survives into the next server joined — and the
     * player's own minimap is handed back, which must not depend on a run ending cleanly.
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        state = DungeonMapPayload.hidden();
        DungeonMapLock.setLocked(false);
    }
}
