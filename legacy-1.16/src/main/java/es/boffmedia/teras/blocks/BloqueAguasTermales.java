package es.boffmedia.teras.blocks;

import es.boffmedia.teras.init.FluidInit;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.FlowingFluidBlock;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BloqueAguasTermales extends FlowingFluidBlock {
    public BloqueAguasTermales() {
        super(FluidInit.AGUAS_TERMALES_SOURCE, AbstractBlock.Properties.of(Material.WATER).randomTicks());
    }

    @Override
    public void entityInside(BlockState state, World world, BlockPos post, Entity entity) {
        super.entityInside(state, world, post, entity);
        if(!world.isClientSide() && entity instanceof PlayerEntity){
            PlayerEntity player = (PlayerEntity) entity;
            if(!player.hasEffect(Effects.REGENERATION)){
                player.addEffect(new EffectInstance(Effects.REGENERATION, 60, 0));
            }
        }
    }
}
