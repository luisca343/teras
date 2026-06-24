package es.boffmedia.teras.client.funko;

import es.boffmedia.teras.tileentity.FunkoTE;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.Random;

/**
 * Spawns {@link FunkoSkinParticle}s sampled from the broken funko's skin, so break/hit debris
 * matches the figure instead of the block's static particle texture. Called (client-side only)
 * from {@code Funko.addDestroyEffects}/{@code addHitEffects}.
 */
@OnlyIn(Dist.CLIENT)
public final class FunkoBreakParticles {

    // Normalised 64x64 skin regions to sample debris from: head, body, arms and legs (front faces).
    private static final float[][] REGIONS = {
            { 8f, 8f, 16f, 16f },   // head
            { 20f, 20f, 28f, 32f }, // body
            { 44f, 20f, 48f, 32f }, // right arm
            { 36f, 52f, 40f, 64f }, // left arm
            { 4f, 20f, 8f, 32f },   // right leg
            { 20f, 52f, 24f, 64f }, // left leg
    };
    private static final float TILE = 64f;
    private static final float SAMPLE = 2f; // px square sampled per particle

    private static final Random RANDOM = new Random();

    // The block/TE is often already gone when destroy particles fire, so the renderer records each
    // visible funko's resolved skin here and we fall back to it.
    private static final Map<Long, ResourceLocation> REMEMBERED = new java.util.concurrent.ConcurrentHashMap<>();

    private FunkoBreakParticles() {
    }

    /** Called by the renderer each frame so the skin is known even after the block is broken. */
    public static void remember(BlockPos pos, ResourceLocation skin) {
        if (skin != null) {
            if (REMEMBERED.size() > 256) {
                REMEMBERED.clear();
            }
            REMEMBERED.put(pos.asLong(), skin);
        }
    }

    public static boolean addDestroyEffects(net.minecraft.world.World world, BlockPos pos, ParticleManager manager) {
        ResourceLocation skin = resolveSkin(world, pos);
        if (skin == null) {
            return false;
        }
        ClientWorld clientWorld = (ClientWorld) world;
        for (int i = 0; i < 28; i++) {
            double x = pos.getX() + 0.3D + RANDOM.nextDouble() * 0.4D;
            double y = pos.getY() + RANDOM.nextDouble() * 0.55D;
            double z = pos.getZ() + 0.3D + RANDOM.nextDouble() * 0.4D;
            spawn(manager, clientWorld, skin, x, y, z,
                    (x - pos.getX() - 0.5D), (y - pos.getY()) * 0.5D, (z - pos.getZ() - 0.5D));
        }
        return true;
    }

    public static boolean addHitEffects(net.minecraft.world.World world, BlockPos pos, Vector3d hit, ParticleManager manager) {
        ResourceLocation skin = resolveSkin(world, pos);
        if (skin == null) {
            return false;
        }
        ClientWorld clientWorld = (ClientWorld) world;
        for (int i = 0; i < 2; i++) {
            spawn(manager, clientWorld, skin, hit.x, hit.y, hit.z, 0.0D, 0.0D, 0.0D);
        }
        return true;
    }

    private static ResourceLocation resolveSkin(net.minecraft.world.World world, BlockPos pos) {
        if (!(world instanceof ClientWorld)) {
            return null;
        }
        TileEntity te = world.getBlockEntity(pos);
        if (te instanceof FunkoTE) {
            FunkoTE funko = (FunkoTE) te;
            ResourceLocation skin = FunkoSkin.getSkinTexture(funko.getOwnerProfile(), funko.getSkinFile());
            remember(pos, skin);
            return skin;
        }
        // Block already removed: use the skin the renderer recorded while it was visible.
        return REMEMBERED.remove(pos.asLong());
    }

    private static void spawn(ParticleManager manager, ClientWorld world, ResourceLocation skin,
                             double x, double y, double z, double xd, double yd, double zd) {
        float[] region = REGIONS[RANDOM.nextInt(REGIONS.length)];
        float u = region[0] + RANDOM.nextFloat() * (region[2] - region[0] - SAMPLE);
        float v = region[1] + RANDOM.nextFloat() * (region[3] - region[1] - SAMPLE);
        manager.add(new FunkoSkinParticle(world, x, y, z, xd, yd, zd, skin,
                u / TILE, v / TILE, (u + SAMPLE) / TILE, (v + SAMPLE) / TILE));
    }
}
