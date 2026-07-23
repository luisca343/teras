package es.boffmedia.teras.client.gear;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.gear.GearItems;
import es.boffmedia.teras.dungeon.gear.GearKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * Wires {@link GearClientModel} onto the nine gear items, and bakes whatever per-piece icons exist.
 *
 * <p>Two mod-bus steps: load every {@code models/item/gear/*.json} present — in the mod or in an
 * active resource pack — so the piece icons are baked and findable, then wrap each kind item's baked
 * model so it can reach them. Dormant until a per-piece model is authored: with none present, every
 * piece falls back to its kind's icon exactly as before, and nothing is logged.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, value = Dist.CLIENT)
public final class GearItemModels {
    private GearItemModels() {}

    private static final String GEAR_MODELS = "models/item/gear";
    private static final String JSON = ".json";

    @SubscribeEvent
    public static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        Minecraft.getInstance().getResourceManager()
                .listResources(GEAR_MODELS, loc -> loc.getPath().endsWith(JSON))
                .keySet().forEach(loc -> {
                    if (!loc.getNamespace().equals(Teras.MOD_ID)) {
                        return;
                    }
                    // models/item/gear/<id>.json -> the id the gear_id component carries.
                    String path = loc.getPath();
                    String id = path.substring(GEAR_MODELS.length() + 1, path.length() - JSON.length());
                    ModelResourceLocation model = GearClientModel.perPiece(id);
                    if (model != null) {
                        event.register(model);
                    }
                });
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        for (GearKind kind : GearKind.values()) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(GearItems.of(kind));
            ModelResourceLocation key = ModelResourceLocation.inventory(itemId);
            BakedModel base = models.get(key);
            if (base != null && !(base instanceof GearClientModel)) {
                models.put(key, new GearClientModel(base));
            }
        }
    }
}
