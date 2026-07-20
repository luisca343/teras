package es.boffmedia.teras.dungeon.gear;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.init.ComponentInit;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Writes the catalog onto the stack: stats, ability magnitude, and the Armourer's Workshop skin.
 *
 * <p>It has to be the stack rather than the item, because the catalog is config-owned and only the
 * <b>server</b> ever loads it — {@code GearConfig.load} runs from {@code ServerAboutToStartEvent}
 * and {@code /teras dungeon reload}, neither of which a remote client sees. Serving stats as item
 * defaults therefore had the client rendering the built-in numbers while the server applied the
 * tuned ones, and no edit to {@code gear.json} ever appeared in a tooltip. Components sync; static
 * fields on the server do not.</p>
 *
 * <p>Stamping is idempotent and generation-tagged, so {@link #refresh} can be called from a tick
 * without cost, and one {@code /teras dungeon reload} re-cuts every piece already in the world.</p>
 */
public final class GearStamp {
    private GearStamp() {}

    /** The attribute lines for {@code def} — also the item default, for an unstamped preview. */
    public static ItemAttributeModifiers modifiersFor(GearDef def) {
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        for (int i = 0; i < def.stats().size(); i++) {
            GearDef.Stat stat = def.stats().get(i);
            // One id per line: a repeated id silently replaces the earlier modifier.
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    Teras.MOD_ID, "gear/" + def.id() + "/" + i);
            builder.add(GearVanilla.attribute(stat.stat()),
                    new AttributeModifier(id, stat.amount(), GearVanilla.operation(stat.operation())),
                    GearVanilla.slot(def.kind()));
        }
        return builder.build();
    }

    /**
     * Stamps {@code stack} against the current catalog. Server-side only — the client has no
     * catalog to stamp from, and would overwrite the synced values with built-in defaults.
     *
     * @return the same stack, for chaining
     */
    public static ItemStack decorate(ItemStack stack) {
        GearDef def = GearHolder.defOf(stack);
        if (def == null) {
            return stack;
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiersFor(def));
        stack.set(ComponentInit.GEAR_MAGNITUDE.get(), def.magnitude());
        stack.set(ComponentInit.GEAR_GENERATION.get(), GearDefs.generation());
        // The base item supplies nothing but the silhouette: name, rarity colour and stack size
        // come from the stamp. ITEM_NAME (not CUSTOM_NAME) renames without the anvil italics and
        // stays translatable per client language.
        stack.set(DataComponents.ITEM_NAME,
                net.minecraft.network.chat.Component.translatable("item.teras." + def.id()));
        stack.set(DataComponents.RARITY, GearVanilla.rarity(def.rarity()));
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        if (stack.isDamageableItem()) {
            // A tool or armour base wears out with use, and gear breaking is a loot drop dying.
            stack.set(DataComponents.UNBREAKABLE,
                    new net.minecraft.world.item.component.Unbreakable(false));
        }
        GearSkins.apply(stack, def);
        return stack;
    }

    /**
     * Re-stamps only when the stack was cut against an older catalog. Cheap enough for a tick: an
     * int compare on everything already current.
     */
    public static void refresh(ItemStack stack) {
        Integer stamped = stack.get(ComponentInit.GEAR_GENERATION.get());
        if (stamped == null || stamped != GearDefs.generation()) {
            decorate(stack);
        }
    }
}
