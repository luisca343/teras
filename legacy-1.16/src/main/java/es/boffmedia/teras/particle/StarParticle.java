package es.boffmedia.teras.particle;

import com.mojang.blaze3d.vertex.IVertexBuilder;
import es.boffmedia.teras.Teras;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.IAnimatedSprite;
import net.minecraft.client.particle.IParticleFactory;
import net.minecraft.client.particle.IParticleRenderType;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.particles.BasicParticleType;

import java.util.Random;

@OnlyIn(Dist.CLIENT)
public class StarParticle extends FakeParticle {

    private static IAnimatedSprite texture = loadTexture(new ResourceLocation(Teras.MOD_ID, "star"));
    private float angleDirection;
    private int startAge;
    private float prevScale;
    private float prevAlpha;
    private float localScale;
    private float drag = 0.9F;

    Random rand = new Random();

    private static final float[] sizeScale = {0.1F, 0.20F, 0.35F, 0.5F, 0.65F, 0.8F, 0.85F, 1F, 1F, 1F, 1F, 0.85F, 0.45F, 0.2F, 0.1F};

    private static final int[] fadeColors = {0x00D5ED, 0xD400E8, 0xE52A00, 0x95E500, 0xE27C00, 0x5200E0};
    private static final int color = 0xEFEF00;

    public StarParticle(ClientWorld world, double x, double y, double z, double motionX, double motionY, double motionZ) {
        super(world, x, y, z, motionX, motionY, motionZ);

        Teras.LOGGER.warn("StarParticle constructor");

        this.xd = motionX + (Math.random() * 2.0D - 1.0D) * (double)0.1F;
        this.yd = motionY + (Math.random() * 2.0D - 1.0D) * (double)0.1F;
        this.yd = motionZ + (Math.random() * 2.0D - 1.0D) * (double)0.1F;

        Teras.LOGGER.warn("StarParticle constructor2");


        this.startAge = this.rand.nextInt(3);
        this.age = this.startAge + 15;
        this.localScale = 0.5F + (this.rand.nextFloat() * 1.5F);
        this.angleDirection = this.rand.nextFloat();
        //this.prevParticleAngle = this.particleAngle;
        this.rCol = 1F - (this.rand.nextFloat() * 0.015F);
        this.gCol = 1F - (this.rand.nextFloat() * 0.015F);
        this.bCol = 1F - (this.rand.nextFloat() * 0.015F);
        this.gravity = 0.4F;
        this.angleDirection = this.rand.nextFloat() > 0.5F ? 1F : -1F;
        this.scale(0F);
        this.setSize(0F, 0F);

        Teras.LOGGER.warn("StarParticle constructor3");
        Teras.LOGGER.warn(texture);


        this.pickSprite(texture);
    }

    @Override
    public void tick() {
        int realAge = this.age - this.startAge;
        if (realAge < 0) {
            this.age++;
            return;
        } else {
            standardTick();
        }

        //this.prevParticleAngle = this.particleAngle;
        //this.particleAngle += (this.angleDirection * 0.5F);
        //this.particleScale = 0.1F * sizeScale[this.age % 20];// * this.getViewScale();
        this.prevScale = this.localScale;
        float f = 0.10F * sizeScale[realAge % 15] * this.getViewScale() * localScale;
        this.setSize(f, f);
        this.scale(f) ;

        //Make the particle fade out as it is nearing the end of its life
        this.prevAlpha = this.alpha;
        this.alpha = realAge >= 10 ? 1F - ((realAge - 10) / 5F) : 1F;
    }

    private void standardTick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.age) {
            this.remove();
        } else {
            this.yd -= 0.04D * (double)this.gravity;
            this.move(this.xd, this.yd, this.zd);
            this.xd *= (double)drag;
            this.yd *= (double)drag;
            this.zd *= (double)drag;
            if (this.onGround) {
                this.xd *= (double)0.7F;
                this.zd *= (double)0.7F;
            }

        }
    }

    @Override
    public void render(IVertexBuilder buffer, ActiveRenderInfo renderInfo, float partialTicks) {
        float f = this.alpha;
        float f2 = MathHelper.lerp(partialTicks, this.prevAlpha, this.alpha);
        this.alpha = f2;
        super.render(buffer, renderInfo, partialTicks);
        this.alpha = f;
    }



    public float getScale(float partialTicks) {
        return MathHelper.lerp(partialTicks, this.prevScale, this.localScale);
    }

    @Override
    protected void setSize(float particleWidth, float particleHeight) {
        double xOff = this.bbWidth - particleWidth;
        double yOff = this.bbHeight - particleHeight;

        super.setSize(particleWidth, particleHeight);

        this.move(xOff, yOff, xOff);
    }

    //Get the scale to render the particle at based on how far away the player is
    private float getViewScale() {
        return 0.1F + (float) (Math.sqrt(Minecraft.getInstance().player.position().distanceToSqr(this.x, this.y, this.z))) / 5.0F;
    }

    @Override
    public IParticleRenderType getRenderType() {
        return IParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }



    @Override
    protected int getLightColor(float partialTick) {
        return 15728880;
    }

    @Override
    public ResourceLocation getResourceLocation() {
        return new ResourceLocation(Teras.MOD_ID, "star");
    }

    @OnlyIn(Dist.CLIENT)
    public static class Factory implements IParticleFactory<BasicParticleType> {
        private final IAnimatedSprite spriteSet;

        public Factory(IAnimatedSprite spriteSet) {
            this.spriteSet = spriteSet;
        }

        public Particle createParticle(BasicParticleType typeIn, ClientWorld worldIn, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new StarParticle(worldIn, x, y, z, xSpeed, ySpeed, zSpeed);
        }
    }
}