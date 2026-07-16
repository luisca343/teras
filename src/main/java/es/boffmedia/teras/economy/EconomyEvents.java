package es.boffmedia.teras.economy;

import es.boffmedia.teras.Teras;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Ties the balance cache to the player lifecycle: load on join, drop on leave. Replaces the 1.16.5
 * Wungill {@code WungillEvents.onLogin} → {@code FileHelper.cargarUsuario} path.
 *
 * <p>Pixelmon-free, and not guarded by {@link EconomyBridge#isAvailable()}: balances are tracked
 * regardless of whether Pixelmon is installed to spend them (see {@link EconomyBridge}).</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class EconomyEvents {
    private EconomyEvents() {}

    /**
     * The fetch is asynchronous, so the player is briefly joined with an unknown balance and cannot
     * spend during that window — see {@link EconomyStore}. Blocking the join instead would stall the
     * server thread on a network hop.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EconomyStore.load(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EconomyStore.unload(player.getUUID());
        }
    }
}
