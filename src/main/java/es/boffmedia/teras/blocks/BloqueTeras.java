package es.boffmedia.teras.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/** A placed {@link es.boffmedia.teras.init.ComidasTeras.Comida}: decorative, faces the placer. */
public class BloqueTeras extends HorizontalDirectionalBlock {

    /**
     * A {@link VoxelShape} cannot round-trip through a codec, so this decodes to a full cube. Nothing
     * calls it: {@code BlockBehaviour.Properties.CODEC} is a unit codec, making block codecs vestigial
     * for blocks that never appear in a datapack.
     */
    public static final MapCodec<BloqueTeras> CODEC = simpleCodec(props -> new BloqueTeras(props, Shapes.block()));

    private final VoxelShape hitbox;

    public BloqueTeras(Properties props, VoxelShape hitbox) {
        super(props);
        this.hitbox = hitbox;
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

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return hitbox;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
}
