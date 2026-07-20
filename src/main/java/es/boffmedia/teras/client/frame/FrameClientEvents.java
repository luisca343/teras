package es.boffmedia.teras.client.frame;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.init.BlockEntityInit;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Registers the frame's {@link FrameRenderer} on the mod bus. */
@EventBusSubscriber(modid = Teras.MOD_ID, value = Dist.CLIENT)
public final class FrameClientEvents {
    private FrameClientEvents() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlockEntityInit.FRAME.get(), FrameRenderer::new);
    }
}
