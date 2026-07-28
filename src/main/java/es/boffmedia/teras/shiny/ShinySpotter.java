package es.boffmedia.teras.shiny;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.net.ShinySparklePayload;
import es.boffmedia.teras.shiny.api.ShinyCandidate;
import es.boffmedia.teras.shiny.api.ShinyProvider;
import es.boffmedia.teras.shiny.api.ShinyProviders;
import es.boffmedia.teras.util.TerasConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Spots wild shinies for each player and fires the Legends Arceus cue at them —
 * {@link es.boffmedia.teras.client.shiny.ShinySparkles} draws it. The server half of the feature and
 * the only place that decides who has seen what.
 *
 * <p><b>Why the server decides.</b> A client can already see every shiny inside its render distance;
 * the 1.16.5 tracker ran entirely on the client, which meant the cue was a courtesy a modified client
 * could give itself through a mountain. Range, line of sight and the repeat interval are enforced
 * here, and the client is handed one entity id it was already rendering.</p>
 *
 * <p><b>Cost.</b> The scan runs on a {@value #INTERVAL_TICKS}-tick timer, walks only the entities in
 * a box around each player, and orders its tests cheapest-first: the box bounds the search, the
 * engine read rejects non-Pokémon, the palette rejects everything that is not shiny, and only what
 * survives all of that pays for a distance check and a raycast. Shinies are rare, so in the ordinary
 * case nothing reaches the raycast at all — which is what makes the line-of-sight test 1.16.5 wrote
 * and then disabled affordable to actually turn on.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class ShinySpotter {
    private ShinySpotter() {}

    /** Ticks between scans. Half a second: fast enough that the cue lands as you turn to look. */
    private static final int INTERVAL_TICKS = 10;

    private static final ShinyLedger LEDGER = new ShinyLedger();

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long tick = server.getTickCount();

        if (tick % INTERVAL_TICKS != 0) {
            return;
        }

        TerasConfig.ShinySettings config = TerasConfig.shiny();
        if (!config.enabled() || config.range() <= 0) {
            return;
        }
        ShinyProvider provider = ShinyProviders.get();
        if (provider == null) {
            // No engine, so nothing on this server is a Pokémon. Nothing to warn about: a Teras
            // server run for regions or the bank is a legitimate deployment.
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            scan(player, provider, config, tick);
        }
    }

    private static void scan(ServerPlayer player, ShinyProvider provider,
                             TerasConfig.ShinySettings config, long tick) {
        if (player.isSpectator() || provider.isBattling(player)) {
            return;
        }
        // Inside a dungeon the run owns the whole sensory channel — its own sounds, its own lighting,
        // its own reasons to look at something. A Pokémon in there is not a spawn the player can go
        // and catch, so a sighting cue would be a lie as well as a distraction.
        if (es.boffmedia.teras.dungeon.run.DungeonHealth.isInRun(player)) {
            return;
        }

        double range = config.range();
        AABB box = player.getBoundingBox().inflate(range);
        List<Entity> nearby = player.level().getEntities(player, box);
        if (nearby.isEmpty()) {
            return;
        }

        for (Entity entity : nearby) {
            ShinyCandidate candidate = provider.read(entity);
            if (!ShinyRules.sparkles(candidate)) {
                continue;
            }
            // The box is a cube; the range is a sphere. Without this a shiny sparkles from the
            // corner of the box, which is 1.7x the configured distance.
            if (!ShinyRules.inRange(entity.distanceToSqr(player), range)) {
                continue;
            }
            // Cheaper than the raycast, so it goes first: most of what is in range at any moment is
            // behind the player.
            if (!inView(player, entity, config.viewConeDegrees())) {
                continue;
            }
            if (config.requireLineOfSight() && !player.hasLineOfSight(entity)) {
                continue;
            }
            // Keyed by the Pokemon's UUID: its network id changes every time its chunk reloads, and
            // keying on that announced the same Pokemon again on every reload.
            if (!LEDGER.claim(player.getUUID(), entity.getUUID(), tick, config.repeatTicks())) {
                continue;
            }
            PacketDistributor.sendToPlayer(player, new ShinySparklePayload(
                    entity.getId(), config.particles(), config.volume()));
        }
    }

    /**
     * Whether {@code entity} is roughly on {@code player}'s screen — measured eye to eye, so a
     * shiny at their feet is not judged against the ground.
     */
    private static boolean inView(ServerPlayer player, Entity entity, double coneDegrees) {
        net.minecraft.world.phys.Vec3 look = player.getLookAngle();
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
        return ShinyRules.inViewCone(
                look.x, look.y, look.z,
                entity.getX() - eye.x,
                entity.getY() + entity.getBbHeight() * 0.5D - eye.y,
                entity.getZ() - eye.z,
                coneDegrees);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // A single-player JVM starts a fresh server per world; a ledger carried across would silence
        // the cue in the new world for entity ids that happen to collide.
        LEDGER.clear();
    }

    /** Live ledger entries — for {@code /teras shiny} and the tests. */
    public static int trackedCount() {
        return LEDGER.size();
    }

    /** Drops every remembered sighting, so the next scan treats everything as newly seen. */
    public static void reset() {
        LEDGER.clear();
    }
}
