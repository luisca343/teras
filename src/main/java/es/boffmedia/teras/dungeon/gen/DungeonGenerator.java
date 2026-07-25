package es.boffmedia.teras.dungeon.gen;

import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.DoorEdge;
import es.boffmedia.teras.dungeon.model.DoorKind;
import es.boffmedia.teras.dungeon.model.DungeonLayout;
import es.boffmedia.teras.dungeon.model.DungeonSeeds;
import es.boffmedia.teras.dungeon.model.GridDir;
import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.RoomGrid;
import es.boffmedia.teras.dungeon.model.RoomShape;
import es.boffmedia.teras.dungeon.model.SeededRng;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The floor pipeline: carve, place specials, validate — rerolling on a derived seed until a floor
 * passes or attempts run out (Isaac's own answer to constraint failure; the legacy builder shipped
 * whatever came out). Pure logic, callable off-thread; same floor + curses + seed string always
 * yields the same {@link DungeonLayout}.
 */
public final class DungeonGenerator {

    /**
     * The dead end the boss spends when it grows into a 2×2: its cell stops being a 1×1 room, and
     * the validator re-checks the minimum <i>after</i> placement. {@link RoomCarver} tops up to
     * exactly the number it is given, so without this reserve every floor that grew its boss would
     * fail validation and lean on the reroll loop to find one that could not.
     */
    private static final int BOSS_GROWTH_RESERVE = 1;

    private DungeonGenerator() {}

    /** The canonical floor {@code floor}, generating every room shape. */
    public static DungeonLayout generate(GenConfig config, int floor, Set<Curse> curses, String seedString) {
        return generate(config, FloorDepth.of(config, floor), curses,
                EnumSet.allOf(RoomShape.class), seedString);
    }

    /**
     * @param depth  which canonical floor this is — see {@link FloorDepth}. Never the run's own
     *               stage: a dungeon is a window onto the sequence, and the generator is told only
     *               which floor of the sequence it is building so that floor comes out the same
     *               whichever window asked for it
     * @param shapes the room shapes the piso declares. A shape absent here is never generated, so
     *               the piso is never asked for a room it did not author — the one lever that cuts
     *               authoring cost without a fallback between pisos
     */
    public static DungeonLayout generate(GenConfig config, FloorDepth depth, Set<Curse> curses,
                                         Set<RoomShape> shapes, String seedString) {
        return generate(config, depth, curses, shapes, seedString, SatelliteChances.NONE);
    }

    /**
     * @param satellites the odds that the seal chamber gets El Acreedor's room, la Orden's, or
     *                   both — decided by {@link SatelliteOdds} from the run's ledger and the floor
     *                   just played, and rolled here so the floor stays a function of its seed
     */
    public static DungeonLayout generate(GenConfig config, FloorDepth depth, Set<Curse> curses,
                                         Set<RoomShape> shapes, String seedString,
                                         SatelliteChances satellites) {
        if (!depth.isValid()) {
            throw new IllegalArgumentException("Invalid floor: " + depth.floor()
                    + " in a sequence of " + depth.canonicalFloors() + " floors");
        }
        int floor = depth.floor();
        String seed = (seedString == null || seedString.isBlank())
                ? Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36)
                : seedString;
        long baseSeed = DungeonSeeds.baseSeed(floor, curses, seed);

        List<String> lastErrors = List.of("generation never ran");
        for (int attempt = 0; attempt < config.maxAttempts(); attempt++) {
            SeededRng rng = new SeededRng(DungeonSeeds.derive(baseSeed, attempt));
            int targetCells = targetCells(config, depth, curses, rng);
            int minDeadEnds = minDeadEnds(config, depth, curses);

            RoomGrid grid = RoomCarver.carve(config, targetCells, minDeadEnds + BOSS_GROWTH_RESERVE,
                    shapes, rng);
            SpecialRoomPlacer.place(grid, config, depth, shapes, rng);

            LayoutValidator.Result result = LayoutValidator.validate(grid, config, depth, targetCells, minDeadEnds);
            if (result.valid()) {
                // Post rooms live outside the loop's economy: the validated playfield is embedded
                // into the margin grid and only then extended, so nothing appended here can change
                // what generation produced or cost a reroll attempt.
                RoomGrid full = PostRooms.embed(grid, config.postMargin());
                List<DoorEdge> doors = doorGraph(full);
                if (config.exitRoom()) {
                    PostRooms.appendExitRoom(full, doors, rng);
                    // Both rolls always run, even when a chance is zero, so the rng stream — and
                    // therefore the floor — does not shift with who happens to be visiting.
                    boolean acreedor = rng.chance(satellites.acreedor() / 100.0);
                    boolean orden = rng.chance(satellites.orden() / 100.0);
                    PostRooms.appendSatellites(full, doors, rng, acreedor, orden);
                }
                return new DungeonLayout(floor, curses, seed, baseSeed, attempt,
                        full, doors, result.warnings());
            }
            lastErrors = result.errors();
        }
        // A forced 2×2 boss is a nice-to-have, never worth failing a run over. On a small floor it
        // can, rarely, be unsatisfiable together with the dead-end minimum, and every reroll misses;
        // rather than refuse the run, drop the requirement and regenerate (same seed → deterministic)
        // with a 1×1 boss allowed. Measured ~3 in 100k floors, all at floor 1.
        if (config.forceBossQuad()) {
            es.boffmedia.teras.Teras.LOGGER.warn("Dungeons: could not place a 2×2 boss for floor {} "
                    + "after {} attempts; this floor takes a 1×1 boss and a normal exit door",
                    floor, config.maxAttempts());
            return generate(config.withForceBossQuad(false), depth, curses, shapes, seedString,
                    satellites);
        }
        throw new DungeonGenerationException(floor, seed, config.maxAttempts(), lastErrors);
    }

    /**
     * The floor's authored cell budget, jittered, then curse-modified. Indexed by the canonical
     * floor, which is what makes a floor the same size wherever it is played from. The result
     * counts grid cells, so a 2×2 room spends four.
     *
     * <p>The finale is the last entry of the curve rather than a branch on {@link
     * FloorDepth#isFinal()}: a run ending is a property of the window, and a floor being the
     * deepest one is a property of the floor, and the old override confused them into giving every
     * short dungeon a twelve-floor climax.</p>
     */
    static int targetCells(GenConfig config, FloorDepth depth, Set<Curse> curses, SeededRng rng) {
        int cells = config.cellsFor(depth.floor()) + rng.between(0, config.jitter());
        if (curses.contains(Curse.LABYRINTH)) {
            cells = Math.min(config.labyrinthRoomCap(), (int) (cells * config.labyrinthMultiplier()));
        } else if (curses.contains(Curse.LOST)) {
            cells += config.lostRoomBonus();
        }
        return cells;
    }

    /** Enough dead ends for the special rooms: 5, +1 past floor 1, +1 labyrinth, +2 final floor. */
    static int minDeadEnds(GenConfig config, FloorDepth depth, Set<Curse> curses) {
        int minDeadEnds = 5;
        if (!depth.isFirst()) {
            minDeadEnds++;
        }
        if (curses.contains(Curse.LABYRINTH)) {
            minDeadEnds++;
        }
        if (depth.isFinal()) {
            minDeadEnds += 2;
        }
        return minDeadEnds;
    }

    /** One edge per adjacent cell pair of distinct rooms, scanning east and south once each. */
    private static List<DoorEdge> doorGraph(RoomGrid grid) {
        List<DoorEdge> doors = new ArrayList<>();
        for (int y = 0; y < grid.size(); y++) {
            for (int x = 0; x < grid.size(); x++) {
                GridPos pos = new GridPos(x, y);
                Room room = grid.roomAt(pos);
                if (room == null) {
                    continue;
                }
                for (GridDir dir : List.of(GridDir.EAST, GridDir.SOUTH)) {
                    Room neighbor = grid.roomAt(pos.step(dir));
                    if (neighbor != null && neighbor != room) {
                        doors.add(new DoorEdge(pos, dir, room, neighbor,
                                DoorKind.between(room.type(), neighbor.type())));
                    }
                }
            }
        }
        return doors;
    }
}
