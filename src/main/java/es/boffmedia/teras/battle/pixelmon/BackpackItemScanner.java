package es.boffmedia.teras.battle.pixelmon;

import com.pixelmonmod.pixelmon.api.battles.BagSection;
import com.pixelmonmod.pixelmon.api.battles.BattleItemScanner;
import com.pixelmonmod.pixelmon.items.ItemData;
import es.boffmedia.teras.battle.NestedScanGuard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Lets Pixelmon find and use battle items (Potions, Revives, Poké Balls) that are inside a backpack
 * rather than loose in the player's inventory.
 *
 * <p>Compiles against Pixelmon ({@code compileOnly}), so it is only ever named from behind a
 * {@code isPixelmonLoaded()} guard — see {@link es.boffmedia.teras.battle.BackpackBridge}. Pixelmon-only
 * by nature, not by omission: {@code BattleItemScanner} is a Pixelmon concept and Cobblemon has no
 * analogue, so unlike battles and the dex this has no engine-neutral half to abstract.</p>
 *
 * <h2>Why no backpack mod is named here</h2>
 *
 * <p>Traveler's Backpack and Sophisticated Backpacks both register the standard NeoForge
 * {@link Capabilities.ItemHandler#ITEM} on their backpack items, so one capability lookup reads either —
 * and any other container mod — with no dependency on, or import from, any of them. 1.16.5 instead
 * constructed {@code new TravelersBackpackInventory(stack, player, (byte) 1)} directly, which is why it
 * only ever supported the one mod.</p>
 *
 * <p><b>The capability is each mod's input/output view, not its raw inventory</b>, and the two are not
 * equivalent: Sophisticated returns a <i>filtered</i> handler honouring the backpack's IO settings,
 * while Traveler's returns a near-raw one. So a Sophisticated backpack with filters set may hide an item
 * from the battle bag. That is the accepted trade of reading both through one vanilla API; the filtered
 * view is the extension point both authors expose deliberately. See {@code INTEGRATIONS.md}.</p>
 */
public final class BackpackItemScanner {
    private BackpackItemScanner() {}

    /** See {@link NestedScanGuard} — {@code checkInventory} re-enters this scanner. */
    private static final NestedScanGuard NESTED = new NestedScanGuard();

    /** Registers the scanner with Pixelmon. Called once, from common setup. */
    public static void install() {
        BattleItemScanner.addScanner(new BattleItemScanner.InventoryScanner(
                BackpackItemScanner::isContainer,
                BackpackItemScanner::collect,
                BackpackItemScanner::find,
                BackpackItemScanner::consume));
    }

    /** The backpack's contents as an item handler, or {@code null} if this stack holds nothing. */
    @Nullable
    private static IItemHandler handlerOf(ItemStack stack) {
        return stack.getCapability(Capabilities.ItemHandler.ITEM);
    }

    /**
     * Whether Pixelmon should look inside this stack. Deliberately "anything that exposes an item
     * handler" rather than a list of known backpack items — that is what makes this mod-agnostic.
     */
    private static boolean isContainer(ItemStack stack) {
        return !stack.isEmpty() && handlerOf(stack) != null;
    }

    /** Reports the backpack's contents to Pixelmon, one level deep. */
    private static void collect(ServerPlayer player, BagSection section, List<ItemStack> inventory,
                                ItemStack stack, List<ItemData> items) {
        IItemHandler handler = handlerOf(stack);
        if (handler == null) {
            return;
        }
        if (!NESTED.enter()) {
            return;
        }
        try {
            List<ItemStack> contents = new ArrayList<>(handler.getSlots());
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                contents.add(handler.getStackInSlot(slot));
            }
            BattleItemScanner.checkInventory(player, section, contents, items);
        } finally {
            NESTED.exit();
        }
    }

    /**
     * Pixelmon's contract is a {@code null} miss, not {@link ItemStack#EMPTY} — {@code findMatchingItem}
     * null-checks the result to decide whether to keep looking.
     */
    @Nullable
    private static ItemStack find(ServerPlayer player, ItemStack stack, ItemStack toMatch) {
        IItemHandler handler = handlerOf(stack);
        if (handler == null) {
            return null;
        }
        return BattleItemScanner.findItemFromIterable(toMatch, handler.getSlots(), handler::getStackInSlot);
    }

    /** Removes one matching item and returns it, mirroring {@code Container.removeItem(slot, 1)}. */
    @Nullable
    private static ItemStack consume(ServerPlayer player, ItemStack stack, ItemStack toMatch) {
        IItemHandler handler = handlerOf(stack);
        if (handler == null) {
            return null;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!ItemStack.isSameItemSameComponents(handler.getStackInSlot(slot), toMatch)) {
                continue;
            }
            // A filtered handler may refuse a slot it will happily show; keep looking rather than
            // reporting a consumption that did not happen.
            ItemStack removed = handler.extractItem(slot, 1, false);
            if (!removed.isEmpty()) {
                return removed;
            }
        }
        return null;
    }
}
