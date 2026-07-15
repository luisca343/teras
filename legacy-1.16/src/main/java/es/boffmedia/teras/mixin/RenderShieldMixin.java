package es.boffmedia.teras.mixin;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import yesman.epicfight.api.client.model.ClientModel;
import yesman.epicfight.api.client.model.ClientModels;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;
import yesman.epicfight.client.renderer.patched.item.RenderItemBase;
import yesman.epicfight.client.renderer.patched.item.RenderShield;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

@Mixin(RenderShield.class)
public class RenderShieldMixin extends RenderItemBase {

    /**
     * @author
     * @reason
     */
    @Overwrite
    public void renderItemInHand(ItemStack stack, LivingEntityPatch<?> entitypatch, Hand hand, IRenderTypeBuffer buffer, MatrixStack poseStack, int packedLight) {
        OpenMatrix4f modelMatrix = this.getCorrectionMatrix(stack, entitypatch, hand);
        String holdingHand = hand == Hand.MAIN_HAND ? "Tool_R" : "Tool_L";
        OpenMatrix4f jointTransform = ((ClientModel)entitypatch.getEntityModel(ClientModels.LOGICAL_CLIENT))
                .getArmature().searchJointByName(holdingHand).getAnimatedTransform();

        if (hand == Hand.OFF_HAND) {

            CompoundNBT nbt = stack.serializeNBT();
            // Check the damage
            CompoundNBT tag = (CompoundNBT) nbt.get("tag");
            assert tag != null;
            if(tag.contains("ArmourersWorkshop")) {
                jointTransform = jointTransform.translate(0.0F, -1F, 0.0F);
            }



        }

        modelMatrix.mulFront(jointTransform);
        poseStack.pushPose();
        this.mulPoseStack(poseStack, modelMatrix);
        ItemCameraTransforms.TransformType transformType = hand == Hand.MAIN_HAND ? ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND : ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND;
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, transformType, packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffer);
        poseStack.popPose();
        GlStateManager._enableDepthTest();
    }
}