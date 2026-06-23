package es.boffmedia.teras.event.karts;

import es.boffmedia.teras.Teras;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "teras")
public class CarreraEvent {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START && event.player instanceof ServerPlayerEntity) {
            if (Teras.raceManager == null) return;
            ServerPlayerEntity player = (ServerPlayerEntity) event.player;
            Teras.raceManager.playerTick(player);
        }
    }

    /**
     * Tick all active vehicle handlers exactly once per server tick. Previously this ran inside
     * {@code playerTick} (once per participant), giving O(P*H) work per tick; here it is O(H).
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && Teras.raceManager != null) {
            Teras.raceManager.tickVehicles();
        }
    }
}
