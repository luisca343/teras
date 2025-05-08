package es.boffmedia.teras.blocks;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.tileentity.PantallaTE;
import es.boffmedia.teras.util.math.BlockSide;
import es.boffmedia.teras.util.string.MessageHelper;
import es.boffmedia.teras.util.math.Multiblock;
import es.boffmedia.teras.util.math.vector.Vector2i;
import es.boffmedia.teras.util.math.vector.Vector3i;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.StateContainer;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;

import java.util.UUID;


public class BloquePantalla extends HorizontalBlock {
    public BloquePantalla(Properties properties) {
        super(properties);
    }

    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }



    /*
    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        // get the facing value from the block
        return new PantallaTE(state.getValue(FACING));
    }*/

    public BlockSide positionToSide(BlockState state){
        switch (state.getValue(FACING)){
            case SOUTH:
                return BlockSide.SOUTH;
            case EAST:
                return BlockSide.EAST;
            case WEST:
                return BlockSide.WEST;
            case DOWN:
                return BlockSide.BOTTOM;
            case UP:
                return BlockSide.TOP;
            default:
                return BlockSide.NORTH;
        }
    }

    // @Override on right click
    @Override
    public ActionResultType use(BlockState state, World world, BlockPos position, PlayerEntity player, Hand handIn, BlockRayTraceResult hit) {

        BlockSide orientacionPantalla = positionToSide(state);

        Vector3i pos = new Vector3i(position);
        Multiblock.findOrigin(world, pos, orientacionPantalla, null);
        PantallaTE te = (PantallaTE) world.getBlockEntity(pos.toBlock());



        Vector2i size = Multiblock.measure(world, pos, orientacionPantalla);

        if (size.x < 2 && size.y < 2) {
            player.sendMessage(new StringTextComponent("Ta pequeño"), UUID.randomUUID());
            return ActionResultType.SUCCESS;
        }

        if (size.x > 10 || size.y > 10) {
            player.sendMessage(new StringTextComponent("Ta grande"), UUID.randomUUID());
            return ActionResultType.SUCCESS;
        }

        Vector3i err = Multiblock.check(world, pos, size, orientacionPantalla);
        if (err != null) {
            player.sendMessage(new StringTextComponent("No vale illo"), UUID.randomUUID());
            return ActionResultType.SUCCESS;
        }

        boolean created = false;

        if (te == null) {
            MessageHelper.enviarMensaje(player, "No existe un TE");
            BlockPos bp = pos.toBlock();
            te =  new PantallaTE(orientacionPantalla);
            world.setBlockEntity(bp, te);

            Vector3i pos2 = new Vector3i(position);
            Multiblock.findEnd(world, pos2, orientacionPantalla, null);
            te.updateAABB(pos2.toBlock());


            created = true;
        }else{
            MessageHelper.enviarMensaje(player, "Ya existe un TE");
        }

        te.load();
        te.setDimensions(size.x, size.y);

        if(player.isCrouching()){
            Teras.PROXY.abrirPantallaCine(te);
        }


        /*
        if (world.isClientSide) {
            PantallaTE te2 = (PantallaTE) world.getBlockEntity(position);
            Teras.PROXY.abrirPantallaCine(te2);
        }*/
        return ActionResultType.SUCCESS;
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }


    @Override
    public void neighborChanged(BlockState state, World world, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, world, pos, block, fromPos, isMoving);
        if (!world.isClientSide) {
            Vector3i vec1 = new Vector3i(pos);
            /*¡
            for (BlockSide side : BlockSide.values()) {
                Vector3i vec = new Vector3i(pos);
                Multiblock.findOrigin(world, vec, side, null);


                PantallaTE tes = (PantallaTE) world.getBlockEntity(vec.toBlock());

                Direction facing = Direction.from2DDataValue(side.reverse().ordinal());
                vec.sub(pos.getX(), pos.getY(), pos.getZ()).neg();

            }*/

            if(block != this){
                Teras.LOGGER.info("Neighbor changed");
            }else {
                Teras.LOGGER.info("I'm the neighbor");
            }
        }
    }
}
