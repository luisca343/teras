package es.boffmedia.teras.client.funko;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import es.boffmedia.teras.items.FunkoItem;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.tileentity.ItemStackTileEntityRenderer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Draws the funko skin on the item in inventory/hand. The item model is
 * {@code builtin/entity}, whose display transforms position the figure; we just render the
 * model with the correct skin resolved from the stack's {@code BlockEntityTag} NBT.
 */
@OnlyIn(Dist.CLIENT)
public class FunkoItemRenderer extends ItemStackTileEntityRenderer {

    @Override
    public void renderByItem(ItemStack stack, ItemCameraTransforms.TransformType transform, MatrixStack matrixStack,
                             IRenderTypeBuffer buffers, int packedLight, int packedOverlay) {
        String owner = FunkoItem.getOwnerName(stack);
        String file = FunkoItem.getSkinFile(stack);
        IVertexBuilder builder = buffers.getBuffer(FunkoSkin.getRenderTypeForOwner(owner, file));

        // The model only occupies the lower half of the block (feet at y=0, head at y=8px) and is
        // centred near x/z=8px. Shift it so its centre sits at the block centre, which is the pivot
        // ItemRenderer uses (it applies translate(-0.5,-0.5,-0.5) before us). This keeps the figure
        // centred and upright for every display context; per-context scale lives in the item model.
        matrixStack.pushPose();
        matrixStack.translate(0.5F - 8.5F / 16.0F, 0.5F - 4.0F / 16.0F, 0.5F - 8.25F / 16.0F);
        FunkoModel.render(matrixStack, builder, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        matrixStack.popPose();
    }
}
