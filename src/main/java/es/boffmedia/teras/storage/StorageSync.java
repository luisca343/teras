package es.boffmedia.teras.storage;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.net.StorageChangedPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tells a player's open SmartRotom that their Pokémon storage changed, so a PC opened in the browser
 * does not keep showing a Pokémon the player has since moved in-game — and, worse, send a swap
 * addressed to where it used to be. The swap is positional, so a stale view moves the wrong Pokémon.
 *
 * <p>Marks are coalesced and flushed on a tick rather than sent as they arrive: one swap notifies
 * twice (once per slot) and a box sort notifies thirty times, all of which the page answers with the
 * same single refetch.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class StorageSync {
    private StorageSync() {}

    /** ~½s. Long enough to swallow a burst, short enough that a player never notices the lag. */
    private static final int FLUSH_INTERVAL_TICKS = 10;

    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();
    private static int ticks;

    /** Marks {@code player}'s storage as changed. Safe from any thread. */
    public static void markDirty(UUID player) {
        if (player != null) {
            DIRTY.add(player);
        }
    }

    public static void markDirty(ServerPlayer player) {
        if (player != null) {
            markDirty(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (DIRTY.isEmpty() || ++ticks < FLUSH_INTERVAL_TICKS) {
            return;
        }
        ticks = 0;
        flush(event.getServer());
    }

    private static void flush(MinecraftServer server) {
        // Removed one at a time as they are observed, not snapshot-then-removeAll: the latter clears
        // marks added between the two calls, losing a change that arrived mid-flush.
        List<UUID> pending = new ArrayList<>();
        for (Iterator<UUID> it = DIRTY.iterator(); it.hasNext(); ) {
            pending.add(it.next());
            it.remove();
        }
        for (UUID uuid : pending) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            // Offline players have no browser to refresh; the page refetches when they return.
            if (player != null) {
                PacketDistributor.sendToPlayer(player, StorageChangedPayload.INSTANCE);
            }
        }
    }
}
