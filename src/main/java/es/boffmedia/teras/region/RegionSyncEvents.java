package es.boffmedia.teras.region;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.net.RegionSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Keeps every client's copy of the region catalog current: the full list on login, and a broadcast
 * after each {@code /teras region} mutation. Always sent — an empty array is how a client learns
 * the last region was deleted.
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class RegionSyncEvents {
    private RegionSyncEvents() {}

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, payload());
        }
    }

    /** Called after every catalog mutation ({@link es.boffmedia.teras.region.command.RegionCommand}). */
    public static void broadcast() {
        PacketDistributor.sendToAllPlayers(payload());
    }

    private static RegionSyncPayload payload() {
        return new RegionSyncPayload(RegionJson.toWebArray(RegionStore.all().values()));
    }
}
