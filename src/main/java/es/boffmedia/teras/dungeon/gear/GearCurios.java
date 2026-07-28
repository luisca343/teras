package es.boffmedia.teras.dungeon.gear;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.init.ItemInit;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.function.Predicate;

/**
 * The Curios half of dungeon charms: the slot they are worn in.
 *
 * <p>The only file in the mod that imports {@code top.theillusivec4.*}, and every entry point is
 * guarded by {@link ModList} — the same shape as {@link GearSkins}. <b>Curios is a soft
 * dependency.</b> Teras is one mod with many systems, and a server that installs it for regions or
 * starbank without wanting dungeons should not have to add a trinket-slot mod because a system it
 * never uses has charms in it.</p>
 *
 * <p>Without Curios a charm falls back to the offhand, which is where charms lived before this
 * existed. The cost is that it then competes with the shield for the same slot, and a player has to
 * choose — a real difference between servers, but a legible one rather than broken content.</p>
 *
 * <h2>Why the modifiers come from here rather than the stack</h2>
 *
 * <p>A curio slot is not an {@link net.minecraft.world.entity.EquipmentSlotGroup}, so the
 * {@code ItemAttributeModifiers} component {@link GearStamp} writes is simply not consulted while a
 * charm sits in one. Curios asks the item, through {@code ICurioItem}, and that is the one path
 * that works. Miss it and a charm equips, tooltips correctly and grants nothing — the exact silent
 * failure this project keeps finding.</p>
 */
public final class GearCurios {
    private GearCurios() {}

    public static final String MOD_ID = "curios";

    /**
     * The slot charms go in.
     *
     * <p>Declared in {@code data/teras/curios/slots/amuleto.json} and given to players by
     * {@code data/teras/curios/entities/amuleto.json}. <b>The namespace is ours, the subdirectory is
     * Curios'</b> — the loader scans {@code data/&lt;any namespace&gt;/curios/slots/}, which is why
     * these first shipped at {@code data/curios/slots/} and were read by nothing at all: the slot
     * simply never existed and the inventory had no tab to show.</p>
     *
     * <p>The slot validates with {@code curios:tag}, so an item belongs in it by being in the
     * {@code curios:amuleto} item tag rather than by anyone's say-so.</p>
     */
    public static final String SLOT = "amuleto";

    private static Boolean available;

    public static boolean available() {
        if (available == null) {
            available = ModList.get().isLoaded(MOD_ID);
        }
        return available;
    }

    /**
     * Registers the charm item as a curio. Called from common setup, and a no-op without Curios.
     *
     * <p>{@code CuriosApi.registerCurio} rather than the capability event: it is the supported
     * one-line path for "this item is a curio", and it keeps every Curios type inside this class.</p>
     */
    public static void register() {
        if (!available()) {
            Teras.LOGGER.info("Gear: Curios is absent — charms will be worn in the offhand");
            return;
        }
        CuriosApi.registerCurio(ItemInit.GEAR_AMULETO.get(), new CharmCurio());
        Teras.LOGGER.info("Gear: charms registered for the '{}' Curios slot", SLOT);
    }

    /**
     * Whether {@code entity} is wearing a charm matching {@code test} in a curio slot.
     *
     * <p>Answers false without Curios, because there are no curio slots to search — the caller's
     * offhand check is what covers that case.</p>
     */
    public static boolean wearsCurio(LivingEntity entity, Predicate<ItemStack> test) {
        if (!available()) {
            return false;
        }
        return CuriosApi.getCuriosInventory(entity)
                .map(inventory -> !inventory.findCurios(test).isEmpty())
                .orElse(false);
    }

    /**
     * Every stack the entity has in a curio slot, or empty without Curios. What the ability hooks
     * append to the slots they already scan.
     */
    public static java.util.List<ItemStack> wornCurios(LivingEntity entity) {
        if (!available()) {
            return java.util.List.of();
        }
        return CuriosApi.getCuriosInventory(entity)
                .map(inventory -> inventory.findCurios(stack -> !stack.isEmpty()).stream()
                        .map(top.theillusivec4.curios.api.SlotResult::stack)
                        .toList())
                .orElse(java.util.List.of());
    }

    /** The charm's behaviour as a curio. Stats come from the catalog, exactly as in every slot. */
    private static final class CharmCurio implements ICurioItem {

        @Override
        public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(
                SlotContext context, ResourceLocation id, ItemStack stack) {
            Multimap<Holder<Attribute>, AttributeModifier> modifiers = HashMultimap.create();
            GearDef def = GearHolder.defOf(stack);
            if (def == null) {
                return modifiers;
            }
            for (int i = 0; i < def.stats().size(); i++) {
                GearDef.Stat stat = def.stats().get(i);
                // As GearStamp: a first-party stat has no attribute, and the combat sheet reads it
                // from the curio stack instead.
                if (!stat.stat().vanilla()) {
                    continue;
                }
                // One id per line, as GearStamp does: a repeated id silently replaces the earlier
                // modifier instead of adding to it.
                modifiers.put(GearVanilla.attribute(stat.stat()), new AttributeModifier(
                        ResourceLocation.fromNamespaceAndPath(
                                Teras.MOD_ID, "curio/" + def.id() + "/" + i),
                        stat.amount(), GearVanilla.operation(stat.operation())));
            }
            return modifiers;
        }
    }
}
