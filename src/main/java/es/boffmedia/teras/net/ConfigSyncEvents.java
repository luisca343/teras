package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Pushes this server's config to every player as they join, which is the client's only source for it
 * (see {@link ServerConfigPayload}). Sending at login rather than on demand means the SmartRotom's
 * home is already known by the time the item can be held.
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class ConfigSyncEvents {
    private ConfigSyncEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TerasNet.sendConfig(player);
        }
    }
}
