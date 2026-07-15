package es.boffmedia.teras.tileentity;


import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.math.BlockSide;
import es.boffmedia.teras.util.string.MessageHelper;
import es.boffmedia.teras.util.math.vector.Vector3f;
import es.boffmedia.teras.util.math.vector.Vector3i;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;

import static net.minecraft.util.math.vector.Vector3f.*;

@OnlyIn(Dist.CLIENT)
public class PantallaRenderer extends TileEntityRenderer<PantallaTE> {

    private final Vector3f mid = new Vector3f();
    private final Vector3i tmpi = new Vector3i(0,0,0);
    private final Vector3f tmpf = new Vector3f();

    public PantallaRenderer(TileEntityRendererDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public void render(PantallaTE te, float p_225616_2_, MatrixStack matrixStack, IRenderTypeBuffer renderTypeBuffer, int combinedLightIn, int combinedOverlayIn) {

        if(!te.isLoaded()){
            return;
        }

        if (te.browser == null) {
            te.browser = Teras.getInstance().getAPI().createBrowser("http://google.es", false);
            te.browser.loadURL("http://google.es");
            if(!te.browser.getURL().contains("liga")){
                te.browser.resize(1920, 1080);
            }

            return;
        }

        if (te.browser.getTextureID() == 0) {
            MessageHelper.enviarMensaje(Minecraft.getInstance().player, "Texture is null");
            //Minecraft.getInstance().player.sendMessage(new StringTextComponent("Texture is null"), UUID.randomUUID());
            return;
        }


        GlStateManager._disableDepthTest();
        GlStateManager._enableTexture();

        Tessellator tesselator = Tessellator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        //TODO: don't use tesselator
        RenderSystem.enableDepthTest();
        //with forge 1.16.5 bind texture to RenderSystem

        int sizeX = te.ancho;
        int sizeY = te.alto;
        /*
        float sw = ((float) sizeX) * 0.6f - 2.f / 16.f;
        float sh = ((float) sizeY) * 0.6f - 2.f / 16.f;*/


        float sw = ((float) sizeX) * 0.5f - 2.f / 16.f;
        float sh = ((float) sizeY) * 0.5f - 2.f / 16.f;


        matrixStack.pushPose();

        Tessellator t = Tessellator.getInstance();
        BufferBuilder vb = t.getBuilder();

        RenderSystem.enableTexture();
        RenderSystem.disableBlend();

        //enviar mensaje al jugador con la orientación de la pantalla
        /**/
        BlockSide lado = te.orientacionPantalla;


        tmpi.set(lado.right);
        tmpi.mul(sizeX);
        tmpi.addMul(lado.up, sizeY);
        tmpf.set(tmpi);
        mid.set(0.5, 0.5, 0.5);
        mid.addMul(tmpf, 0.5f);
        tmpf.set(lado.left);
        mid.addMul(tmpf, 0.5f);
        tmpf.set(lado.down);
        mid.addMul(tmpf, 0.5f);

        matrixStack.translate(mid.x, mid.y, mid.z);



        switch (lado){
            case TOP:
                matrixStack.mulPose(XP.rotationDegrees(90.f + 49.8f));
                break;
            case BOTTOM:
                matrixStack.mulPose(XN.rotation(90.f + 49.8f));
                break;
            case NORTH:
                matrixStack.mulPose(YN.rotationDegrees(180.f));
                break;
            case SOUTH:
                break;
            case WEST:
                matrixStack.mulPose(YN.rotationDegrees(90.f));
                break;
            default:
                matrixStack.mulPose(YP.rotationDegrees(90.f));
                break;
        }



        RenderSystem.enableDepthTest();
        GlStateManager._bindTexture(te.browser.getTextureID() );
        RenderSystem.color4f(1.0f, 1.0f, 1.0f, 1.0f);

        vb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);

        builder.vertex(matrixStack.last().pose(), -sw, -sh, 0.505f).uv(0.f, 1.f).color(1.f, 1.f, 1.f, 1.f).endVertex();
        builder.vertex(matrixStack.last().pose(), sw, -sh, 0.505f).uv(1.f, 1.f).color(1.f, 1.f, 1.f, 1.f).endVertex();
        builder.vertex(matrixStack.last().pose(), sw, sh, 0.505f).uv(1.f, 0.f).color(1.f, 1.f, 1.f, 1.f).endVertex();
        builder.vertex(matrixStack.last().pose(), -sw, sh, 0.505f).uv(0.f, 0.f).color(1.f, 1.f, 1.f, 1.f).endVertex();
        tesselator.end();//Minecraft does shit with mah texture otherwise...
        RenderSystem.disableDepthTest();

        matrixStack.popPose();

        GlStateManager._enableDepthTest();

    }



    public static void renderCosa(){

    }
}
