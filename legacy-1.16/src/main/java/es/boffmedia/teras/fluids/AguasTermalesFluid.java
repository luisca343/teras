package es.boffmedia.teras.fluids;

import es.boffmedia.teras.init.FluidInit;
import es.boffmedia.teras.init.ItemInit;
import net.minecraft.fluid.FluidState;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.ForgeFlowingFluid;

import java.util.Random;

public abstract class AguasTermalesFluid extends ForgeFlowingFluid {
    protected AguasTermalesFluid() {
        super(new ForgeFlowingFluid.Properties(
                FluidInit.AGUAS_TERMALES_SOURCE, FluidInit.AGUAS_TERMALES_FLOWING, FluidAttributes.builder(
                        FluidInit.WATER_STILL, FluidInit.WATER_FLOW).overlay(FluidInit.WATER_OVERLAY).color(0xFF40d9f7))
                .block(FluidInit.AGUAS_TERMALES_BLOCK).bucket(ItemInit.CUBO_AGUAS_TERMALES).canMultiply()
        );
    }


    @Override
    protected boolean isRandomlyTicking() {
        return true;
    }

    public static class Source extends ForgeFlowingFluid.Source {
        public Source(Properties properties) {
            super(properties);
        }

        @Override
        public boolean isRandomlyTicking() {
            return true;
        }

        @Override
        protected void animateTick(World world, BlockPos pos, FluidState state, Random random) {
            BlockPos abovePos = pos.above();
            // Add steam above source blocks
            if (world.isEmptyBlock(abovePos)) {
                if (random.nextInt(20) == 0) {
                    world.addParticle(ParticleTypes.CLOUD, (double) pos.getX() + random.nextDouble(), (double) pos.getY() + 0.8D, (double) pos.getZ()  + random.nextDouble(), 0.0D, 0.05D, 0.0D);
                }
            }
        }
    }



    public static class Flowing extends ForgeFlowingFluid.Flowing {
        public Flowing(Properties properties) {
            super(properties);
        }
    }

}
