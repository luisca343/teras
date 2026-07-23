package es.boffmedia.teras.dungeon.gear;

import es.boffmedia.teras.init.ComponentInit;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;

/**
 * A gear armour piece that can wear its own worn texture, not just the material's shared grey.
 *
 * <p>The armour material ships one grey layer as the honest no-AW look; {@link GearArmorTint} colours
 * it per piece, and this goes a step further — if a piece ships {@code teras:textures/models/armor/
 * gear/<id>_layer_N.png} the body renders that instead. Absent, it returns null and the grey layer
 * (tinted) stands in. Armourer's Workshop, when a piece has a skin, renders above all of it.</p>
 */
public class GearArmorItem extends ArmorItem {

    public GearArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot,
                                            ArmorMaterial.Layer layer, boolean innerModel) {
        String id = stack.get(ComponentInit.GEAR_ID.get());
        if (id == null || id.isBlank()) {
            return null;
        }
        // Client-only class, reached only from the client render hook — never loaded server-side.
        return es.boffmedia.teras.client.gear.GearArmorTextures.resolve(id, innerModel);
    }
}
