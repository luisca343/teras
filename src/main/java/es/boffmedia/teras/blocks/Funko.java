package es.boffmedia.teras.blocks;

import com.mojang.serialization.MapCodec;
import es.boffmedia.teras.blockentity.FunkoBlockEntity;
import es.boffmedia.teras.init.ItemInit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * A "funko" statue. The block itself renders nothing (its model is empty); {@code FunkoRenderer}
 * draws the figure using whatever skin its block entity holds. Rotatable so the figure faces the
 * placer, like a player head. Skin-sampled break/hit particles live in {@code FunkoClientExtensions}.
 */
public class Funko extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<Funko> CODEC = simpleCodec(Funko::new);

    private static final VoxelShape SHAPE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 9.0D, 12.0D);

    public Funko(Properties props) {
        super(props);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Face the placer, matching player-head behaviour.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FunkoBlockEntity(pos, state);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state) {
        return withSkinOf(world.getBlockEntity(pos));
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return Collections.singletonList(withSkinOf(builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY)));
    }

    /** A funko item carrying {@code blockEntity}'s skin. */
    private ItemStack withSkinOf(@Nullable BlockEntity blockEntity) {
        ItemStack stack = new ItemStack(ItemInit.FUNKO.get());
        if (blockEntity instanceof FunkoBlockEntity funko) {
            stack.applyComponents(funko.collectComponents());
        }
        return stack;
    }
}
