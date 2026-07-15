package es.boffmedia.teras.items;


import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class Disco extends Item {
    public Disco() {
        super(new Item.Properties().stacksTo(1).tab(TerasItemGroup.LIZARDON_GROUP));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable World p_77624_2_, List<ITextComponent> p_77624_3_, ITooltipFlag p_77624_4_) {
        super.appendHoverText(stack, p_77624_2_, p_77624_3_, p_77624_4_);
        CompoundNBT nbt = stack.getOrCreateTag();
        if (nbt.contains("disco")) {
            p_77624_3_.add(new StringTextComponent(TextFormatting.LIGHT_PURPLE + nbt.getString("disco")));
        }else{
            p_77624_3_.add(new StringTextComponent("Disco sin grabar"));
        }
    }
}
