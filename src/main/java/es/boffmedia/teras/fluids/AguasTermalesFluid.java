package es.boffmedia.teras.fluids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * Hot springs. The 1.16.5 abstract parent was dead code — its nested Source/Flowing extended
 * {@code ForgeFlowingFluid}'s directly, never it — so only the steam and its random tick survive.
 */
public final class AguasTermalesFluid {
    private AguasTermalesFluid() {}

    public static class Source extends BaseFlowingFluid.Source {
        public Source(Properties properties) {
            super(properties);
        }

        @Override
        protected boolean isRandomlyTicking() {
            return true;
        }

        @Override
        protected void animateTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
            if (level.isEmptyBlock(pos.above()) && random.nextInt(20) == 0) {
                level.addParticle(ParticleTypes.CLOUD,
                        pos.getX() + random.nextDouble(), pos.getY() + 0.8D, pos.getZ() + random.nextDouble(),
                        0.0D, 0.05D, 0.0D);
            }
        }
    }

    public static class Flowing extends BaseFlowingFluid.Flowing {
        public Flowing(Properties properties) {
            super(properties);
        }
    }
}
