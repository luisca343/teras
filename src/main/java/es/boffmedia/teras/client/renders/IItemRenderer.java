package es.boffmedia.teras.client.renders;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Tiny first-person item renderer contract, ported from 1.16.5.
 * {@code handSideSign} is {@code +1} for the main (right) hand and {@code -1} for the off (left) hand.
 */
@OnlyIn(Dist.CLIENT)
public interface IItemRenderer {

    void render(PoseStack stack, ItemStack is, float handSideSign, float swingProgress,
                float equipProgress, MultiBufferSource buffer, int packedLight);
}
