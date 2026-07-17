package es.boffmedia.teras.client.camera;

import es.boffmedia.teras.Teras;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * The camera's mod-bus registrations: its viewfinder layer and its keybinds. Separate from the classes
 * that implement them because those subscribe to the game bus, and one class cannot do both.
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CameraSetup {
    private CameraSetup() {}

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        // Above everything, so the HUD sits behind the viewfinder's mask rather than through it.
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "camera_viewfinder"),
                (LayeredDraw.Layer) CameraOverlay::renderViewfinder);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(CameraKeys.ZOOM_IN);
        event.register(CameraKeys.ZOOM_OUT);
        event.register(CameraKeys.FLASHLIGHT);
    }
}
