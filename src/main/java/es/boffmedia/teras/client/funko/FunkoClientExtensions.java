package es.boffmedia.teras.client.funko;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.init.BlockEntityInit;
import es.boffmedia.teras.init.BlockInit;
import es.boffmedia.teras.init.ItemInit;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Client-only funko wiring: the block-entity renderer, the inventory/hand item renderer, and
 * skin-sampled break/hit particles.
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FunkoClientExtensions {
    private FunkoClientExtensions() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlockEntityInit.FUNKO.get(), FunkoRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerBlock(BLOCK_EXTENSIONS, BlockInit.FUNKO.get());
        event.registerItem(ITEM_EXTENSIONS, ItemInit.FUNKO.get());
    }

    private static final IClientBlockExtensions BLOCK_EXTENSIONS = new IClientBlockExtensions() {
        @Override
        public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine manager) {
            return FunkoBreakParticles.addDestroyEffects(level, pos, manager);
        }

        @Override
        public boolean addHitEffects(BlockState state, Level level, HitResult target, ParticleEngine manager) {
            if (target instanceof BlockHitResult blockHit) {
                return FunkoBreakParticles.addHitEffects(level, blockHit.getBlockPos(), target.getLocation(), manager);
            }
            return false;
        }
    };

    private static final IClientItemExtensions ITEM_EXTENSIONS = new IClientItemExtensions() {
        // Lazy: the renderer resolves Minecraft's dispatchers in its constructor, which are not ready
        // when this field is initialised.
        private BlockEntityWithoutLevelRenderer renderer;

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (this.renderer == null) {
                this.renderer = new FunkoItemRenderer();
            }
            return this.renderer;
        }
    };
}
