package es.boffmedia.teras.client.gear;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.gear.GearDef;
import es.boffmedia.teras.dungeon.gear.GearDefs;
import es.boffmedia.teras.init.ComponentInit;
import es.boffmedia.teras.init.ItemInit;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Colours a worn gear armour piece that has no Armourer's Workshop skin.
 *
 * <p>The armour material ships one grey layer on purpose — the honest look without AW — and this
 * multiplies it per piece so the four slots do not all read as the same grey suit. A piece uses its
 * own {@code tint} when {@code gear.json} gives one, otherwise a colour keyed on its rarity: common
 * stays grey, rare turns blue, epic violet. AW is untouched — a skinned piece renders through AW's
 * own component, above the armour layer, and never reaches here.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, value = Dist.CLIENT)
public final class GearArmorTint {
    private GearArmorTint() {}

    private static final int NO_TINT = 0xFFFFFFFF;
    private static final int RARE = 0xFF6FA8FF;
    private static final int EPIC = 0xFFC77DFF;

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(TINT,
                ItemInit.GEAR_YELMO.get(), ItemInit.GEAR_CORAZA.get(),
                ItemInit.GEAR_GREBAS.get(), ItemInit.GEAR_BOTAS.get());
    }

    private static final IClientItemExtensions TINT = new IClientItemExtensions() {
        @Override
        public int getArmorLayerTintColor(ItemStack stack, LivingEntity entity,
                                          ArmorMaterial.Layer layer, int layerIdx, int fallbackColor) {
            String id = stack.get(ComponentInit.GEAR_ID.get());
            GearDef def = id == null ? null : GearDefs.get(id);
            if (def == null) {
                return NO_TINT;
            }
            if (def.tint() != 0) {
                return def.tint();
            }
            return switch (def.rarity()) {
                case RARO -> RARE;
                case EPICO -> EPIC;
                case COMUN -> NO_TINT;
            };
        }
    };
}
