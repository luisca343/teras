package es.boffmedia.teras.client.fluid;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.init.FluidInit;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Hot springs are vanilla water textures under a blue tint. */
@EventBusSubscriber(modid = Teras.MOD_ID, value = Dist.CLIENT)
public final class AguasTermalesClient {
    private AguasTermalesClient() {}

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return FluidInit.WATER_STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return FluidInit.WATER_FLOW;
            }

            @Override
            public ResourceLocation getOverlayTexture() {
                return FluidInit.WATER_OVERLAY;
            }

            @Override
            public int getTintColor() {
                return FluidInit.TINT;
            }
        }, FluidInit.AGUAS_TERMALES_TYPE.get());
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // The LiquidBlock itself is INVISIBLE; only the fluid layer matters.
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(FluidInit.AGUAS_TERMALES_SOURCE.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(FluidInit.AGUAS_TERMALES_FLOWING.get(), RenderType.translucent());
        });
    }
}
