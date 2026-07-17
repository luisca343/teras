package es.boffmedia.teras.items;

import es.boffmedia.teras.init.ComponentInit;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Block item for the funko. Carries its skin in data components, which {@link BlockItem} hands to the
 * block entity on placement, so it behaves like {@code player_head}. Rendering in inventory/hand is
 * delegated to a client renderer registered in {@code FunkoClientExtensions} (1.16.5 wired that up
 * through {@code Item.Properties.setISTER}, which 1.21 replaced with client item extensions).
 */
public class FunkoItem extends BlockItem {

    public FunkoItem(Block block, Properties props) {
        super(block, props);
    }

    @Nullable
    public static ResolvableProfile getOwner(ItemStack stack) {
        return stack.get(DataComponents.PROFILE);
    }

    @Nullable
    public static String getSkinFile(ItemStack stack) {
        return stack.get(ComponentInit.FUNKO_SKIN_FILE.get());
    }

    /**
     * Turns a name-only profile into one carrying textures, mirroring vanilla {@code PlayerHeadItem}.
     * Only the server resolves (the profile caches are server-side), and the finished component then
     * syncs with the stack — which is why an unresolved funko in an inventory fixes itself on load.
     */
    @Override
    public void verifyComponentsAfterLoad(ItemStack stack) {
        ResolvableProfile profile = getOwner(stack);
        if (profile != null && !profile.isResolved()) {
            profile.resolve().thenAcceptAsync(resolved -> stack.set(DataComponents.PROFILE, resolved),
                    SkullBlockEntity.CHECKED_MAIN_THREAD_EXECUTOR);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        ResolvableProfile owner = getOwner(stack);
        String file = getSkinFile(stack);
        if (owner != null && owner.name().isPresent()) {
            tooltip.add(label("Jugador: ", owner.name().get()));
        } else if (file != null) {
            tooltip.add(label("Skin: ", file));
        }
    }

    private static MutableComponent label(String key, String value) {
        return Component.literal(key).withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }
}
