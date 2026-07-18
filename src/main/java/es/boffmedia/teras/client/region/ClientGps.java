package es.boffmedia.teras.client.region;

import es.boffmedia.teras.Teras;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client-side live GPS, ported from the 1.16.5 {@code GpsClient}. Holds the active destination and,
 * on a throttled client tick, recomputes the route from the player's <em>current</em> position to
 * that destination and redraws it on JourneyMap.
 *
 * <p>JourneyMap types stay in {@code RouteDrawer}, named only behind the {@code ModList} guard, so
 * this class loads on clients without the map mod.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ClientGps {
    private ClientGps() {}

    /** Ticks between route recomputes while active (~0.5s at 20 TPS). */
    private static final int RECOMPUTE_INTERVAL = 10;
    /** Minimum blocks the player must move before the route is rebuilt. */
    private static final double MOVE_THRESHOLD = 2.0;
    /** Within this many blocks of the destination, the GPS turns itself off. */
    private static final double ARRIVAL_DISTANCE = 5.0;

    private static BlockPos destination; // null = inactive
    private static int tickCounter;
    private static double lastX = Double.NaN;
    private static double lastZ = Double.NaN;

    public static boolean isActive() {
        return destination != null;
    }

    /** Activates the GPS toward (x, z) and draws the first route immediately. */
    public static void start(int x, int z) {
        destination = new BlockPos(x, 64, z);
        tickCounter = 0;
        lastX = Double.NaN;
        lastZ = Double.NaN;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendSystemMessage(Component.literal("GPS activado hacia " + x + ", " + z));
            recompute(player);
        }
    }

    /** Deactivates the GPS and clears the route overlay. */
    public static void stop() {
        boolean wasActive = destination != null;
        destination = null;
        lastX = Double.NaN;
        lastZ = Double.NaN;
        if (journeyMapLoaded()) {
            es.boffmedia.teras.client.region.journeymap.RouteDrawer.clearRoute();
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (wasActive && player != null) {
            player.sendSystemMessage(Component.literal("GPS desactivado"));
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (destination == null) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.level() == null) return;
        // The route overlay lives in the overworld; don't navigate from other dimensions.
        if (player.level().dimension() != Level.OVERWORLD) return;

        if (++tickCounter < RECOMPUTE_INTERVAL) return;
        tickCounter = 0;

        double px = player.getX();
        double pz = player.getZ();

        // Arrived at the destination?
        double dx = px - destination.getX();
        double dz = pz - destination.getZ();
        if (Math.sqrt(dx * dx + dz * dz) <= ARRIVAL_DISTANCE) {
            player.sendSystemMessage(Component.literal("Has llegado a tu destino"));
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

    private static void recompute(LocalPlayer player) {
        lastX = player.getX();
        lastZ = player.getZ();
        if (!journeyMapLoaded()) {
            Teras.LOGGER.warn("GPS active but JourneyMap is not installed; nothing to draw");
            return;
        }
        es.boffmedia.teras.client.region.journeymap.RouteDrawer.createRoute(
                player.blockPosition().getX(), player.blockPosition().getZ(),
                destination.getX(), destination.getZ());
    }

    private static boolean journeyMapLoaded() {
        return ModList.get().isLoaded("journeymap");
    }
}
