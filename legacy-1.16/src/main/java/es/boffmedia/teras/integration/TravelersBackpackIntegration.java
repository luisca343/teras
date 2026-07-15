package es.boffmedia.teras.integration;

import com.pixelmonmod.pixelmon.api.battles.BattleItemScanner;
import com.tiviacz.travelersbackpack.inventory.TravelersBackpackInventory;
import com.tiviacz.travelersbackpack.items.TravelersBackpackItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

public class TravelersBackpackIntegration {


    public TravelersBackpackIntegration() {
        if (Integrations.travelersBackpack()) {
            BattleItemScanner.addScanner(new BattleItemScanner.InventoryScanner((stack) -> stack.getItem() instanceof TravelersBackpackItem
                    , (player, section, inventory, stack, items) -> {
                TravelersBackpackInventory inv = new TravelersBackpackInventory(stack, player, (byte)1);
                List<ItemStack> invItems = new ArrayList<>();
                for (int i = 0; i < inv.getInventory().getSlots(); i++) {
                    invItems.add(inv.getInventory().getStackInSlot(i));
                }
                BattleItemScanner.checkInventory(player, section, invItems, items);
            }, (player, stack, toMatch) -> {
                TravelersBackpackInventory inv = new TravelersBackpackInventory(stack, player, (byte)1);
                IItemHandler handler = inv.getInventory();
                return BattleItemScanner.findItemFromIterable(toMatch, handler.getSlots(), handler::getStackInSlot);
            }, (player, stack, toMatch) -> {
                TravelersBackpackInventory inv = new TravelersBackpackInventory(stack, player, (byte)1);
                IItemHandler handler = inv.getInventory();
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack slot = handler.getStackInSlot(i);
                    if (ItemStack.isSame(slot, toMatch) && ItemStack.tagMatches(slot, toMatch)) {
                        inv.decrStackSize(i, 1);
                        return slot;
                    }
                }

                return null;
            }));
        }
    }
}