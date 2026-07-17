package es.boffmedia.teras.blocks;

import com.mojang.serialization.MapCodec;
import es.boffmedia.teras.blockentity.FrameBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * A thin in-world media frame. The block model is empty — {@code FrameRenderer} draws the display
 * from the {@link FrameBlockEntity}'s url. {@code FACING} is the direction the screen looks toward
 * (the placer's side); the thin collision slab sits flush against the wall behind it.
 */
public class PictureFrame extends Block implements EntityBlock {

    public static final MapCodec<PictureFrame> CODEC = simpleCodec(PictureFrame::new);

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    /** Slab depth: half a pixel, matching the legacy {@code frameThickness}. */
    private static final VoxelShape SHAPE_NORTH = box(0, 0, 15.5, 16, 16, 16);
    private static final VoxelShape SHAPE_SOUTH = box(0, 0, 0, 16, 16, 0.5);
    private static final VoxelShape SHAPE_WEST = box(15.5, 0, 0, 16, 16, 16);
    private static final VoxelShape SHAPE_EAST = box(0, 0, 0, 0.5, 16, 16);
    private static final VoxelShape SHAPE_UP = box(0, 0, 0, 16, 0.5, 16);
    private static final VoxelShape SHAPE_DOWN = box(0, 15.5, 0, 16, 16, 16);

    public PictureFrame(Properties props) {
        super(props);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The screen looks toward the player, i.e. out of the surface they clicked.
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        // Editing gates on permission server-side; opening the client screen for everyone is harmless
        // (a forged config packet is rejected). The class is only reached on the client, so the server
        // never loads the screen.
        if (level.isClientSide && level.getBlockEntity(pos) instanceof FrameBlockEntity frame) {
            es.boffmedia.teras.client.frame.FrameConfigScreen.open(frame);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
            case UP -> SHAPE_UP;
            case DOWN -> SHAPE_DOWN;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FrameBlockEntity(pos, state);
    }
}
