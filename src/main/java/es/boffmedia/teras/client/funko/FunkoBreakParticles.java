package es.boffmedia.teras.client.funko;

import es.boffmedia.teras.blockentity.FunkoBlockEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawns {@link FunkoSkinParticle}s sampled from the broken funko's skin, so break/hit debris
 * matches the figure instead of the block's static particle texture. Driven by
 * {@code FunkoClientExtensions}.
 */
@OnlyIn(Dist.CLIENT)
public final class FunkoBreakParticles {

    // Normalised 64x64 skin regions to sample debris from: head, body, arms and legs (front faces).
    private static final float[][] REGIONS = {
            {8f, 8f, 16f, 16f},   // head
            {20f, 20f, 28f, 32f}, // body
            {44f, 20f, 48f, 32f}, // right arm
            {36f, 52f, 40f, 64f}, // left arm
            {4f, 20f, 8f, 32f},   // right leg
            {20f, 52f, 24f, 64f}, // left leg
    };
    private static final float TILE = 64f;
    private static final float SAMPLE = 2f; // px square sampled per particle

    // The block/BE is often already gone when destroy particles fire, so the renderer records each
    // visible funko's resolved skin here and we fall back to it.
    private static final Map<Long, ResourceLocation> REMEMBERED = new ConcurrentHashMap<>();

    private FunkoBreakParticles() {
    }

    /** Called by the renderer each frame so the skin is known even after the block is broken. */
    public static void remember(BlockPos pos, @Nullable ResourceLocation skin) {
        if (skin != null) {
            if (REMEMBERED.size() > 256) {
                REMEMBERED.clear();
            }
            REMEMBERED.put(pos.asLong(), skin);
        }
    }

    public static boolean addDestroyEffects(Level level, BlockPos pos, ParticleEngine manager) {
        ResourceLocation skin = resolveSkin(level, pos);
        if (skin == null) {
            return false;
        }
        ClientLevel clientLevel = (ClientLevel) level;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 28; i++) {
            double x = pos.getX() + 0.3D + random.nextDouble() * 0.4D;
            double y = pos.getY() + random.nextDouble() * 0.55D;
            double z = pos.getZ() + 0.3D + random.nextDouble() * 0.4D;
            spawn(manager, clientLevel, skin, x, y, z,
                    (x - pos.getX() - 0.5D), (y - pos.getY()) * 0.5D, (z - pos.getZ() - 0.5D));
        }
        return true;
    }

    public static boolean addHitEffects(Level level, BlockPos pos, Vec3 hit, ParticleEngine manager) {
        ResourceLocation skin = resolveSkin(level, pos);
        if (skin == null) {
            return false;
        }
        ClientLevel clientLevel = (ClientLevel) level;
        for (int i = 0; i < 2; i++) {
            spawn(manager, clientLevel, skin, hit.x, hit.y, hit.z, 0.0D, 0.0D, 0.0D);
        }
        return true;
    }

    @Nullable
    private static ResourceLocation resolveSkin(Level level, BlockPos pos) {
        if (!(level instanceof ClientLevel)) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof FunkoBlockEntity funko) {
            ResourceLocation skin = FunkoSkin.getSkinTexture(funko.getOwnerProfile(), funko.getSkinFile());
            remember(pos, skin);
            return skin;
        }
        // Block already removed: use the skin the renderer recorded while it was visible.
        return REMEMBERED.remove(pos.asLong());
    }

    private static void spawn(ParticleEngine manager, ClientLevel level, ResourceLocation skin,
                              double x, double y, double z, double xd, double yd, double zd) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        float[] region = REGIONS[random.nextInt(REGIONS.length)];
        float u = region[0] + random.nextFloat() * (region[2] - region[0] - SAMPLE);
        float v = region[1] + random.nextFloat() * (region[3] - region[1] - SAMPLE);
        manager.add(new FunkoSkinParticle(level, x, y, z, xd, yd, zd, skin,
                u / TILE, v / TILE, (u + SAMPLE) / TILE, (v + SAMPLE) / TILE));
    }
}
