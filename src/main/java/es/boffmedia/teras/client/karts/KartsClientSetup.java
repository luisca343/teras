package es.boffmedia.teras.client.karts;

import es.boffmedia.teras.Teras;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Mod-bus client registration for the race HUD — the same setup/renderer split as
 * {@link es.boffmedia.teras.client.region.RegionClientSetup}: the layer registers on the mod bus,
 * the renderer itself has no bus.
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KartsClientSetup {
    private KartsClientSetup() {}

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "race_hud"),
                (LayeredDraw.Layer) RaceOverlay::render);
    }
}
