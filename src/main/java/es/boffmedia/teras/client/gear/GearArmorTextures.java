package es.boffmedia.teras.client.gear;

import es.boffmedia.teras.Teras;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Which gear pieces ship their own worn-armour texture, so {@link es.boffmedia.teras.dungeon.gear.GearArmorItem}
 * can fall back to the material's grey when one is absent without a per-frame resource lookup.
 *
 * <p>Rebuilt on every resource reload from what is actually present — in the mod or an active
 * resource pack — so dropping {@code textures/models/armor/gear/<id>_layer_N.png} (and running
 * {@code /reload}) is all it takes to reskin a piece's body.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, value = Dist.CLIENT)
public final class GearArmorTextures {
    private GearArmorTextures() {}

    private static final String DIR = "textures/models/armor/gear";
    private static volatile Set<ResourceLocation> available = Set.of();

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) GearArmorTextures::rebuild);
    }

    private static void rebuild(ResourceManager resources) {
        Set<ResourceLocation> found = new HashSet<>();
        resources.listResources(DIR, loc -> loc.getPath().endsWith(".png")).keySet().forEach(loc -> {
            if (loc.getNamespace().equals(Teras.MOD_ID)) {
                found.add(loc);
            }
        });
        available = Set.copyOf(found);
    }

    /** The worn texture for this piece and layer, or null to fall back to the material's grey. */
    public static ResourceLocation resolve(String id, boolean innerModel) {
        ResourceLocation texture = ResourceLocation.tryBuild(Teras.MOD_ID,
                DIR + "/" + id + "_layer_" + (innerModel ? 2 : 1) + ".png");
        return texture != null && available.contains(texture) ? texture : null;
    }
}
