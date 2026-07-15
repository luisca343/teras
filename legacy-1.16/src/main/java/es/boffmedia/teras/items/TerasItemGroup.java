package es.boffmedia.teras.items;

import es.boffmedia.teras.init.ItemInit;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;

public class TerasItemGroup {
    public static final ItemGroup LIZARDON_GROUP = new ItemGroup("terasTab") {
        @Override
        public ItemStack makeIcon() {
            return new ItemStack(ItemInit.SMARTROTOM.get());
        }
    };
}
