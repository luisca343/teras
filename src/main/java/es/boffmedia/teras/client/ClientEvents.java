package es.boffmedia.teras.client;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.client.renders.IItemRenderer;
import es.boffmedia.teras.client.renders.SmartRotomRenderer;
import es.boffmedia.teras.init.ItemInit;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * Client game-bus event hooks. Currently: the first-person SmartRotom hand renderer.
 *
 * <p>Replaces the 1.16.5 {@code ClientProxy.onRenderPlayerHand(RenderHandEvent)} hook: when the held
 * item is the SmartRotom, cancel the vanilla hand render and delegate to {@link SmartRotomRenderer}.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    private static final IItemRenderer SMARTROTOM_RENDERER = new SmartRotomRenderer();

    @SubscribeEvent
    public static void onRenderPlayerHand(RenderHandEvent ev) {
        if (!ItemInit.SMARTROTOM.isBound() || ev.getItemStack().getItem() != ItemInit.SMARTROTOM.get()) {
            return;
        }

        float handSideSign = (ev.getHand() == InteractionHand.MAIN_HAND) ? 1.0f : -1.0f;
        SMARTROTOM_RENDERER.render(
                ev.getPoseStack(),
                ev.getItemStack(),
                handSideSign,
                ev.getSwingProgress(),
                ev.getEquipProgress(),
                ev.getMultiBufferSource(),
                ev.getPackedLight());
        ev.setCanceled(true);
    }
}
