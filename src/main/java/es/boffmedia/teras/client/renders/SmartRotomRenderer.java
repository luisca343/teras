package es.boffmedia.teras.client.renders;

import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import es.boffmedia.teras.client.gui.PantallaSmartRotom;
import es.boffmedia.teras.items.SmartRotom;
import es.boffmedia.teras.mcef.TerasMCEF;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * First-person (in-hand) SmartRotom renderer, ported from the 1.16.5 montoyo-based version.
 *
 * <p>On a {@code RenderHandEvent} (dispatched from {@link es.boffmedia.teras.client.ClientEvents})
 * it cancels the vanilla hand render and instead: manually renders the player's arm, renders the
 * item model, then draws the live MCEF browser texture as a quad on the model's screen area, at the
 * legacy model coordinates {@code (0,0) -> (27.65/32, 14/32)}. When the SmartRotom is off (no
 * browser) nothing is drawn on the screen area — the model's own screen face shows through.</p>
 *
 * <p>Differences from the 1.16.5 original are limited to API surface (montoyo {@code IBrowser.draw}
 * → a hand-rolled textured quad from {@code getRenderer().getTextureID()}; {@code MatrixStack} →
 * {@code PoseStack}; {@code Vector3f.YP} → {@code Axis.YP}). Like the original, each item draws its
 * <b>own</b> browser: the browser is looked up by the item's {@code smartrotom_id} via
 * {@link TerasMCEF#getBrowser(java.util.UUID)} (the old {@code pad}/{@code PadID} vocabulary retired).</p>
 */
@OnlyIn(Dist.CLIENT)
public final class SmartRotomRenderer implements IItemRenderer {

    private static final float PI = (float) Math.PI;

    /** Screen quad extent on the model, in item-model space (from the 1.16.5 SmartRotom geometry). */
    private static final float SCREEN_W = 27.65f / 32.0f + 0.01f;
    private static final float SCREEN_H = 14.0f / 32.0f + 0.002f;

    private final Minecraft mc = Minecraft.getInstance();
    private float sinSqrtSwingProg1;
    private float sinSqrtSwingProg2;
    private float sinSwingProg1;
    private float sinSwingProg2;

    @Override
    public void render(PoseStack stack, ItemStack is, float handSideSign, float swingProgress,
                       float equipProgress, MultiBufferSource buffer, int packedLight) {
        // The full-screen browser owns the browser while it is open; don't double-render in-hand.
        if (mc.screen instanceof PantallaSmartRotom) return;

        // Pre-compute swing curves (identical to 1.16.5).
        float sqrtSwingProg = (float) Math.sqrt(swingProgress);
        sinSqrtSwingProg1 = (float) Math.sin(sqrtSwingProg * PI);
        sinSqrtSwingProg2 = (float) Math.sin(sqrtSwingProg * PI * 2.0f);
        sinSwingProg1 = (float) Math.sin(swingProgress * PI);
        sinSwingProg2 = (float) Math.sin(swingProgress * swingProgress * PI);

        RenderSystem.disableCull();

        // Render arm.
        stack.pushPose();
        renderArmFirstPerson(stack, packedLight, equipProgress, buffer, handSideSign);
        stack.popPose();

        stack.pushPose();
        stack.translate(handSideSign * -0.4f * sinSqrtSwingProg1, 0.2f * sinSqrtSwingProg2, -0.2f * sinSwingProg1);
        stack.translate(handSideSign * 0.56f, -0.52f - equipProgress * 0.6f, -0.72f);

        renderModel(stack, is, buffer, packedLight, handSideSign >= 0.0f
                ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                : ItemDisplayContext.FIRST_PERSON_LEFT_HAND);

        // Prepare the SmartRotom screen transform.
        stack.mulPose(Axis.YP.rotationDegrees(handSideSign * (45.0f - sinSwingProg2 * 20.0f)));
        stack.mulPose(Axis.ZP.rotationDegrees(handSideSign * sinSqrtSwingProg1 * -20.0f));
        stack.mulPose(Axis.XP.rotationDegrees(sinSqrtSwingProg1 * -80.0f));
        stack.mulPose(Axis.YP.rotationDegrees(handSideSign * -45.0f));

        if (handSideSign >= 0.0f) {
            stack.translate(-1.065f, 0.0f, 0.0f);
        } else {
            stack.translate(0.0f, 0.0f, -0.2f);
            stack.mulPose(Axis.YP.rotationDegrees(20.0f));
            stack.translate(-0.475f, -0.1f, 0.0f);
            stack.mulPose(Axis.ZP.rotationDegrees(1.0f));
        }

        // Render THIS item's own browser onto the model's screen area (each SmartRotom has its own,
        // keyed by its smartrotom_id). When the SmartRotom is off (no browser for this id) draw
        // nothing at all — the model's own screen face is what shows through.
        MCEFBrowser browserView = TerasMCEF.getBrowser(SmartRotom.getId(is));
        if (browserView != null) {
            // The arm/item were drawn into the buffered MultiBufferSource. Flush it now so the
            // immediate-mode browser quad below lands on top of the model instead of behind it.
            // (1.16.5's montoyo IBrowser.draw was immediate too; forcing the flush here makes the
            // draw order explicit under NeoForge's buffered hand-render pass.)
            if (buffer instanceof MultiBufferSource.BufferSource bufferSource) {
                bufferSource.endBatch();
            }
            stack.translate(0.063f, 0.28f, 0.001f);
            drawScreenQuad(stack.last().pose(), browserView.getRenderer().getTextureID());
        }

        stack.popPose();
        RenderSystem.enableCull();
    }

    /** Lets Minecraft pick the correct model/texture for the stack (CustomModelData / overrides). */
    private void renderModel(PoseStack stack, ItemStack is, MultiBufferSource buffer, int packedLight,
                             ItemDisplayContext transform) {
        mc.getEntityRenderDispatcher().getItemInHandRenderer()
                .renderItem(mc.player, is, transform, false, stack, buffer, packedLight);
    }

    private void renderArmFirstPerson(PoseStack stack, int combinedLight, float equipProgress,
                                      MultiBufferSource buffer, float handSideSign) {
        float tx = -0.3f * sinSqrtSwingProg1;
        float ty = 0.4f * sinSqrtSwingProg2;
        float tz = -0.4f * sinSwingProg1;

        stack.translate(handSideSign * (tx + 0.64000005f), ty - 0.6f - equipProgress * 0.6f, tz - 0.71999997f);
        stack.mulPose(Axis.YP.rotationDegrees(handSideSign * 45.0f));
        stack.mulPose(Axis.YP.rotationDegrees(handSideSign * sinSqrtSwingProg1 * 70.0f));
        stack.mulPose(Axis.ZP.rotationDegrees(handSideSign * sinSwingProg2 * -20.0f));
        stack.translate(-handSideSign, 3.6f, 3.5f);
        stack.mulPose(Axis.ZP.rotationDegrees(handSideSign * 120.0f));
        stack.mulPose(Axis.XP.rotationDegrees(200.0f));
        stack.mulPose(Axis.YP.rotationDegrees(handSideSign * -135.0f));
        stack.translate(handSideSign * 5.6f, 0.0f, 0.0f);

        // renderRightHand/renderLeftHand bind the player's skin internally in 1.21.1, so unlike the
        // 1.16.5 code we don't bind the skin texture manually here.
        PlayerRenderer playerRenderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(mc.player);
        if (handSideSign >= 0.0f) {
            playerRenderer.renderRightHand(stack, buffer, combinedLight, mc.player);
        } else {
            playerRenderer.renderLeftHand(stack, buffer, combinedLight, mc.player);
        }
    }

    /** Draws the live browser texture on the model screen, with CEF's top-left origin Y-flipped. */
    private void drawScreenQuad(Matrix4f pose, int textureId) {
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, textureId);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder vb = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        vb.addVertex(pose, 0.0f, 0.0f, 0.0f).setUv(0.0f, 1.0f).setColor(255, 255, 255, 255);
        vb.addVertex(pose, SCREEN_W, 0.0f, 0.0f).setUv(1.0f, 1.0f).setColor(255, 255, 255, 255);
        vb.addVertex(pose, SCREEN_W, SCREEN_H, 0.0f).setUv(1.0f, 0.0f).setColor(255, 255, 255, 255);
        vb.addVertex(pose, 0.0f, SCREEN_H, 0.0f).setUv(0.0f, 0.0f).setColor(255, 255, 255, 255);
        BufferUploader.drawWithShader(vb.build());

        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableDepthTest();
    }
}
