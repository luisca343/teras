package es.boffmedia.teras.client.funko;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import es.boffmedia.teras.items.FunkoItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the funko skin on the item in inventory/hand. The item model is {@code builtin/entity}, whose
 * display transforms position the figure; we just render the model with the skin resolved from the
 * stack's components.
 */
@OnlyIn(Dist.CLIENT)
public class FunkoItemRenderer extends BlockEntityWithoutLevelRenderer {

    public FunkoItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        ResolvableProfile profile = FunkoItem.getOwner(stack);
        if (profile != null && !profile.isResolved()) {
            // No-ops in multiplayer (the profile caches are server-side); the server's resolved
            // profile arrives with the stack instead. Single-player resolves here.
            stack.remove(DataComponents.PROFILE);
            profile.resolve().thenAcceptAsync(resolved -> stack.set(DataComponents.PROFILE, resolved),
                    Minecraft.getInstance());
            profile = null;
        }
        VertexConsumer builder = buffers.getBuffer(FunkoSkin.getRenderType(profile, FunkoItem.getSkinFile(stack)));

        // The model only occupies the lower half of the block (feet at y=0, head at y=8px) and is
        // centred near x/z=8px. Shift it so its centre sits at the block centre, which is the pivot
        // ItemRenderer uses (it applies translate(-0.5,-0.5,-0.5) before us). This keeps the figure
        // centred and upright for every display context; per-context scale lives in the item model.
        poseStack.pushPose();
        poseStack.translate(0.5F - 8.5F / 16.0F, 0.5F - 4.0F / 16.0F, 0.5F - 8.25F / 16.0F);
        FunkoModel.render(poseStack, builder, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
