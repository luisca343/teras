package es.boffmedia.teras.util.math;


import es.boffmedia.teras.init.BlockInit;
import es.boffmedia.teras.util.math.vector.Vector2i;
import es.boffmedia.teras.util.math.vector.Vector3i;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public abstract class Multiblock {

    public enum OverrideAction {
        NONE,
        SIMULATE,
        IGNORE
    }

    public static class BlockOverride {
        final Vector3i pos;
        final OverrideAction action;

        public BlockOverride(Vector3i p, OverrideAction act) {
            pos = p;
            action = act;
        }

        public boolean apply(Vector3i bp, boolean originalResult) {
            if(action == OverrideAction.NONE || !bp.equals(pos))
                return originalResult;
            else if(action == OverrideAction.SIMULATE)
                return true;
            else //action == OverrideAction.IGNORE
                return false;
        }

    }

    public static final BlockOverride NULL_OVERRIDE = new BlockOverride(null, OverrideAction.NONE);

    //Modifies pos
    public static void findOrigin(World world, Vector3i pos, BlockSide side, BlockOverride override)
    {
        if(override == null)
            override = NULL_OVERRIDE;

        BlockPos.Mutable bp = new BlockPos.Mutable();

        //Go left
        do {
            pos.add(side.left);
            pos.toBlock(bp);
        } while(override.apply(pos, world.getBlockState(bp).getBlock() == BlockInit.PANTALLA.get()));

        pos.add(side.right);

        //Go down
        do {
            pos.add(side.down);
            pos.toBlock(bp);
        } while(override.apply(pos, world.getBlockState(bp).getBlock() == BlockInit.PANTALLA.get()));

        pos.add(side.up);
    }

    public static void findEnd(World world, Vector3i pos, BlockSide side, BlockOverride override)
    {
        if(override == null)
            override = NULL_OVERRIDE;

        BlockPos.Mutable bp = new BlockPos.Mutable();

        //Go right
        do {
            pos.add(side.right);
            pos.toBlock(bp);
        } while(override.apply(pos, world.getBlockState(bp).getBlock() == BlockInit.PANTALLA.get()));

        pos.add(side.left);

        //Go up
        do {
            pos.add(side.up);
            pos.toBlock(bp);
        } while(override.apply(pos, world.getBlockState(bp).getBlock() == BlockInit.PANTALLA.get()));

        pos.add(side.down);
    }

    //Origin stays constant
    public static Vector2i measure(World world, Vector3i origin, BlockSide side)
    {
        Vector2i ret = new Vector2i();
        Vector3i pos = origin.clone();

        BlockPos.Mutable bp = new BlockPos.Mutable();
        pos.toBlock(bp);

        //Go up
        do {
            pos.add(side.up);
            pos.toBlock(bp);
            ret.y++;
        } while(world.getBlockState(bp).getBlock() == BlockInit.PANTALLA.get());

        pos.add(side.down);

        //Go right
        do {
            pos.add(side.right);
            pos.toBlock(bp);
            ret.x++;
        } while(world.getBlockState(bp).getBlock() == BlockInit.PANTALLA.get());

        return ret;
    }

    //Origin and size stays constant.
    //Returns null if structure is okay, otherwise the erroring block pos.
    public static Vector3i check(World world, Vector3i origin, Vector2i size, BlockSide side)
    {
        Vector3i pos = origin.clone();
        BlockPos.Mutable bp = new BlockPos.Mutable();

        //Check inner
        for(int y = 0; y < size.y; y++) {
            for(int x = 0; x < size.x; x++) {
                pos.toBlock(bp);
                if(!(world.getBlockState(bp).getBlock() == BlockInit.PANTALLA.get()))
                    return pos; //Hole

                pos.add(side.forward);
                pos.toBlock(bp);
                if(world.getBlockState(bp).getBlock() == BlockInit.PANTALLA.get())
                    return pos; //Back should be empty

                pos.addMul(side.backward, 2);
                pos.toBlock(bp);
                if(world.getBlockState(bp).getBlock() == BlockInit.PANTALLA.get())
                    return pos; //Front should be empty

                pos.add(side.forward);
                pos.add(side.right);
            }

            pos.addMul(side.left, size.x);
            pos.add(side.up);
        }

        //Check left edge
        pos.set(origin);
        pos.add(side.left);

        for(int y = 0; y < size.y; y++) {
            pos.toBlock(bp);
            if(world.getBlockState(bp).getBlock() == BlockInit.PANTALLA.get())
                return pos; //Left edge should be empty

            pos.add(side.up);
        }

        //Check right edge
        pos.set(origin);
        pos.addMul(side.right, size.x);

        for(int y = 0; y < size.y; y++) {
            pos.toBlock(bp);
            if(world.getBlockState(bp).getBlock() == BlockInit.PANTALLA.get())
                return pos; //Left edge should be empty

            pos.add(side.up);
        }

        //Check bottom edge
        pos.set(origin);
        pos.add(side.down);

        for(int x = 0; x < size.x; x++) {
            pos.toBlock(bp);
            if(world.getBlockState(bp).getBlock() == BlockInit.PANTALLA.get())
                return pos; //Left edge should be empty

            pos.add(side.right);
        }

        //Check top edge
        pos.set(origin);
        pos.addMul(side.up, size.y);

        for(int x = 0; x < size.x; x++) {
            pos.toBlock(bp);
            if(world.getBlockState(bp).getBlock() == BlockInit.PANTALLA.get())
                return pos; //Left edge should be empty

            pos.add(side.right);
        }

        //All good.
        return null;
    }

    public static AxisAlignedBB getAABB(BlockPos inicio, BlockPos fin){
        int x = Math.min(inicio.getX(), fin.getX());
        int y = Math.min(inicio.getY(), fin.getY());
        int z = Math.min(inicio.getZ(), fin.getZ());

        int x2 = Math.max(inicio.getX(), fin.getX());
        int y2 = Math.max(inicio.getY(), fin.getY());
        int z2 = Math.max(inicio.getZ(), fin.getZ());

        return new AxisAlignedBB(x, y, z, x2 + 1, y2 + 1 , z2 + 1);
    }

}