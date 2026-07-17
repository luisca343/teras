package es.boffmedia.teras.client.funko;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import es.boffmedia.teras.blockentity.FunkoBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Draws a placed funko: the {@link FunkoModel} statue textured with the block entity's skin. */
@OnlyIn(Dist.CLIENT)
public class FunkoRenderer implements BlockEntityRenderer<FunkoBlockEntity> {

    public FunkoRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FunkoBlockEntity blockEntity, float partialTicks, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        BlockState state = blockEntity.getBlockState();
        Direction facing = state.hasProperty(HorizontalDirectionalBlock.FACING)
                ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;

        FunkoBreakParticles.remember(blockEntity.getBlockPos(),
                FunkoSkin.getSkinTexture(blockEntity.getOwnerProfile(), blockEntity.getSkinFile()));
        VertexConsumer builder = buffers.getBuffer(
                FunkoSkin.getRenderType(blockEntity.getOwnerProfile(), blockEntity.getSkinFile()));

        poseStack.pushPose();
        // Rotate the figure (front = +Z) so it points along the FACING direction, around the block centre.
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(-0.5D, 0.0D, -0.5D);

        FunkoModel.render(poseStack, builder, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
