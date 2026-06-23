package es.boffmedia.teras.client.gps;

import es.boffmedia.teras.util.RouteCreator;
import es.boffmedia.teras.util.string.MessageHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Client-side live GPS. Holds the active destination and, on a throttled client
 * tick, recomputes the route from the player's <em>current</em> position to that
 * destination and redraws it on JourneyMap. Registered on the Forge event bus in
 * {@code ClientProxy.preInit}.
 */
@OnlyIn(Dist.CLIENT)
public class GpsClient {
    public static final GpsClient INSTANCE = new GpsClient();

    /** Ticks between route recomputes while active (~0.5s at 20 TPS). */
    private static final int RECOMPUTE_INTERVAL = 10;
    /** Minimum blocks the player must move before the route is rebuilt. */
    private static final double MOVE_THRESHOLD = 2.0;
    /** Within this many blocks of the destination, the GPS turns itself off. */
    private static final double ARRIVAL_DISTANCE = 5.0;

    private BlockPos destination; // null = inactive
    private int tickCounter;
    private double lastX = Double.NaN;
    private double lastZ = Double.NaN;

    private GpsClient() {}

    public boolean isActive() {
        return destination != null;
    }

    /** Activates the GPS toward (x, z) and draws the first route immediately. */
    public void start(int x, int z) {
        this.destination = new BlockPos(x, 64, z);
        this.tickCounter = 0;
        this.lastX = Double.NaN;
        this.lastZ = Double.NaN;

        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player != null) {
            MessageHelper.enviarMensaje(player, "GPS activado hacia " + x + ", " + z);
            recompute(player);
        }
    }

    /** Deactivates the GPS and clears the route overlay. */
    public void stop() {
        boolean wasActive = destination != null;
        destination = null;
        lastX = Double.NaN;
        lastZ = Double.NaN;
        RouteCreator.clearRoute();

        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (wasActive && player != null) {
            MessageHelper.enviarMensaje(player, "GPS desactivado");
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || destination == null) return;

        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player == null || player.level == null) return;
        // The route overlay lives in the overworld; don't navigate from other dimensions.
        if (player.level.dimension() != World.OVERWORLD) return;

        if (++tickCounter < RECOMPUTE_INTERVAL) return;
        tickCounter = 0;

        double px = player.getX();
        double pz = player.getZ();

        // Arrived at the destination?
        double dx = px - destination.getX();
        double dz = pz - destination.getZ();
        if (Math.sqrt(dx * dx + dz * dz) <= ARRIVAL_DISTANCE) {
            MessageHelper.enviarMensaje(player, "Has llegado a tu destino");
            stop();
            return;
        }

        // Don't rebuild the route unless the player has actually moved.
        if (!Double.isNaN(lastX)) {
            double mdx = px - lastX;
            double mdz = pz - lastZ;
            if (Math.sqrt(mdx * mdx + mdz * mdz) < MOVE_THRESHOLD) return;
        }

        recompute(player);
    }

    private void recompute(ClientPlayerEntity player) {
        lastX = player.getX();
        lastZ = player.getZ();
        RouteCreator.Point start = new RouteCreator.Point(
                player.blockPosition().getX(), player.blockPosition().getZ());
        RouteCreator.Point end = new RouteCreator.Point(destination.getX(), destination.getZ());
        RouteCreator.createRoute(start, end);
    }
}
