package es.boffmedia.teras.client.gear;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.init.ComponentInit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

import java.util.List;

/**
 * Lets a gear piece show its own icon while every piece of a kind still shares one registered item.
 *
 * <p>The item's baked model is wrapped in this, and the swap happens at render time off the piece's
 * {@code teras:gear_id}: if a model is baked at {@code teras:item/gear/<id>} the piece renders it,
 * otherwise it falls back to the kind's shared icon. A per-piece model (and its texture) is a plain
 * flat item — the mod can ship one, and a resource pack can add or replace one, without any code.
 * Armourer's Workshop is untouched: it renders from its own skin component, above this, so a skinned
 * piece never reaches here.</p>
 */
public class GearClientModel extends BakedModelWrapper<BakedModel> {

    public GearClientModel(BakedModel kindModel) {
        super(kindModel);
    }

    @Override
    public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
        String id = stack.get(ComponentInit.GEAR_ID.get());
        if (id != null && !id.isBlank()) {
            ModelManager models = Minecraft.getInstance().getModelManager();
            ModelResourceLocation key = perPiece(id);
            if (key != null) {
                BakedModel piece = models.getModel(key);
                if (piece != models.getMissingModel()) {
                    return List.of(piece);
                }
            }
        }
        return originalModel.getRenderPasses(stack, fabulous);
    }

    /**
     * The standalone location a piece's icon is registered and looked up under, or null when the id
     * cannot be a resource path (a hand-written {@code gear.json} id with stray characters falls back
     * rather than crashing the item renderer).
     */
    public static ModelResourceLocation perPiece(String id) {
        ResourceLocation path = ResourceLocation.tryBuild(Teras.MOD_ID, "item/gear/" + id);
        return path == null ? null
                : new ModelResourceLocation(path, ModelResourceLocation.STANDALONE_VARIANT);
    }
}
