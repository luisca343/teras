package es.boffmedia.teras.dungeon.gen;

import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.DoorEdge;
import es.boffmedia.teras.dungeon.model.DoorKind;
import es.boffmedia.teras.dungeon.model.DungeonLayout;
import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.Room;
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

    private void assertInvariants(DungeonLayout layout, int stage, Set<Curse> curses) {
        String context = "stage " + stage + " curses " + curses + " seed " + layout.seedString();

        assertEquals(1, count(layout, RoomType.START), context);
        assertEquals(1, count(layout, RoomType.BOSS), context);
        assertTrue(count(layout, RoomType.SHOP) >= 1, context + ": no shop");
        assertTrue(count(layout, RoomType.TREASURE) >= 1, context + ": no treasure");
        assertEquals(layout.grid().center(), layout.start().anchor(), context);

        for (Room room : layout.rooms()) {
            if (room.type().isSpecial()) {
                assertTrue(room.isSingle(), context + ": special room not 1x1: " + room);
            }
        }

        Room boss = layout.roomOfType(RoomType.BOSS).orElseThrow();
        assertEquals(1, layout.grid().occupiedNeighborCount(boss.anchor()),
                context + ": boss not on a dead end");

        Map<GridPos, Integer> distances = layout.grid().distancesFromCenter();
        Room treasure = layout.roomOfType(RoomType.TREASURE).orElseThrow();
        assertTrue(distances.get(boss.anchor()) >= distances.get(treasure.anchor()),
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
