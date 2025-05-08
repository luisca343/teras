package es.boffmedia.teras.blocks;

import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.potion.Effects;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.IPlantable;

public class LavenderFlower extends FlowerBlock {
    
    public LavenderFlower() {
        super(
            Effects.NIGHT_VISION, 
            100, 
            Properties.of(Material.PLANT)
                .noCollission()
                .instabreak()
                .sound(SoundType.GRASS)
                .randomTicks()
                .noOcclusion()
        );
    }
    
    @Override
    protected boolean mayPlaceOn(net.minecraft.block.BlockState state, IBlockReader worldIn, BlockPos pos) {
        Block block = state.getBlock();
        return block == Blocks.GRASS_BLOCK ||
                block == Blocks.DIRT ||
                block == Blocks.COARSE_DIRT ||
                block == Blocks.PODZOL ||
                block == Blocks.FARMLAND;
    }

    @Override
    public boolean canSustainPlant(BlockState state, IBlockReader reader, BlockPos pos, Direction direction, IPlantable plantable) {
        return false;
    }

    @OnlyIn(Dist.CLIENT)
    public static void registerRenderType() {
        RenderTypeLookup.setRenderLayer(es.boffmedia.teras.init.BlockInit.LAVENDER.get(), RenderType.cutout());
    }


}