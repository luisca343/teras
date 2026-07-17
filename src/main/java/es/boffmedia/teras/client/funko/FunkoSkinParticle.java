package es.boffmedia.teras.client.funko;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A break/hit particle textured from a funko's skin. It samples a small square of the skin
 * texture so the debris visually matches the figure (head, body, limbs...).
 *
 * <p>Each distinct skin gets one {@link ParticleRenderType} instance (cached, so the particle
 * manager batches them together); the render type binds that skin and we reuse vanilla's
 * {@link SingleQuadParticle} billboard rendering.</p>
 */
@OnlyIn(Dist.CLIENT)
public class FunkoSkinParticle extends SingleQuadParticle {

    private static final Map<ResourceLocation, ParticleRenderType> RENDER_TYPES = new ConcurrentHashMap<>();

    private final ParticleRenderType renderType;
    private final float u0;
    private final float v0;
    private final float u1;
    private final float v1;

    public FunkoSkinParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                             ResourceLocation skin, float u0, float v0, float u1, float v1) {
        super(level, x, y, z, xd, yd, zd);
        this.renderType = renderType(skin);
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
        this.gravity = 1.0F;
        this.quadSize *= 0.7F;
        this.lifetime = (int) (this.lifetime * 0.8F) + 4;
        // Slow the inherited random spread a little so debris stays near the block.
        this.xd = this.xd * 0.4D + xd;
        this.yd = this.yd * 0.4D + yd;
        this.zd = this.zd * 0.4D + zd;
    }

    /**
     * Mirrors vanilla {@code PARTICLE_SHEET_TRANSLUCENT}, but bound to a skin instead of the particle
     * atlas. 1.16.5 also set {@code RenderSystem.alphaFunc(516, 0.0039)}; the fixed-function alpha test
     * is gone, and the particle shader already discards near-zero alpha, so it has no replacement here.
     */
    private static ParticleRenderType renderType(ResourceLocation skin) {
        return RENDER_TYPES.computeIfAbsent(skin, rl -> new ParticleRenderType() {
            @Override
            public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
                RenderSystem.depthMask(true);
                RenderSystem.setShader(GameRenderer::getParticleShader);
                RenderSystem.setShaderTexture(0, rl);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            }

            @Override
            public String toString() {
                return "FUNKO_SKIN[" + rl + "]";
            }
        });
    }

    @Override
    public ParticleRenderType getRenderType() {
        return this.renderType;
    }

    @Override
    protected float getU0() {
        return this.u0;
    }

    @Override
    protected float getU1() {
        return this.u1;
    }

    @Override
    protected float getV0() {
        return this.v0;
    }

    @Override
    protected float getV1() {
        return this.v1;
    }
}
