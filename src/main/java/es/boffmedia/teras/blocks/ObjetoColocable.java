package es.boffmedia.teras.blocks;

import es.boffmedia.teras.util.objects._old.ObjColocable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.UseAction;
import net.minecraft.util.Direction;

public class ObjetoColocable extends BlockItem {
    ObjColocable objColocable;

    public ObjetoColocable(Block block, ObjColocable objColocable) {
        super(block, objColocable.getProperties());
        this.objColocable = objColocable;
    }

    @Override
    public UseAction getUseAnimation(ItemStack stack) {
        return stack.getItem().isEdible() ? objColocable.getAction() : UseAction.NONE;
    }

    @Override
    protected boolean placeBlock(BlockItemUseContext ctx, BlockState state) {
        if(!ctx.getPlayer().isShiftKeyDown() || ctx.getClickedFace() != Direction.UP) return false;
        return super.placeBlock(ctx, state);
    }


}
