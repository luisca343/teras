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
        // A line per ability. The stamped magnitude only speaks for the first — it is one double
        // on the stack — so the rest read their numbers from the catalog, which is where a piece
        // with several abilities is described anyway.
        Double stamped = stack.get(ComponentInit.GEAR_MAGNITUDE.get());
        for (int i = 0; i < def.abilities().size(); i++) {
            AbilityDef ability = def.abilities().get(i);
            double fallback = ability.magnitude(ability.ability().defaultMagnitude());
            double magnitude = i == 0 && stamped != null ? stamped : fallback;
            tooltip.add(Component.translatable(
                    "gear.teras." + ability.ability().name().toLowerCase(Locale.ROOT),
                    format(ability.ability(), magnitude)));
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
