package es.boffmedia.teras.event.karts;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.objects.karts.Race;
import es.boffmedia.teras.util.objects.karts.RaceStatus;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "teras")
public class CarreraEvent {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START && event.player instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) event.player;
            Teras.raceManager.playerTick(player);
        }
    }
}