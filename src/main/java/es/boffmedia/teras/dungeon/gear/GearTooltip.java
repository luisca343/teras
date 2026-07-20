package es.boffmedia.teras.dungeon.gear;

import es.boffmedia.teras.init.ComponentInit;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * The ability line under a piece of gear.
 *
 * <p>The number comes off the stack, not the catalog: tooltips render on the client, and a remote
 * client never loads {@code gear.json} — reading the catalog here is what made a retuned magnitude
 * invisible. The catalog is only the fallback, for a stack the server has not stamped yet.</p>
 */
final class GearTooltip {
    private GearTooltip() {}

    static void append(String gearId, ItemStack stack, List<Component> tooltip) {
        GearDef def = GearDefs.get(gearId);
        if (def == null) {
            return;
        }
        if (def.ability() != GearAbility.NINGUNA) {
            Double stamped = stack.get(ComponentInit.GEAR_MAGNITUDE.get());
            double magnitude = stamped != null ? stamped : def.magnitude();
            tooltip.add(Component.translatable(
                    "gear.teras." + def.ability().name().toLowerCase(Locale.ROOT),
                    format(def.ability(), magnitude)));
        }
        tooltip.add(Component.translatable("gear.teras.exclusiva"));
    }

    /** Percentages read as percentages; flat amounts read as whole numbers. */
    private static String format(GearAbility ability, double magnitude) {
        return switch (ability) {
            case VAMPIRISMO, ONDA, ESPINAS -> String.valueOf(Math.round(magnitude * 100));
            default -> String.valueOf((long) magnitude);
        };
    }
}
