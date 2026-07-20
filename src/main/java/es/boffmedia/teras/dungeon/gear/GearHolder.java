package es.boffmedia.teras.dungeon.gear;

import es.boffmedia.teras.init.ComponentInit;
import net.minecraft.world.item.ItemStack;

/**
 * The one question every gear hook asks: "is this stack gear, and which?" Gear has no item classes
 * — a piece is a vanilla base item tagged with the {@code teras:gear_id} component (loot tables
 * write it via {@code minecraft:set_components}, {@code /teras dungeon gear dar} writes it
 * directly), and everything else about it is derived from the catalog by {@link GearStamp}.
 */
public final class GearHolder {
    private GearHolder() {}

    /** The live definition, or null if the stack is not gear. */
    public static GearDef defOf(ItemStack stack) {
        String id = stack.isEmpty() ? null : stack.get(ComponentInit.GEAR_ID.get());
        return id != null ? GearDefs.get(id) : null;
    }
}
