package es.boffmedia.teras.client.region;

import es.boffmedia.teras.Teras;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Mod-bus client registrations for the region system (the {@link CameraSetup} split: layers register
 * on the mod bus, the renderer itself has no bus).
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RegionClientSetup {
    private RegionClientSetup() {}

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "region_cartel"),
                (LayeredDraw.Layer) CartelOverlay::render);
    }
}
