package es.boffmedia.teras.region;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.region.model.TerasRegion;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server-side region enter/exit detection — the piece the 1.16.5 stack delegated to WorldGuard.
 * Every {@value #SCAN_INTERVAL_TICKS} ticks it diffs each player's containing-region set against the
 * previous scan and dispatches transitions to the registered {@link RegionListener}s
 * (same cadence pattern as {@link es.boffmedia.teras.storage.StorageSync}).
 *
 * <p>Login seeds an <em>empty</em> set on purpose: a player who logs in inside a town gets its
 * enter (and cartel) on the first scan, matching the old WorldGuard greeting behavior.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class RegionTracker {
    private RegionTracker() {}

    /** ~½s: fast enough that a walking player gets the cartel at the border. */
    private static final int SCAN_INTERVAL_TICKS = 10;

    private static final Map<UUID, Set<String>> INSIDE = new ConcurrentHashMap<>();
    private static final List<RegionListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static int ticks;

    public static void addListener(RegionListener listener) {
        LISTENERS.add(listener);
    }

    /**
     * Warms the catalog on the server thread before anything else can want it — otherwise the first
     * reader could be the HTTP executor, and {@link RegionStore}'s lazy disk load is not for that
     * thread.
     */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        Teras.LOGGER.info("RegionTracker: {} regions loaded", RegionStore.all().size());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            INSIDE.put(player.getUUID(), Set.of());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        INSIDE.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks < SCAN_INTERVAL_TICKS) return;
        ticks = 0;
        if (RegionStore.all().isEmpty()) return;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            String dimension = player.level().dimension().location().toString();
            Set<String> now = RegionIndex.namesAt(dimension, player.getX(), player.getY(), player.getZ());
            Set<String> before = INSIDE.getOrDefault(player.getUUID(), Set.of());
            if (now.equals(before)) continue;

            for (String name : before) {
                if (!now.contains(name)) dispatchExit(player, name);
            }
            for (String name : now) {
                if (!before.contains(name)) dispatchEnter(player, name);
            }
            INSIDE.put(player.getUUID(), now);
        }
    }

    private static void dispatchEnter(ServerPlayer player, String name) {
        TerasRegion region = RegionStore.get(name);
        if (region == null) return;
        for (RegionListener listener : LISTENERS) {
            try {
                listener.onEnter(player, region);
            } catch (Exception e) {
                Teras.LOGGER.warn("Region enter listener failed for '{}': {}", name, e.toString());
            }
        }
    }

    private static void dispatchExit(ServerPlayer player, String name) {
        TerasRegion region = RegionStore.get(name);
        if (region == null) return;
        for (RegionListener listener : LISTENERS) {
            try {
                listener.onExit(player, region);
            } catch (Exception e) {
                Teras.LOGGER.warn("Region exit listener failed for '{}': {}", name, e.toString());
            }
        }
    }
}
