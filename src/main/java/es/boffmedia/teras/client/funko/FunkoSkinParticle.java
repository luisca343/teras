package es.boffmedia.teras.client.funko;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.particle.IParticleRenderType;
import net.minecraft.client.particle.TexturedParticle;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A break/hit particle textured from a funko's skin. It samples a small square of the skin
 * texture so the debris visually matches the figure (head, body, limbs...).
 *
 * <p>Each distinct skin gets one {@link IParticleRenderType} instance (cached, so the particle
 * manager batches them together); the render type binds that skin and we reuse vanilla's
 * {@link TexturedParticle} billboard rendering.</p>
 */
@OnlyIn(Dist.CLIENT)
public class FunkoSkinParticle extends TexturedParticle {

    private static final Map<ResourceLocation, IParticleRenderType> RENDER_TYPES = new ConcurrentHashMap<>();

    private final IParticleRenderType renderType;
    private final float u0;
    private final float v0;
    private final float u1;
    private final float v1;

    public FunkoSkinParticle(ClientWorld world, double x, double y, double z, double xd, double yd, double zd,
                             ResourceLocation skin, float u0, float v0, float u1, float v1) {
        super(world, x, y, z, xd, yd, zd);
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

    private static IParticleRenderType renderType(ResourceLocation skin) {
        return RENDER_TYPES.computeIfAbsent(skin, rl -> new IParticleRenderType() {
            @Override
            public void begin(BufferBuilder builder, TextureManager textureManager) {
                RenderSystem.depthMask(true);
                textureManager.bind(rl);
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                RenderSystem.alphaFunc(516, 0.003921569F);
                builder.begin(7, DefaultVertexFormats.PARTICLE);
            }

            @Override
            public void end(Tessellator tessellator) {
                tessellator.end();
            }

            @Override
            public String toString() {
                return "FUNKO_SKIN[" + rl + "]";
            }
        });
    }

    @Override
    public IParticleRenderType getRenderType() {
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
