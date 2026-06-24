package es.boffmedia.teras.items;

import es.boffmedia.teras.tileentity.FunkoTE;
import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Block item for the funko. Carries its skin in {@code BlockEntityTag} NBT (auto-copied to the
 * tile entity on placement by {@link BlockItem}), so it behaves like {@code player_head}.
 * Rendering in inventory/hand is delegated to a client ISTER configured via
 * {@code Item.Properties.setISTER(...)}.
 */
public class FunkoItem extends BlockItem {

    public FunkoItem(Block block, Properties props) {
        super(block, props);
    }

    @Nullable
    public static String getOwnerName(ItemStack stack) {
        CompoundNBT tag = stack.getTagElement("BlockEntityTag");
        if (tag == null) {
            return null;
        }
        if (tag.contains(FunkoTE.TAG_OWNER, 8)) {
            return tag.getString(FunkoTE.TAG_OWNER);
        }
        if (tag.contains(FunkoTE.TAG_OWNER, 10) && tag.getCompound(FunkoTE.TAG_OWNER).contains("Name", 8)) {
            return tag.getCompound(FunkoTE.TAG_OWNER).getString("Name");
        }
        return null;
    }

    @Nullable
    public static String getSkinFile(ItemStack stack) {
        CompoundNBT tag = stack.getTagElement("BlockEntityTag");
        if (tag != null && tag.contains(FunkoTE.TAG_FILE, 8)) {
            return tag.getString(FunkoTE.TAG_FILE);
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        super.appendHoverText(stack, world, tooltip, flag);
        String owner = getOwnerName(stack);
        String file = getSkinFile(stack);
        if (owner != null) {
            tooltip.add(label("Jugador: ", owner));
        } else if (file != null) {
            tooltip.add(label("Skin: ", file));
        }
    }

    private static IFormattableTextComponent label(String key, String value) {
        return new StringTextComponent(key).withStyle(TextFormatting.GRAY)
                .append(new StringTextComponent(value).withStyle(TextFormatting.WHITE));
    }
}
