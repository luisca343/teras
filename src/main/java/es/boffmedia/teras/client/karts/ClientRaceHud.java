package es.boffmedia.teras.client.karts;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.net.RaceHudPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * The client's copy of its own race state, as last sent by the server.
 *
 * <p>Pure display: nothing here feeds back into the race, and stale values only ever cost a frame
 * of accuracy on a lap counter.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ClientRaceHud {
    private ClientRaceHud() {}

    private static RaceHudPayload state = RaceHudPayload.hidden();

    public static void accept(RaceHudPayload payload) {
        state = payload == null ? RaceHudPayload.hidden() : payload;
    }

    public static RaceHudPayload state() {
        return state;
    }

    public static boolean isVisible() {
        return state.phase() != RaceHudPayload.PHASE_HIDDEN;
    }

    /** Cleared on disconnect, so a stale HUD never survives into the next server joined. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        state = RaceHudPayload.hidden();
    }
}
