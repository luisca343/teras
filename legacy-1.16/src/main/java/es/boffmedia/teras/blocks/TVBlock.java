package es.boffmedia.teras.blocks;

import es.boffmedia.teras.init.TileEntityInit;
import es.boffmedia.teras.net.video.ScreenManager;
import es.boffmedia.teras.tileentity.FrameBlockEntity;
import es.boffmedia.teras.util.math.AlignedBox;
import es.boffmedia.teras.util.math.Facing;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalBlock;
import net.minecraft.entity.EntitySpawnPlacementRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
public class TVBlock extends Block {

    public static final DirectionProperty FACING = HorizontalBlock.FACING;
    public static final BooleanProperty VISIBLE = BooleanProperty.create("visible");

    private static final VoxelShape BOX_NORTH = Block.box(0, 0, 0, 16, 16, 1);
    private static final VoxelShape BOX_SOUTH = Block.box(0, 0, 15, 16, 16, 16);
    private static final VoxelShape BOX_WEST = Block.box(15, 0, 0, 16, 16, 16);
    private static final VoxelShape BOX_EAST = Block.box(0, 0, 0, 1, 16, 16);

    public TVBlock(Properties properties) {
        super(properties.noOcclusion().noCollission());


        this.registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
        this.registerDefaultState(defaultBlockState().setValue(VISIBLE, true));

    }
    @Override
    public VoxelShape getInteractionShape(BlockState state, IBlockReader p_199600_2_, BlockPos p_199600_3_) {
        switch (state.getValue(FACING)) {
            case NORTH:
                return BOX_NORTH;
            case SOUTH:
                return BOX_SOUTH;
            case EAST:
                return BOX_EAST;
            case WEST:
                return BOX_WEST;
            default:
                return BOX_NORTH;
        }
    }




    @Override
    public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context) {
        switch (state.getValue(FACING)) {
            case NORTH:
                return BOX_NORTH;
            case SOUTH:
                return BOX_SOUTH;
            case EAST:
                return BOX_EAST;
            case WEST:
                return BOX_WEST;
            default:
                return BOX_NORTH;
        }
    }


/*
    @Override
    public VoxelShape getCollisionShape(BlockState p_220071_1_, IBlockReader p_220071_2_, BlockPos p_220071_3_, ISelectionContext p_220071_4_) {
        return VoxelShapes.empty();

    }*/

    @Override
    public void setPlacedBy(World world, BlockPos pPos, BlockState pState, @Nullable LivingEntity pPlacer, ItemStack pStack) {
        TileEntity blockEntity = world.getBlockEntity(pPos);
        if (blockEntity instanceof FrameBlockEntity) {
            blockEntity.setChanged();
            world.setBlockEntity(pPos, blockEntity);
        }
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        return this.getStateDefinition().any().setValue(FACING, context.getHorizontalDirection() == Direction.WEST ? Direction.EAST : (context.getHorizontalDirection() == Direction.EAST ? Direction.WEST : context.getHorizontalDirection()));
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirrorIn) {
        return super.mirror(state, mirrorIn);
    }

    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(FACING);
        pBuilder.add(VISIBLE);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public int getLightValue(BlockState state, IBlockReader world, BlockPos pos) {
        return 1;
    }

    @Override
    public boolean canCreatureSpawn(BlockState state, IBlockReader world, BlockPos pos, EntitySpawnPlacementRegistry.PlacementType type, EntityType<?> entityType) {
        return false;
    }

    @Override
    public ActionResultType use(BlockState pState, World pLevel, BlockPos pPos, PlayerEntity pPlayer, Hand pHand, BlockRayTraceResult pHit) {
        TileEntity blockEntity = pLevel.getBlockEntity(pPos);
        if (!pLevel.isClientSide) {
            if (blockEntity instanceof FrameBlockEntity) {
                FrameBlockEntity frameBlockEntity = (FrameBlockEntity) blockEntity;
                frameBlockEntity.tryOpen(pLevel, pPos, pPlayer);
            }
        }

        return ActionResultType.SUCCESS;
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return TileEntityInit.FRAME_TE.get().create();
    }


    public static AlignedBox box(Direction direction) {
        Facing facing = Facing.get(direction);
        AlignedBox box = new AlignedBox();

        /*
        if (facing.positive)
            box.setMax(facing.axis, 0.031F);
        else
            box.setMin(facing.axis, 1 - 0.031F);*/
        return box;
    }


    @Override
    public void destroy(IWorld world, BlockPos pos, BlockState state) {
        TileEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof FrameBlockEntity) {
            ScreenManager.removePunto(pos);
        }
        super.destroy(world, pos, state);

    }

    @Override
    public BlockRenderType getRenderShape(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

}