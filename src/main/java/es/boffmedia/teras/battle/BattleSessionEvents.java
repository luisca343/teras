package es.boffmedia.teras.battle;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.battle.lifecycle.BattleOutcomeHandler;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Drops a player's in-flight battle state when they leave. Both maps are cleared by the <i>next</i>
 * battle event rather than by time, so an entry left by a mid-battle disconnect would silently apply
 * to an unrelated later battle instead of surfacing as an error.
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class BattleSessionEvents {
    private BattleSessionEvents() {}

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BattleOutcomeHandler.removeEndListener(player.getUUID());
        // TerasTeamPreview compiles against Pixelmon: named only inside the guard so it is never
        // classloaded without it.
        if (ModList.get().isLoaded("pixelmon")) {
            es.boffmedia.teras.battle.pixelmon.TerasTeamPreview.discard(player.getUUID());
        }
    }
}
