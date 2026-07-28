package es.boffmedia.teras.client.shiny;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.init.SoundInit;
import es.boffmedia.teras.net.ShinySparklePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the shiny cue: a ring of {@link StarParticle}s around the Pokémon and a chime at its
 * position. Client half of {@link es.boffmedia.teras.shiny.ShinySpotter}, which has already decided
 * this player may see it.
 *
 * <p><b>The burst is two waves.</b> One ring of stars appearing at once reads as an explosion; the
 * second wave three ticks later, offset in angle, reads as a twinkle that keeps going — which is
 * what Legends Arceus draws. The wait is also what the 1.16.5 version used its scheduler for, and it
 * is the reason this class holds a queue rather than doing everything in the packet handler.</p>
 *
 * <p>The cue is re-checked here, not just on the server: between the scan and the packet landing the
 * entity may have despawned or left the client's world. A sparkle at a position where nothing is
 * standing is worse than a missed one.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, value = Dist.CLIENT)
public final class ShinySparkles {
    private ShinySparkles() {}

    /** Ticks between the two waves of a burst. */
    private static final int SECOND_WAVE_DELAY = 3;

    /** Bursts waiting on their second wave. Tiny — a shiny sighting is a rare event. */
    private static final List<Pending> PENDING = new ArrayList<>();

    private record Pending(int entityId, int particles, int fireAtTick) {}

    private static int clientTick;

    /** Handles {@link ShinySparklePayload}; already on the client main thread. */
    public static void accept(ShinySparklePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(payload.entityId());
        if (entity == null || !entity.isAlive()) {
            return;
        }

        int particles = scaled(payload.particles());
        if (particles > 0) {
            burst(level, entity, particles, 0.0D);
            PENDING.add(new Pending(payload.entityId(), particles, clientTick + SECOND_WAVE_DELAY));
        }

        if (payload.volume() > 0F) {
            // Positional and attenuating, so distance is audible. PLAYERS rather than a Pokemon
            // category: this is the game talking to the player about what they found.
            minecraft.getSoundManager().play(new SimpleSoundInstance(
                    SoundInit.SPARKLE.get(), SoundSource.PLAYERS,
                    payload.volume(), 1.0F, RandomSource.create(),
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5D, entity.getZ()));
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        clientTick++;
        if (PENDING.isEmpty()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            PENDING.clear();
            return;
        }
        PENDING.removeIf(pending -> {
            if (clientTick < pending.fireAtTick()) {
                return false;
            }
            Entity entity = level.getEntity(pending.entityId());
            if (entity != null && entity.isAlive()) {
                // Half the stars, rotated off the first ring, so the two waves interleave.
                burst(level, entity, Math.max(1, pending.particles() / 2), 36.0D);
            }
            return true;
        });
    }

    /** Entity ids mean nothing in the next world, so a queued wave must not survive the disconnect. */
    @SubscribeEvent
    public static void onLoggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        PENDING.clear();
    }

    /**
     * A ring of stars at the Pokémon's mid-height, each thrown outward and up.
     *
     * @param angleOffset degrees to rotate the whole ring by, so a later wave does not overlap
     */
    private static void burst(ClientLevel level, Entity entity, int count, double angleOffset) {
        double radius = entity.getBbWidth() / 2.5D + 0.2D;
        double height = entity.getBbHeight() / 2.0D;
        double step = 360.0D / count;

        for (int i = 0; i < count; i++) {
            // Jitter inside the slice rather than across the ring: the stars stay evenly spread but
            // never land on an obvious lattice.
            double degrees = angleOffset + i * step + (level.random.nextDouble() - 0.5D) * step * 0.5D;
            double cos = Math.cos(Math.toRadians(degrees));
            double sin = Math.sin(Math.toRadians(degrees));

            double x = entity.getX() + cos * radius + jitter(level, radius);
            double y = entity.getY() + height + jitter(level, 0.25D);
            double z = entity.getZ() + sin * radius + jitter(level, radius);

            Particle particle = new StarParticle(level, x, y, z,
                    cos * 0.035D, 0.045D + level.random.nextDouble() * 0.02D, sin * 0.035D);
            Minecraft.getInstance().particleEngine.add(particle);
        }
    }

    private static double jitter(ClientLevel level, double scale) {
        return (level.random.nextDouble() * 0.2D - 0.1D) * scale;
    }

    /**
     * The requested count, respecting the player's particle setting. Particles added straight to the
     * engine bypass the check {@code Level.addParticle} would have done, so a player on MINIMAL would
     * otherwise get the full burst they asked the game not to draw.
     */
    private static int scaled(int requested) {
        return switch (Minecraft.getInstance().options.particles().get()) {
            case ALL -> requested;
            case DECREASED -> Math.max(1, requested / 2);
            case MINIMAL -> Math.max(1, requested / 4);
        };
    }
}
