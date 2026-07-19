package es.boffmedia.teras.dungeon.gen;

import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.DoorEdge;
import es.boffmedia.teras.dungeon.model.DoorKind;
import es.boffmedia.teras.dungeon.model.DungeonLayout;
import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.RoomShape;
import es.boffmedia.teras.dungeon.model.RoomType;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The structural guarantees every shipped floor carries, swept across stages, curses and seeds.
 * This is the regression net for the legacy failure modes: the out-of-bounds carve crash, floors
 * without a shop/treasure, and rooms only reachable through a secret room.
 */
class GenerationInvariantsTest {

    private static final GenConfig CONFIG = GenConfig.defaults();
    private static final int SEEDS_PER_COMBO = 50;

    @Test
    void everyGeneratedFloorHoldsItsInvariants() {
        for (int stage : new int[] {1, 3, 6, 12}) {
            for (Set<Curse> curses : List.of(
                    Set.<Curse>of(), Set.of(Curse.LABYRINTH), Set.of(Curse.LOST))) {
                for (int i = 0; i < SEEDS_PER_COMBO; i++) {
                    DungeonLayout layout = DungeonGenerator.generate(
                            CONFIG, stage, curses, "invariant-" + i);
                    assertInvariants(layout, stage, curses);
                }
            }
        }
    }

    /**
     * The 2x2 chamber is the point of the feature and its constraint is strict enough — three free
     * cells that themselves touch nothing — that a tightening elsewhere could silently switch it
     * off. Pinned as a rate rather than per seed: a floor with nowhere to put the chamber correctly
     * keeps a 1x1 boss.
     *
     * <p>Measured at 92–95% across stages once the boss started claiming the farthest dead end that
     * can actually grow rather than the farthest outright; it was 29% before. The bar sits well
     * under that so ordinary generation drift does not fail the build, but a regression to the old
     * pick-then-hope behaviour would.</p>
     */
    @Test
    void theBossChamberGrowsIntoAQuadOnMostFloors() {
        int floors = 200;
        int quads = 0;
        for (int i = 0; i < floors; i++) {
            DungeonLayout layout = DungeonGenerator.generate(CONFIG, 6, Set.of(), "grow-" + i);
            if (layout.roomOfType(RoomType.BOSS).orElseThrow().shape() == RoomShape.QUAD) {
                quads++;
            }
        }
        assertTrue(quads >= floors * 85 / 100,
                "boss grew on only " + quads + " of " + floors + " floors");
    }

    /** How deep into the floor a room sits: the distance of its nearest cell to the start. */
    private int distanceTo(Map<GridPos, Integer> distances, Room room) {
        return room.cells().stream()
                .mapToInt(cell -> distances.getOrDefault(cell, Integer.MAX_VALUE))
                .min().orElseThrow();
    }

    private void assertInvariants(DungeonLayout layout, int stage, Set<Curse> curses) {
        String context = "stage " + stage + " curses " + curses + " seed " + layout.seedString();

        assertEquals(1, count(layout, RoomType.START), context);
        assertEquals(1, count(layout, RoomType.BOSS), context);
        assertTrue(count(layout, RoomType.SHOP) >= 1, context + ": no shop");
        assertTrue(count(layout, RoomType.TREASURE) >= 1, context + ": no treasure");
        assertEquals(layout.grid().center(), layout.start().anchor(), context);

        for (Room room : layout.rooms()) {
            if (!room.type().isSpecial()) {
                continue;
            }
            // The boss chamber is the one special room allowed to be large, and only as a 2x2.
            boolean allowed = room.isSingle()
                    || (room.type() == RoomType.BOSS && room.shape() == RoomShape.QUAD);
            assertTrue(allowed, context + ": special room with an unsupported shape: " + room);
        }

        Room boss = layout.roomOfType(RoomType.BOSS).orElseThrow();
        // One way in, 1x1 or grown. occupiedNeighborCount cannot say this any more — it counts a
        // quad's own cells as neighbours of its anchor — so ask the door graph, which is what the
        // seal actually operates on.
        assertEquals(1, layout.doorsOf(boss).size(), context + ": boss not on a dead end");

        Map<GridPos, Integer> distances = layout.grid().distancesFromCenter();
        Room treasure = layout.roomOfType(RoomType.TREASURE).orElseThrow();
        assertTrue(distanceTo(distances, boss) >= distanceTo(distances, treasure),
                context + ": treasure farther than boss");

        int minDeadEnds = DungeonGenerator.minDeadEnds(CONFIG, stage, curses);
        assertTrue(layout.grid().deadEndCells().size() >= minDeadEnds,
                context + ": only " + layout.grid().deadEndCells().size() + " dead ends");

        assertDoorGraph(layout, context);
    }

    private void assertDoorGraph(DungeonLayout layout, String context) {
        for (DoorEdge door : layout.doors()) {
            assertTrue(door.from() != door.to(), context + ": self-edge");
            assertEquals(door.from(), layout.grid().roomAt(door.cell()), context);
            assertEquals(door.to(), layout.grid().roomAt(door.neighborCell()), context);

            boolean secretEdge = door.from().type().isSecret() || door.to().type().isSecret();
            assertEquals(secretEdge,
                    door.kind() == DoorKind.SECRET_CRACK || door.kind() == DoorKind.HIDDEN,
                    context + ": wrong kind on " + door);
        }

        for (Room room : layout.rooms()) {
            if (room.type() == RoomType.SUPER_SECRET) {
                assertTrue(layout.doorsOf(room).stream().allMatch(d -> d.kind() == DoorKind.HIDDEN),
                        context + ": super-secret with a hinted door");
            }
            if (room.type() == RoomType.BOSS) {
                assertTrue(layout.doorsOf(room).stream().allMatch(d -> d.kind() == DoorKind.BOSS),
                        context + ": boss with a non-boss door");
            }
            assertTrue(!layout.doorsOf(room).isEmpty(), context + ": doorless room " + room);
        }

        assertEquals(Set.copyOf(walkableRooms(layout)), nonSecretRooms(layout),
                context + ": door graph does not cover the floor");
    }

    /** Rooms reachable from the start over non-secret door edges only. */
    private Set<Room> walkableRooms(DungeonLayout layout) {
        Set<Room> visited = new HashSet<>();
        ArrayDeque<Room> queue = new ArrayDeque<>();
        queue.add(layout.start());
        visited.add(layout.start());
        while (!queue.isEmpty()) {
            Room current = queue.poll();
            for (DoorEdge door : layout.doorsOf(current)) {
                if (door.kind() == DoorKind.SECRET_CRACK || door.kind() == DoorKind.HIDDEN) {
                    continue;
                }
                Room other = door.from() == current ? door.to() : door.from();
                if (visited.add(other)) {
                    queue.add(other);
                }
            }
        }
        return visited;
    }

    private Set<Room> nonSecretRooms(DungeonLayout layout) {
        Set<Room> rooms = new HashSet<>();
        for (Room room : layout.rooms()) {
            if (!room.type().isSecret()) {
                rooms.add(room);
            }
        }
        return rooms;
    }

    private long count(DungeonLayout layout, RoomType type) {
        return layout.rooms().stream().filter(r -> r.type() == type).count();
    }
}
