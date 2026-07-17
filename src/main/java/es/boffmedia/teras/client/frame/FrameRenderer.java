package es.boffmedia.teras.client.frame;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import es.boffmedia.teras.blockentity.FrameBlockEntity;
import es.boffmedia.teras.blocks.PictureFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * Draws each {@link FrameBlockEntity}: a bezel + screen backing (so an unconfigured frame is still
 * visible and findable), with the live media on top when it has loaded. The media texture is the raw
 * GL handle from the {@link org.watermedia.api.media.players.MediaPlayer} via {@link FrameMediaManager}.
 *
 * <p>Immediate-mode draw (bake the pose matrix into the vertices, then {@code drawWithShader}) is the
 * right tool here because the texture is a raw GL id, not a registered {@code ResourceLocation} a
 * {@code RenderType} could bind. It composes correctly with the world camera: in {@code renderLevel}
 * the camera rotation lives in {@code RenderSystem.getModelViewMatrix()} while the block-entity pose
 * stack carries only the camera-relative translation, and {@code drawWithShader} multiplies both.</p>
 */
@OnlyIn(Dist.CLIENT)
public class FrameRenderer implements BlockEntityRenderer<FrameBlockEntity> {

    /** Distance from the mounting wall to the bezel plane — clears the collision slab, avoids z-fighting. */
    private static final float WALL_OFFSET = 0.03F;
    /** Small forward steps that layer screen in front of bezel and media in front of screen. */
    private static final float Z_SCREEN = 0.01F;
    private static final float Z_MEDIA = 0.02F;
    /** Bezel thickness around the media, in blocks (~1px). */
    private static final float BEZEL = 0.0625F;

    private static final float[] BEZEL_COLOR = {0.10F, 0.07F, 0.05F};
    private static final float[] SCREEN_COLOR = {0.02F, 0.02F, 0.03F};

    /** Extra distance past a frame's render range before its player is freed (hysteresis). */
    private static final int RELEASE_MARGIN = 16;

    public FrameRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public int getViewDistance() {
        // The per-frame renderDistance does the real culling; this only stops Minecraft from dropping
        // frames before that check runs.
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(FrameBlockEntity frame) {
        // A display sized past one block would be frustum-culled the moment the single anchor block
        // left view; inflate the render box by the configured reach so it stays drawn.
        double reach = Math.max(1.0, Math.max(frame.getSizeX(), frame.getSizeY()));
        return new AABB(frame.getBlockPos()).inflate(reach);
    }

    @Override
    public void render(FrameBlockEntity frame, float partialTicks, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        BlockPos pos = frame.getBlockPos();
        // A stale render pass can call us after the block was broken; never (re)create a player for a
        // removed frame, or it plays on orphaned with nothing left to release it.
        if (frame.isRemoved()) {
            FrameMediaManager.release(pos);
            return;
        }

        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dx = cam.x - (pos.getX() + 0.5);
        double dy = cam.y - (pos.getY() + 0.5);
        double dz = cam.z - (pos.getZ() + 0.5);
        double distanceSq = dx * dx + dy * dy + dz * dz;
        int rd = frame.getRenderDistance();
        if (distanceSq > (double) rd * rd) {
            // Well past its range: free the player so distant frames don't keep FFMPEG threads and an
            // OpenAL source alive. The margin keeps a frame idling right at the edge from thrashing.
            double release = rd + RELEASE_MARGIN;
            if (distanceSq > release * release) {
                FrameMediaManager.release(pos);
            }
            return;
        }

        Direction facing = frame.getBlockState().getValue(PictureFrame.FACING);

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        // Rotate so local +Z becomes the outward face normal; local +X/+Y stay the screen plane.
        switch (facing) {
            case NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            case EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            case WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
            case UP -> poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            case DOWN -> poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            default -> { /* SOUTH: +Z already */ }
        }
        // -0.5 puts the plane on the wall side of the cell (where the block sits), not the front face a
        // block away; +WALL_OFFSET lifts it just clear of the wall toward the viewer.
        poseStack.translate(0.0, 0.0, -0.5 + WALL_OFFSET);
        float rotation = frame.getRotation();
        if (rotation != 0.0F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(rotation));
        }

        Matrix4f matrix = poseStack.last().pose();

        // Outer (display) rectangle, centred on the block; inner rectangle is the media, inset by the bezel.
        float x0 = frame.getMinX() - 0.5F;
        float x1 = frame.getMaxX() - 0.5F;
        float y0 = frame.getMinY() - 0.5F;
        float y1 = frame.getMaxY() - 0.5F;
        float bw = Math.min(BEZEL, Math.min((x1 - x0) / 3.0F, (y1 - y0) / 3.0F));
        float ix0 = x0 + bw;
        float ix1 = x1 - bw;
        float iy0 = y0 + bw;
        float iy1 = y1 - bw;

        // When not "lit", the display is dimmed by the block's world light instead of glowing fullbright.
        float light = frame.isLit() ? 1.0F : worldLight(packedLight);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // Bezel + screen: drawn unless the frame is borderless, so an empty/loading frame stays findable.
        if (frame.isShowFrame()) {
            solidQuad(matrix, x0, y0, x1, y1, 0.0F, BEZEL_COLOR, light);
            solidQuad(matrix, ix0, iy0, ix1, iy1, Z_SCREEN, SCREEN_COLOR, light);
        }

        long texture = mediaTexture(frame, pos, Math.sqrt(distanceSq));
        if (texture != 0L) {
            if (!frame.isBothSides()) {
                RenderSystem.enableCull();
            }
            mediaQuad(matrix, frame, ix0, iy0, ix1, iy1, texture, light);
        }

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    /** The frame's media texture id (0 when empty/loading), managing the player lifecycle by url. */
    private static long mediaTexture(FrameBlockEntity frame, BlockPos pos, double distance) {
        String url = frame.getUrl();
        if (url == null || url.isEmpty()) {
            FrameMediaManager.release(pos);
            return 0L;
        }
        FrameMedia media = FrameMediaManager.getOrCreate(pos, url);
        if (media == null) {
            return 0L;
        }
        int volume = frame.isMuted() ? 0 : attenuatedVolume(frame, distance);
        return media.texture(frame.isPlaying(), frame.isLoop(), volume);
    }

    /** Block/sky light at the frame as a 0.1-1 dim factor (never fully black), for the un-lit mode. */
    private static float worldLight(int packedLight) {
        int level = Math.max(LightTexture.block(packedLight), LightTexture.sky(packedLight));
        return Math.max(0.1F, level / 15.0F);
    }

    private static void solidQuad(Matrix4f matrix, float x0, float y0, float x1, float y1, float z,
                                  float[] rgb, float light) {
        float r = rgb[0] * light;
        float g = rgb[1] * light;
        float b = rgb[2] * light;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        builder.addVertex(matrix, x0, y0, z).setColor(r, g, b, 1.0F);
        builder.addVertex(matrix, x1, y0, z).setColor(r, g, b, 1.0F);
        builder.addVertex(matrix, x1, y1, z).setColor(r, g, b, 1.0F);
        builder.addVertex(matrix, x0, y1, z).setColor(r, g, b, 1.0F);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private static void mediaQuad(Matrix4f matrix, FrameBlockEntity frame,
                                  float x0, float y0, float x1, float y1, long texture, float light) {
        float uLeft = frame.isFlipX() ? 1.0F : 0.0F;
        float uRight = frame.isFlipX() ? 0.0F : 1.0F;
        // GL texture rows run top-first, so the top edge samples v=0 unless flipped.
        float vTop = frame.isFlipY() ? 1.0F : 0.0F;
        float vBottom = frame.isFlipY() ? 0.0F : 1.0F;
        float b = frame.getBrightness() * light;
        float a = frame.getAlpha();

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, (int) texture);
        BufferBuilder builder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.addVertex(matrix, x0, y0, Z_MEDIA).setUv(uLeft, vBottom).setColor(b, b, b, a);
        builder.addVertex(matrix, x1, y0, Z_MEDIA).setUv(uRight, vBottom).setColor(b, b, b, a);
        builder.addVertex(matrix, x1, y1, Z_MEDIA).setUv(uRight, vTop).setColor(b, b, b, a);
        builder.addVertex(matrix, x0, y1, Z_MEDIA).setUv(uLeft, vTop).setColor(b, b, b, a);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    /**
     * Distance falloff for a frame's audio, as a 0-100 gain: full within {@code minAudioDistance},
     * silent past {@code maxAudioDistance}, linear between, then scaled by the master volume so it
     * respects the player's sound settings. Mirrors the 1.16.5 frame's attenuation.
     */
    private static int attenuatedVolume(FrameBlockEntity frame, double distance) {
        float base = frame.getVolume();
        float minD = frame.getMinAudioDistance();
        float maxD = frame.getMaxAudioDistance();
        float v;
        if (distance <= minD) {
            v = base;
        } else if (distance >= maxD || maxD <= minD) {
            v = distance >= maxD ? 0.0F : base;
        } else {
            v = base * (1.0F - (float) ((distance - minD) / (maxD - minD)));
        }
        float master = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER);
        return Math.round(v * master * 100.0F);
    }
}
