package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.model.TickQueue;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * "Do this in N ticks", which vanilla does not actually provide.
 *
 * <p>{@code MinecraftServer.tell(new TickTask(now + n, …))} looks like a delay and is not — see
 * {@link TickQueue} for the one-line reason. Every dungeon mechanic that wants a pause goes through
 * here instead.</p>
 *
 * <p>A task that throws is logged and dropped rather than allowed to escape into the tick loop: a
 * mechanic misbehaving must never take a server down, the same rule the materializer's job queue
 * already follows.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class DungeonScheduler {
    private DungeonScheduler() {}

    private static final TickQueue<Runnable> QUEUE = new TickQueue<>();

    /** Runs {@code action} {@code delay} ticks from now, or on the next tick if delay is 0. */
    public static void after(MinecraftServer server, int delay, Runnable action) {
        if (server == null || action == null) {
            return;
        }
        QUEUE.in(server.getTickCount(), delay, action);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (QUEUE.isEmpty()) {
            return;
        }
        for (Runnable action : QUEUE.drain(event.getServer().getTickCount())) {
            try {
                action.run();
            } catch (Throwable t) {
                Teras.LOGGER.error("Dungeons: a scheduled task failed and was dropped", t);
            }
        }
    }

    /** Nothing queued outlives the server; a pending hatch has no floor to hatch into. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        QUEUE.clear();
    }
}
