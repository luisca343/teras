package es.boffmedia.teras.client.frame;

import es.boffmedia.teras.Teras;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Releases every live frame player when the client leaves a world (game bus). */
@EventBusSubscriber(modid = Teras.MOD_ID, value = Dist.CLIENT)
public final class FrameWorldEvents {
    private FrameWorldEvents() {}

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            FrameMediaManager.releaseAll();
        }
    }
}
