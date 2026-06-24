package es.boffmedia.teras.tileentity;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import es.boffmedia.teras.client.funko.FunkoBreakParticles;
import es.boffmedia.teras.client.funko.FunkoModel;
import es.boffmedia.teras.client.funko.FunkoSkin;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalBlock;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.util.Direction;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FunkoTERenderer extends TileEntityRenderer<FunkoTE> {

    public FunkoTERenderer(TileEntityRendererDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public void render(FunkoTE te, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffers,
                       int packedLight, int packedOverlay) {
        BlockState state = te.getBlockState();
        Direction facing = state.hasProperty(HorizontalBlock.FACING)
                ? state.getValue(HorizontalBlock.FACING) : Direction.NORTH;

        FunkoBreakParticles.remember(te.getBlockPos(), FunkoSkin.getSkinTexture(te.getOwnerProfile(), te.getSkinFile()));
        IVertexBuilder builder = buffers.getBuffer(FunkoSkin.getRenderType(te.getOwnerProfile(), te.getSkinFile()));

        matrixStack.pushPose();
        // Rotate the figure (front = +Z) so it points along the FACING direction, around the block centre.
        matrixStack.translate(0.5D, 0.0D, 0.5D);
        matrixStack.mulPose(Vector3f.YP.rotationDegrees(-facing.toYRot()));
        matrixStack.translate(-0.5D, 0.0D, -0.5D);

        FunkoModel.render(matrixStack, builder, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        matrixStack.popPose();
    }
}
