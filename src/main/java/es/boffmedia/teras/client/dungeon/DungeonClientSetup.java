package es.boffmedia.teras.client.dungeon;

import es.boffmedia.teras.Teras;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Mod-bus client registration for the dungeon minimap — the same setup/renderer split as
 * {@link es.boffmedia.teras.client.karts.KartsClientSetup}.
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DungeonClientSetup {
    private DungeonClientSetup() {}

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "dungeon_map"),
                (LayeredDraw.Layer) DungeonMapOverlay::render);
    }
}
