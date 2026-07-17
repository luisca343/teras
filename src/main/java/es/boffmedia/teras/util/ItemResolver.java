package es.boffmedia.teras.util;

import es.boffmedia.teras.Teras;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/**
 * Turns a backend-supplied item id and count into an {@link ItemStack}.
 *
 * <p>Shared by the two delivery paths, which differ in wrapping rather than resolution: darCaja packs
 * its stacks into chests ({@link ChestCreationHelper}), {@code POST /giveitems} puts them straight in
 * the inventory. Same resolver, so an id that works on one works on the other.</p>
 *
 * <p><b>Shape only, never entitlement.</b> Both callers must already have obtained their list from the
 * backend, which is the authority on what a player is owed.</p>
 */
public final class ItemResolver {
    private ItemResolver() {}

    /** A vanilla stack. Counts are clamped to this rather than the item's own max, matching 1.16.5. */
    public static final int MAX_ITEM_COUNT = 64;

    /**
     * A stack of {@code id}, count clamped to [1, {@value #MAX_ITEM_COUNT}], or {@code null} if the id
     * is malformed or names no registered item.
     *
     * <p>The lower clamp is not cosmetic: the backend has sent {@code 0} before, and an unclamped 0
     * would be an empty stack — a silently dropped reward rather than a visible failure.</p>
     */
    public static ItemStack resolve(String id, int count) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            Teras.LOGGER.warn("Item id '{}' is malformed; skipping", id);
            return null;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        if (item == null) {
            Teras.LOGGER.warn("Item id '{}' is not a registered item; skipping", id);
            return null;
        }
        return new ItemStack(item, Math.max(1, Math.min(MAX_ITEM_COUNT, count)));
    }

    /** Applies an optional display name and lore, both of which the backend may omit. */
    public static void applyDisplay(ItemStack stack, String displayName, List<String> lore) {
        if (displayName != null && !displayName.isBlank()) {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(displayName));
        }
        if (lore != null && !lore.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(lore.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(line -> (Component) Component.literal(line))
                    .toList()));
        }
    }
}
