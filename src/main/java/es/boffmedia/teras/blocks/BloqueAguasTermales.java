package es.boffmedia.teras.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

public class BloqueAguasTermales extends LiquidBlock {

    private static final int REGEN_TICKS = 60;

    public BloqueAguasTermales(FlowingFluid fluid, Properties props) {
        super(fluid, props);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (!level.isClientSide && entity instanceof Player player && !player.hasEffect(MobEffects.REGENERATION)) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_TICKS, 0));
        }
    }
}
