package es.boffmedia.teras.client.renders;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.client.ClientProxy;
import es.boffmedia.teras.client.gui.PantallaSmartRotom;
import es.boffmedia.teras.init.ItemInit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.PlayerRenderer;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;

import static org.lwjgl.opengl.GL11.*;

@OnlyIn(Dist.CLIENT)
public final class SmartRotomRenderer implements IItemRenderer {

    private static final float PI = (float) Math.PI;
    private final Minecraft mc = Minecraft.getInstance();
    private float sinSqrtSwingProg1;
    private float sinSqrtSwingProg2;
    private float sinSwingProg1;
    private float sinSwingProg2;

    public static void drawAxis() {
        glDisable(GL_TEXTURE_2D);
        glBegin(GL_LINES);
        glColor4f(1.f, 0.f, 0.f, 1.f); glVertex3d(0.0, 0.0, 0.0);
        glColor4f(1.f, 0.f, 0.f, 1.f); glVertex3d(5.0, 0.0, 0.0);
        glColor4f(0.f, 1.f, 0.f, 1.f); glVertex3d(0.0, 0.0, 0.0);
        glColor4f(0.f, 1.f, 0.f, 1.f); glVertex3d(0.0, 5.0, 0.0);
        glColor4f(0.f, 0.f, 1.f, 1.f); glVertex3d(0.0, 0.0, 0.0);
        glColor4f(0.f, 0.f, 1.f, 1.f); glVertex3d(0.0, 0.0, 5.0);
        glEnd();
        glEnable(GL_TEXTURE_2D);
    }


    @Override
    public final void render(MatrixStack stack, ItemStack is, float handSideSign, float swingProgress, float equipProgress, IRenderTypeBuffer buffer, int packedLight) {
        if(mc.screen instanceof PantallaSmartRotom) return;
        //Pre-compute values
        float sqrtSwingProg = (float) Math.sqrt(swingProgress);
        sinSqrtSwingProg1 = (float) Math.sin(sqrtSwingProg * PI);
        sinSqrtSwingProg2 = (float) Math.sin(sqrtSwingProg * PI * 2.0f);
        sinSwingProg1 = (float) Math.sin(swingProgress * PI);
        sinSwingProg2 = (float) Math.sin(swingProgress * swingProgress * PI);

        RenderSystem.disableCull();
//        glEnable(GL_RESCALE_NORMAL);

        //Render arm
        stack.pushPose();
        renderArmFirstPerson(stack, packedLight, equipProgress, buffer, handSideSign);
        stack.popPose();


        stack.pushPose();
        stack.translate(handSideSign * -0.4f * sinSqrtSwingProg1, 0.2f * sinSqrtSwingProg2, -0.2f * sinSwingProg1);
        stack.translate(handSideSign * 0.56f, -0.52f - equipProgress * 0.6f, -0.72f);

        if(handSideSign >= 0.0f)
            renderModel(stack, buffer, packedLight,  ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND);
        else
            renderModel(stack, buffer, packedLight,  ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND);

        //Prepare minePad transform
        stack.mulPose(Vector3f.YP.rotationDegrees(handSideSign * (45.0f - sinSwingProg2 * 20.0f)));
        stack.mulPose(Vector3f.ZP.rotationDegrees(handSideSign * sinSqrtSwingProg1 * -20.0f));
        stack.mulPose(Vector3f.XP.rotationDegrees(sinSqrtSwingProg1 * -80.0f));
        stack.mulPose(Vector3f.YP.rotationDegrees(handSideSign * -45.0f));

        if(handSideSign >= 0.0f)
            stack.translate(-1.065f, 0.0f, 0.0f);
        else {
            stack.translate(0.0f, 0.0f, -0.2f);
            stack.mulPose(Vector3f.YP.rotationDegrees(20.0f));
            stack.translate(-0.475f, -0.1f, 0.0f);
            stack.mulPose(Vector3f.ZP.rotationDegrees(1.0f));
        }

        //Render web view
        boolean existePad = true;
        if(is.getTag() != null && is.getTag().contains("PadID")) {
            int id = is.getTag().getInt("PadID");
            ClientProxy.PadData pd = Teras.PROXY.getPadByID(id);
            if(pd != null) {
                stack.translate(0.063f, 0.28f, 0.001f);
                GlStateManager._disableDepthTest();
                GlStateManager._enableTexture();
                pd.view.draw(stack,0.0, 0.0, 27.65 / 32.0 + 0.01, 14.0 / 32.0 + 0.002);
                GlStateManager._enableDepthTest();
            }
            else{
                existePad = false;
            }
        }else{
            existePad = false;
        }
        if(!existePad){
            /* Pantalla falsa placeholder  */
            Matrix4f positionMatrix = stack.last().pose();
            Tessellator t = Tessellator.getInstance();
            BufferBuilder vb = t.getBuilder();

            GlStateManager._bindTexture(0);
            vb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
            stack.translate(0.063f, 0.28f, 0.001f);

            vb.vertex(positionMatrix, (float) 0.0, (float) 0.0, 0.0f).uv(0.0f, 1.0f).color(0, 0, 0, 255).endVertex();
            vb.vertex(positionMatrix, (float) (27.65 / 32.0 + 0.01), (float) 0.0, 0.0f).uv(1.f, 1.f).color(0, 0, 0, 255).endVertex();
            vb.vertex(positionMatrix, (float) (27.65 / 32.0 + 0.01), (float) (14.0 / 32.0 + 0.002), 0.0f).uv(1.f, 0.0f).color(0, 0, 0, 255).endVertex();
            vb.vertex(positionMatrix, (float) 0.0, (float) (14.0 / 32.0 + 0.002), 0.0f).uv(0.0f, 0.0f).color(0, 0, 0, 255).endVertex();
            t.end();
        }




        stack.popPose();
//        glDisable(GL_RESCALE_NORMAL);
        RenderSystem.enableCull();



    }

    private void renderModel(MatrixStack stack, IRenderTypeBuffer buffer, int packedLight, ItemCameraTransforms.TransformType transform) {
        stack.clear();
        Minecraft.getInstance().getTextureManager().bind(new ResourceLocation("teras:textures/item/smartrotom.png"));
        Item smart = ItemInit.SMARTROTOM.get();

        mc.getItemInHandRenderer().renderItem(mc.player, smart.getDefaultInstance(), transform , false, stack, buffer, packedLight);



        stack.pushPose();
        stack.popPose();
    }

    private void renderArmFirstPerson(MatrixStack stack , int combinedLight, float equipProgress, IRenderTypeBuffer buffer, float handSideSign) {
        float tx = -0.3f * sinSqrtSwingProg1;
        float ty = 0.4f * sinSqrtSwingProg2;
        float tz = -0.4f * sinSwingProg1;

        stack.translate(handSideSign * (tx + 0.64000005f), ty - 0.6f - equipProgress * 0.6f, tz - 0.71999997f);
        stack.mulPose(Vector3f.YP.rotationDegrees(handSideSign * 45.0f));
        stack.mulPose(Vector3f.YP.rotationDegrees(handSideSign * sinSqrtSwingProg1 * 70.0f));
        stack.mulPose(Vector3f.ZP.rotationDegrees(handSideSign * sinSwingProg2 * -20.0f));
        stack.translate(-handSideSign, 3.6f, 3.5f);
        stack.mulPose(Vector3f.ZP.rotationDegrees(handSideSign * 120.0f));
        stack.mulPose(Vector3f.XP.rotationDegrees(200.0f));
        stack.mulPose(Vector3f.YP.rotationDegrees(handSideSign * -135.0f));
        stack.translate(handSideSign * 5.6f, 0.0f, 0.0f);

        PlayerRenderer playerRenderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(mc.player);
        mc.getTextureManager().bind(mc.player.getSkinTextureLocation());

        if(handSideSign >= 0.0f)
            playerRenderer.renderRightHand(stack, buffer, combinedLight, mc.player);
        else
            playerRenderer.renderLeftHand(stack, buffer, combinedLight, mc.player);
    }


 

}
