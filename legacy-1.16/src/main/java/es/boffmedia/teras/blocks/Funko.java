package es.boffmedia.teras.blocks;

import es.boffmedia.teras.init.ItemInit;
import es.boffmedia.teras.init.TileEntityInit;
import es.boffmedia.teras.tileentity.FunkoTE;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalBlock;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootContext;
import net.minecraft.loot.LootParameters;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * A "funko" statue. The block itself renders nothing (its model is empty); the
 * {@code FunkoTERenderer} draws the figure using whatever skin its tile entity holds.
 * Rotatable so the figure faces the placer, like a player head.
 */
public class Funko extends HorizontalBlock {

    private static final VoxelShape SHAPE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 9.0D, 12.0D);

    public Funko(Properties props) {
        super(props);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        // Face the placer, matching player-head behaviour.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
        return SHAPE;
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return TileEntityInit.FUNKO_TE.get().create();
    }

    @Override
    public ItemStack getCloneItemStack(IBlockReader world, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(ItemInit.FUNKO.get());
        TileEntity te = world.getBlockEntity(pos);
        if (te instanceof FunkoTE) {
            ((FunkoTE) te).writeToItem(stack);
        }
        return stack;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootContext.Builder builder) {
        ItemStack stack = new ItemStack(ItemInit.FUNKO.get());
        TileEntity te = builder.getOptionalParameter(LootParameters.BLOCK_ENTITY);
        if (te instanceof FunkoTE) {
            ((FunkoTE) te).writeToItem(stack);
        }
        return Collections.singletonList(stack);
    }

    // Break/hit particles: sample the funko's actual skin instead of the static block texture.
    // These run only client-side, so referencing the client particle helper here is safe.
    @Override
    public boolean addDestroyEffects(BlockState state, World world, BlockPos pos, ParticleManager manager) {
        return es.boffmedia.teras.client.funko.FunkoBreakParticles.addDestroyEffects(world, pos, manager);
    }

    @Override
    public boolean addHitEffects(BlockState state, World world, RayTraceResult target, ParticleManager manager) {
        if (target instanceof BlockRayTraceResult) {
            return es.boffmedia.teras.client.funko.FunkoBreakParticles.addHitEffects(world, pos(target), target.getLocation(), manager);
        }
        return false;
    }

    private static BlockPos pos(RayTraceResult target) {
        return ((BlockRayTraceResult) target).getBlockPos();
    }
}
