package es.boffmedia.teras.karts.engine;

import es.boffmedia.teras.Teras;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Drives every live race from the server tick, and makes sure a disconnect never strands a kart.
 *
 * <p>Races tick <b>every</b> tick, unlike {@code RegionTracker}'s ten-tick scan: a kart covers real
 * distance in a tick, and checkpoint detection is only as good as how often it samples. The
 * expensive parts — recomputing standings, refreshing the HUD — have their own counters inside
 * {@link RaceCore}, so the per-tick work is a position read and a segment test per racer.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class RaceEngine {
    private RaceEngine() {}

    private static long tick;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        KartsRaceManager.tickAll(++tick);
    }

    /** The server's tick counter — the clock every race runs on. */
    public static long currentTick() {
        return tick;
    }

    /**
     * A racer who logs out is retired and their kart removed. Without this the kart would sit on the
     * circuit with nobody in it until the race timed out, blocking the grid slot and the track.
     */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        KartsRaceManager.leave(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        KartsRaceManager.clearAll("El servidor se está apagando.");
    }
}
