package es.boffmedia.teras.init;

import es.boffmedia.teras.items.TerasItemGroup;
import net.minecraft.item.Item;

public class ItemBase extends Item {
    public ItemBase(String nombre) {
        super(new Properties().tab(TerasItemGroup.LIZARDON_GROUP));
    }
}
