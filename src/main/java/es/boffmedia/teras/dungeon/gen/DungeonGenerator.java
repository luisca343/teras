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
 * whatever came out). Pure logic, callable off-thread; same stage + curses + seed string always
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

    /**
     * A floor of a dungeon exactly as long as the difficulty curve was authored for, generating
     * every room shape. What every caller wanted while there was one twelve-floor dungeon.
     */
    public static DungeonLayout generate(GenConfig config, int stage, Set<Curse> curses, String seedString) {
        return generate(config, FloorDepth.of(config, stage), curses,
                EnumSet.allOf(RoomShape.class), seedString);
    }

    /**
     * @param depth  where this floor sits in its own dungeon — see {@link FloorDepth}, which keeps
     *               "the party's first floor", "this dungeon's last floor" and "how deep the
     *               difficulty curve thinks we are" from collapsing into one number
     * @param shapes the room shapes the piso declares. A shape absent here is never generated, so
     *               the piso is never asked for a room it did not author — the one lever that cuts
     *               authoring cost without a fallback between pisos
     */
    public static DungeonLayout generate(GenConfig config, FloorDepth depth, Set<Curse> curses,
                                         Set<RoomShape> shapes, String seedString) {
        if (!depth.isValid()) {
            throw new IllegalArgumentException("Invalid stage: " + depth.stage()
                    + " in a dungeon of " + depth.dungeonLength() + " floors");
        }
        int stage = depth.stage();
        String seed = (seedString == null || seedString.isBlank())
                ? Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36)
                : seedString;
        long baseSeed = DungeonSeeds.baseSeed(stage, curses, seed);

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
                return new DungeonLayout(stage, curses, seed, baseSeed, attempt,
                        grid, doorGraph(grid), result.warnings());
            }
            lastErrors = result.errors();
        }
        throw new DungeonGenerationException(stage, seed, config.maxAttempts(), lastErrors);
    }

    /**
     * Isaac's room-count formula plus the legacy pad: {@code min(20, coin + 5 + stage*10/3)},
     * curse-modified, then 2–3 more. The result counts grid cells, so a 2×2 room spends four.
     */
    static int targetCells(GenConfig config, FloorDepth depth, Set<Curse> curses, SeededRng rng) {
        int base = Math.min(20, (rng.chance(0.5) ? 0 : 1) + 5 + (depth.curveStage() * 10) / 3);
        int rooms = base;
        if (curses.contains(Curse.LABYRINTH)) {
            rooms = Math.min(config.labyrinthRoomCap(), (int) (rooms * config.labyrinthMultiplier()));
        } else if (curses.contains(Curse.LOST)) {
            rooms += config.lostRoomBonus();
        }
        if (depth.isFinal()) {
            rooms = config.finalStageRooms();
        }
        return rooms + 2 + (rng.chance(0.5) ? 0 : 1);
    }

    /** Enough dead ends for the special rooms: 5, +1 past stage 1, +1 labyrinth, +2 final stage. */
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
