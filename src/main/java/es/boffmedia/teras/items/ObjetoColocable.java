package es.boffmedia.teras.items;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Both a food and a placeable block: sneak-right-click places, a plain right-click eats.
 * Refusing to place is what makes eating work — {@link BlockItem#useOn} falls back to
 * {@link #use} whenever placement does not consume the action and the stack carries FOOD.
 */
public class ObjetoColocable extends BlockItem {

    private final UseAnim animacion;

    public ObjetoColocable(Block block, UseAnim animacion, Properties props) {
        super(block, props);
        this.animacion = animacion;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return stack.has(DataComponents.FOOD) ? animacion : UseAnim.NONE;
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext ctx, BlockState state) {
        return ctx.getPlayer() != null
                && ctx.getPlayer().isShiftKeyDown()
                && super.placeBlock(ctx, state);
    }
}
