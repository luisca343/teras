package es.boffmedia.teras.client.shiny;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import es.boffmedia.teras.Teras;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One star of the shiny burst: it pops out from the Pokémon, swells, drifts up, then fades.
 *
 * <p><b>No particle registry.</b> Like {@link es.boffmedia.teras.client.funko.FunkoSkinParticle}, it
 * binds its own texture in its render type instead of living on the particle atlas, so there is no
 * {@code ParticleType} to register, no {@code particles/*.json}, and no server-side particle id that
 * would have to exist on a server that never draws one. The 1.16.5 version went the atlas route and
 * needed an animated sprite set, a factory and a registration — none of which it ever finished
 * wiring up, which is why its particle loop sat commented out.</p>
 *
 * <p>Full-bright on purpose: a shiny spotted at dusk is exactly when the cue matters most, and a
 * star lit by the world would be invisible there.</p>
 */
@OnlyIn(Dist.CLIENT)
public class StarParticle extends SingleQuadParticle {

    /** The three star sprites 1.16.5 shipped; one is picked per particle so a burst is not uniform. */
    private static final int SPRITE_COUNT = 3;

    private static final Map<ResourceLocation, ParticleRenderType> RENDER_TYPES = new ConcurrentHashMap<>();

    /**
     * Size over life, sampled by age. Ported from the 1.16.5 curve: a fast pop, a long hold, a quick
     * collapse — the shape that reads as a twinkle rather than a puff of smoke.
     */
    private static final float[] SIZE_CURVE = {
            0.1F, 0.20F, 0.35F, 0.5F, 0.65F, 0.8F, 0.85F, 1F, 1F, 1F, 1F, 0.85F, 0.45F, 0.2F, 0.1F};

    private final ParticleRenderType renderType;
    private final float baseSize;

    StarParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
        super(level, x, y, z, 0, 0, 0);
        this.renderType = renderType(sprite(level.random.nextInt(SPRITE_COUNT)));
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = SIZE_CURVE.length;
        this.baseSize = 0.14F + level.random.nextFloat() * 0.10F;
        this.gravity = -0.02F; // Negative: stars rise. In Arceus they float off the Pokemon, not onto it.
        this.friction = 0.92F;
        // A faint warm tint, varied per star, so a burst is not five identical white quads.
        float warm = 1F - level.random.nextFloat() * 0.06F;
        this.rCol = 1F;
        this.gCol = warm;
        this.bCol = 0.55F + level.random.nextFloat() * 0.25F;
        this.quadSize = 0.01F;
    }

    private static ResourceLocation sprite(int index) {
        return ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "textures/particle/stars_" + index + ".png");
    }

    /** Vanilla {@code PARTICLE_SHEET_TRANSLUCENT}, bound to our star texture instead of the atlas. */
    private static ParticleRenderType renderType(ResourceLocation texture) {
        return RENDER_TYPES.computeIfAbsent(texture, rl -> new ParticleRenderType() {
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
                return "TERAS_STAR[" + rl + "]";
            }
        });
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) {
            return;
        }
        int index = Math.min(this.age, SIZE_CURVE.length - 1);
        this.quadSize = this.baseSize * SIZE_CURVE[index];
        // Fade over the last third rather than vanishing on the frame the lifetime runs out.
        int fadeFrom = (this.lifetime * 2) / 3;
        this.alpha = this.age < fadeFrom
                ? 1F
                : Mth.clamp(1F - (this.age - fadeFrom) / (float) (this.lifetime - fadeFrom), 0F, 1F);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return this.renderType;
    }

    // The bound texture is the whole sprite, so the quad takes all of it. There is no atlas to
    // look a region up in — that is the point of binding our own.

    @Override
    protected float getU0() {
        return 0F;
    }

    @Override
    protected float getU1() {
        return 1F;
    }

    @Override
    protected float getV0() {
        return 0F;
    }

    @Override
    protected float getV1() {
        return 1F;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0; // Full-bright.
    }
}
