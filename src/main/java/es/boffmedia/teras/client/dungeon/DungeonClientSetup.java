package es.boffmedia.teras.client.dungeon;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.init.EntityInit;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Mod-bus client registration for the dungeon minimap — the same setup/renderer split as
 * {@link es.boffmedia.teras.client.karts.KartsClientSetup}.
 */
@EventBusSubscriber(modid = Teras.MOD_ID, value = Dist.CLIENT)
public final class DungeonClientSetup {
    private DungeonClientSetup() {}

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "dungeon_map"),
                (LayeredDraw.Layer) DungeonMapOverlay::render);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityInit.DUNGEON_ENEMY.get(), DungeonGeoEnemyRenderer::new);
        // Bolts render as a thrown item — no model to author, and the two kinds read apart by the
        // item they borrow.
        event.registerEntityRenderer(EntityInit.DUNGEON_BOLT.get(),
                ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(ctx, 1.0f, false));
    }
}
