package es.boffmedia.teras.mixin;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.client.renderer.entity.layers.BipedArmorLayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.client.renderer.entity.model.PlayerModel;
import net.minecraft.client.settings.PointOfView;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.api.animation.AnimationPlayer;
import yesman.epicfight.api.client.animation.Layer;
import yesman.epicfight.api.client.model.ClientModel;
import yesman.epicfight.api.client.model.ClientModels;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.MathUtils;
import yesman.epicfight.client.renderer.patched.entity.PatchedEntityRenderer;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;
import yesman.epicfight.client.renderer.patched.layer.EmptyLayer;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.api.utils.math.OpenMatrix4f;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Mixin(PatchedLivingEntityRenderer.class)
public abstract class PatchedLivingEntityRendererMixin<E extends LivingEntity, T extends LivingEntityPatch<E>, M extends EntityModel<E>> extends PatchedEntityRenderer<E, T, LivingRenderer<E, M>> {

    @Shadow
    public abstract void render(E entityIn, T entitypatch, LivingRenderer<E, M> renderer, IRenderTypeBuffer buffer, MatrixStack poseStack, int packedLight, float partialTicks);
    @Shadow
    public abstract void mulPoseStack(MatrixStack poseStack, Armature armature, E entityIn, T entitypatch, float partialTicks);
    @Shadow
    private Map<Class<?>, PatchedLayer<E, T, M, ? extends LayerRenderer<E, M>>> patchedLayers;
    @Shadow
    protected abstract double getLayerCorrection();
    @Shadow
    protected abstract int getRootJointIndex();

    Minecraft mc;

    @Inject(method = "<init>", at = @At("RETURN"))
    public void onConstructor(CallbackInfo ci) {
        this.mc = Minecraft.getInstance();
    }


    @Redirect(method = "renderLayer", at = @At(value = "INVOKE", target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V"))
    private void redirectForEach(List<LayerRenderer<E, M>> layers, Consumer<LayerRenderer<E, M>> action, LivingRenderer<E, M> renderer, T entitypatch, E entityIn, OpenMatrix4f[] poses, IRenderTypeBuffer buffer, MatrixStack poseStack, int packedLightIn, float partialTicks) {
        List<String> layersToSkip = Arrays.asList("LayerEquippables", "MekanismArmorLayer", "SkinWardrobeLayer");
        layers.forEach(layer -> {
            if(mc.options.getCameraType() == PointOfView.FIRST_PERSON
                    && entityIn.equals(mc.player)
                    && layersToSkip.contains(layer.getClass().getSimpleName())
            ){
               return;
            }
            action.accept(layer);
        });
    }

}