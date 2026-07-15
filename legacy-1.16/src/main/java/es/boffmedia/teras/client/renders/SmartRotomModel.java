package es.boffmedia.teras.client.renders;

// Made with Blockbench 4.3.1
// Exported for Minecraft version 1.15 - 1.16 with Mojang mappings
// Paste this class into your mod and generate all required imports


import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.entity.Entity;

public class SmartRotomModel extends EntityModel<Entity> {
    private final ModelRenderer bone;

    public SmartRotomModel() {
        texWidth = 128;
        texHeight = 128;

        bone = new ModelRenderer(this);
        bone.setPos(8.0F, 24.0F, -8.0F);
        bone.texOffs(0, 0).addBox(-12.75F, -6.875F, 7.5F, 9.0F, 5.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-8.25F, -9.125F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, true);
        bone.texOffs(0, 10).addBox(-8.25F, -9.125F, 7.5F, 1.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-12.75F, -6.875F, 8.25F, 9.0F, 5.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-8.5F, -8.875F, 8.25F, 1.0F, 2.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-3.0F, -6.625F, 8.25F, 0.0F, 5.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-13.0F, -6.625F, 8.25F, 0.0F, 5.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-7.0F, -9.875F, 8.25F, 0.0F, 1.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-8.25F, -1.125F, 8.25F, 0.0F, 1.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-8.25F, -9.125F, 8.25F, 1.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-8.75F, -7.625F, 8.25F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-8.5F, -1.125F, 8.25F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-7.0F, -8.125F, 8.25F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-6.75F, -10.125F, 8.25F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-7.5F, -1.125F, 8.25F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-6.25F, -10.125F, 7.5F, 0.0F, 2.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-8.5F, -8.875F, 7.5F, 0.0F, 1.0F, 0.0F, 0.0F, true);
        bone.texOffs(0, 11).addBox(-8.5F, -8.875F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-6.5F, -8.125F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-7.0F, -7.875F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-6.5F, -8.125F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-7.0F, -7.875F, 7.5F, 0.0F, 1.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-8.75F, -7.625F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, true);
        bone.texOffs(0, 11).addBox(-8.75F, -7.625F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-12.75F, -6.875F, 8.0F, 0.0F, 0.0F, 0.0F, 0.0F, true);
        bone.texOffs(0, 11).addBox(-12.5F, -6.875F, 7.5F, 3.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 11).addBox(-12.75F, -6.875F, 8.0F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 11).addBox(-12.75F, -6.875F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-12.75F, -6.875F, 8.0F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 6).addBox(-7.0F, -6.875F, 7.5F, 3.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 11).addBox(-3.25F, -6.875F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 11).addBox(-3.25F, -6.875F, 8.0F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-3.0F, -6.875F, 8.0F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-3.25F, -6.875F, 8.0F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-13.0F, -6.625F, 7.5F, 0.0F, 5.0F, 0.0F, 0.0F, true);
        bone.texOffs(0, 12).addBox(-13.0F, -6.625F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 11).addBox(-12.75F, -6.625F, 7.75F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-2.75F, -6.625F, 7.5F, 0.0F, 5.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 6).addBox(-3.0F, -6.625F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 6).addBox(-3.25F, -6.625F, 7.75F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-13.0F, -1.375F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-3.0F, -1.375F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-12.75F, -1.375F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-12.75F, -1.125F, 7.5F, 4.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-7.25F, -1.125F, 7.5F, 4.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-3.0F, -1.375F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-8.5F, -1.125F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, true);
        bone.texOffs(0, 0).addBox(-7.25F, -1.125F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-8.5F, -0.625F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-7.5F, -0.625F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-8.25F, -0.625F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, true);
        bone.texOffs(0, 0).addBox(-7.5F, -0.625F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-7.5F, -0.125F, 7.75F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-8.25F, 0.125F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-7.75F, 0.125F, 7.75F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-6.75F, -10.125F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, true);
        bone.texOffs(0, 10).addBox(-6.75F, -10.125F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-7.0F, -9.875F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, true);
        bone.texOffs(0, 11).addBox(-7.0F, -9.875F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-12.5F, -6.875F, 7.75F, 0.0F, 0.0F, 0.0F, 0.0F, true);
        bone.texOffs(0, 0).addBox(-3.25F, -6.875F, 7.75F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-8.5F, -8.875F, 7.5F, 1.0F, 2.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-3.0F, -6.625F, 7.5F, 0.0F, 5.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-13.0F, -6.625F, 7.5F, 0.0F, 5.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-7.0F, -9.875F, 7.5F, 0.0F, 1.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-8.25F, -1.125F, 7.5F, 0.0F, 1.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-8.25F, -9.125F, 7.5F, 1.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-8.75F, -7.625F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-8.25F, -0.125F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-7.0F, -8.125F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-7.5F, -1.125F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-8.5F, -1.125F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-6.75F, -10.125F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-12.75F, -6.875F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, true);
        bone.texOffs(0, 0).addBox(-12.75F, -6.875F, 7.75F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-3.0F, -6.875F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-3.25F, -6.875F, 7.75F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-7.5F, -0.125F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-7.75F, 0.125F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
        bone.texOffs(0, 0).addBox(-7.75F, -0.125F, 7.5F, 0.0F, 0.0F, 0.0F, 0.0F, false);
    }

    @Override
    public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch){
        //previously the render function, render code was moved to a method below
    }

    @Override
    public void renderToBuffer(MatrixStack matrixStack, IVertexBuilder buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha){
        bone.render(matrixStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    public void setRotationAngle(ModelRenderer modelRenderer, float x, float y, float z) {
        modelRenderer.xRot = x;
        modelRenderer.yRot = y;
        modelRenderer.zRot = z;
    }
}