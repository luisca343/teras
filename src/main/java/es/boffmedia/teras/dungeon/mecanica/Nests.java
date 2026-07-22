package es.boffmedia.teras.dungeon.mecanica;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.build.BuiltDungeon;
import es.boffmedia.teras.dungeon.build.TemplateMarkers;
import es.boffmedia.teras.dungeon.encounter.EnemySpawner;
import es.boffmedia.teras.dungeon.encounter.SpawnTables;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.piso.FloorDef;
import es.boffmedia.teras.dungeon.piso.MechanicDef;
import es.boffmedia.teras.dungeon.run.RunEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * The infestation mechanic: {@code nido} markers hatch a second wave a few seconds after the doors
 * shut.
 *
 * <p>Visible before it opens, on purpose. An egg sac the party can see on entry is a decision — fight
 * near it or away from it — where one that bursts unannounced is only a surprise the first time and
 * an annoyance afterwards.</p>
 *
 * <p>The count per nest is <b>fixed</b>. Hatchlings enter the room's kill ledger, so the room cannot
 * clear while they live; a nest that kept producing would be a room that never clears at all.</p>
 */
public final class Nests implements Mechanic {

    /** The mechanic key a piso declares to get this. */
    public static final String MECHANIC = "infestacion";

    /**
     * Ticks after the doors seal before the sacs crack. Long enough to have started fighting.
     * Overridable per piso as {@code retrasoTicks} — the shipped value is a feel decision, and the
     * person running the server is better placed to tune it than the person who guessed it.
     */
    public static final String P_DELAY = "retrasoTicks";
    private static final int DEFAULT_DELAY = 80;

    /** How many hatch per sac. {@code porNido}. */
    public static final String P_PER_NEST = "porNido";
    private static final int DEFAULT_PER_NEST = 2;

    /**
     * What a nest hatches. Chaff, not a second boss: the pressure is the timing, not the tier.
     * {@code cria} names a {@code GeoEnemyVariant}, so a piso can hatch something else entirely.
     */
    public static final String P_HATCHLING = "cria";
    private static final String DEFAULT_HATCHLING = "cria";

    @Override
    public String id() {
        return MECHANIC;
    }

    /** Whether this floor's piso runs the infestation. */
    public static boolean active(FloorDef piso) {
        return piso != null && piso.mecanica().is(MECHANIC);
    }

    /**
     * Schedules the room's nests to hatch. Called when a room seals; a piso without this mechanic
     * never reaches here, because {@link Mechanics#of} hands it a no-op instead.
     */
    @Override
    public void onRoomSealed(ServerLevel level, BuiltDungeon built, Room room, MechanicDef def) {
        List<BlockPos> nests = markers(built, room);
        if (nests.isEmpty()) {
            return;
        }
        MinecraftServer server = level.getServer();
        int delay = def.intParam(P_DELAY, DEFAULT_DELAY);
        server.tell(new TickTask(server.getTickCount() + delay,
                () -> hatch(level, room, nests, def)));
    }

    /**
     * A room authored with nests inside a piso that does not run the infestation: the sacs are
     * visible, nothing hatches, and nothing anywhere says why. Checked by the caller, which is the
     * only place that can see both the room and the piso that declined the mechanic.
     */
    public static void warnIfUnhatchable(BuiltDungeon built, Room room, FloorDef piso) {
        if (active(piso)) {
            return;
        }
        List<BlockPos> nests = markers(built, room);
        if (nests.isEmpty()) {
            return;
        }
        Teras.LOGGER.warn("Dungeons: {} has {} 'nido' markers but piso '{}' runs mecanica {} — set "
                        + "it to '{}' or the sacs will never hatch",
                room, nests.size(), piso == null ? "?" : piso.id(),
                piso == null ? "(ninguna)" : piso.mecanica(), MECHANIC);
    }

    private static void hatch(ServerLevel level, Room room, List<BlockPos> nests, MechanicDef def) {
        SpawnTables.SpawnEntry entry = new SpawnTables.SpawnEntry(
                SpawnTables.Kind.GEO, def.param(P_HATCHLING, DEFAULT_HATCHLING), 0, 1);
        int perNest = Math.max(1, def.intParam(P_PER_NEST, DEFAULT_PER_NEST));
        for (BlockPos nest : nests) {
            // The sac is consumed by hatching: a nest that stayed intact would read as one that
            // could go again.
            if (level.getBlockState(nest).is(Blocks.SNIFFER_EGG)) {
                level.destroyBlock(nest, false);
            }
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ITEM_SLIME,
                    nest.getX() + 0.5, nest.getY() + 0.5, nest.getZ() + 0.5, 24, 0.4, 0.4, 0.4, 0.05);
            for (int i = 0; i < perNest; i++) {
                Entity add = EnemySpawner.spawnSummon(level, entry, nest.above());
                if (add == null) {
                    continue;
                }
                // Into the ledger, or the room clears with hatchlings still on the floor. Refused
                // means nothing is tracking this room — a floor that ended mid-hatch.
                if (!RunEngine.registerNestSpawn(level, room, add)) {
                    add.discard();
                }
            }
        }
        Teras.LOGGER.debug("Dungeons: {} nests hatched", nests.size());
    }

    private static List<BlockPos> markers(BuiltDungeon built, Room room) {
        List<BlockPos> positions = new java.util.ArrayList<>();
        for (TemplateMarkers.Marker marker : built.markers().getOrDefault(room, List.of())) {
            if (marker.kind().equals("nido")) {
                positions.add(marker.pos());
            }
        }
        return positions;
    }
}
