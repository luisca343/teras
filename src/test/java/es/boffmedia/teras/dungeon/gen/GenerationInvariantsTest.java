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
     * The 2×2 chamber, on the production path. The boss claims the farthest dead end outright, and a
     * dead end the carve grew has 2×2 room around it only about 29% of the time — so the carve is
     * told to end in one: {@code RoomCarver.addGrowableBossDeadEnd} adds a growable dead end past
     * everything else whenever the floor did not already finish in one, for the price of a single
     * cell. {@link LayoutValidator#checkBossQuad} stays as the net, and the {@code
     * forceBossQuad(false)} fallback stays behind that.
     *
     * <p>Swept with {@code forceBossQuad}, the way every quad piso runs. Before the carve made the
     * promise this was the reroll's job and cost about 3.4 discarded floors each time; the bar is
     * kept here because what production must ship is the quad, however it is arrived at.</p>
     */
    @Test
    void theBossChamberGrowsIntoAQuadOnMostFloors() {
        GenConfig config = CONFIG.withForceBossQuad(true);
        int floors = 200;
        int quads = 0;
        for (int i = 0; i < floors; i++) {
            DungeonLayout layout = DungeonGenerator.generate(config, 6, Set.of(), "grow-" + i);
            if (layout.roomOfType(RoomType.BOSS).orElseThrow().shape() == RoomShape.QUAD) {
                quads++;
            }
        }
        assertTrue(quads >= floors * 99 / 100,
                "boss grew on only " + quads + " of " + floors + " floors");
    }

    /**
     * The carve settles the boss chamber itself, so the reroll loop has almost nothing left to do.
     *
     * <p>This is the measure that says the guarantee is structural rather than statistical: mean
     * attempts ran at ~3.4 when {@code forceBossQuad} was enforced by rerolling whole floors, and at
     * ~0.15 once the carve was made to end in a growable dead end. A regression that quietly went
     * back to rerolling would still pass every other test in this class — it would just cost four
     * times the work per floor — so the cost is pinned as well as the outcome.</p>
     */
    @Test
    void theQuadGuaranteeCostsAlmostNoRerolls() {
        GenConfig config = CONFIG.withForceBossQuad(true).withExitRoom(true);
        int floors = 0;
        int attempts = 0;
        for (int stage : new int[] {1, 3, 6, 12}) {
            for (int i = 0; i < 100; i++) {
                attempts += DungeonGenerator.generate(config, stage, Set.of(), "reroll-" + i)
                        .attempt();
                floors++;
            }
        }
        assertTrue(attempts < floors, "generation spent " + attempts
                + " rerolls over " + floors + " floors; the carve should be settling the quad");
    }

    /**
     * The sala del sello, swept the same way: a 2×2 EXIT chamber appended post-generation against
     * the boss, and every door it carries is a SELLO edge that leads to the boss — one for a
     * fallback attachment, two for the aligned full-face attachment that a forced 2×2 boss gets.
     * The chamber may physically touch other rooms (post placement owns its doors, adjacency no
     * longer implies a doorway), so the guarantee that matters is where the doors lead, not the
     * neighbours.
     *
     * <p>Swept with the boss forced to 2×2 (the production path), so nearly every floor gets the
     * aligned face and the exit almost always fits. The miss rate is pinned so a placement
     * regression cannot hide behind "it's allowed to fail sometimes"; the rare miss keeps the
     * in-arena carve.</p>
     */
    @Test
    void everyExitFloorSealsItsExitRoom() {
        GenConfig config = CONFIG.withExitRoom(true).withForceBossQuad(true);
        int floors = 0;
        int missing = 0;
        int aligned = 0;
        for (int stage : new int[] {1, 3, 6, 12}) {
            for (Set<Curse> curses : List.of(
                    Set.<Curse>of(), Set.of(Curse.LABYRINTH), Set.of(Curse.LOST))) {
                for (int i = 0; i < SEEDS_PER_COMBO; i++) {
                    DungeonLayout layout = DungeonGenerator.generate(
                            config, stage, curses, "sello-" + i);
                    String context = "stage " + stage + " curses " + curses
                            + " seed " + layout.seedString();
                    assertInvariants(layout, stage, curses, false);

                    List<Room> exits = layout.rooms().stream()
                            .filter(r -> r.type() == RoomType.EXIT).toList();
                    assertTrue(exits.size() <= 1, context);
                    floors++;
                    if (exits.isEmpty()) {
                        missing++;
                        continue;
                    }
                    Room exit = exits.get(0);
                    assertEquals(RoomShape.QUAD, exit.shape(), context);
                    Room boss = layout.roomOfType(RoomType.BOSS).orElseThrow();
                    // The exit's doors are its SELLO edges to the boss (one for a fallback, two
                    // for the aligned face) plus, when the Acreedor rolled, a single DEVIL edge to
                    // its flank satellite.
                    List<DoorEdge> sello = layout.doorsOf(exit).stream()
                            .filter(d -> d.kind() == DoorKind.SELLO).toList();
                    assertTrue(sello.size() == 1 || sello.size() == 2,
                            context + " — exit has " + sello.size() + " seal doors");
                    if (sello.size() == 2) {
                        aligned++;
                    }
                    for (DoorEdge door : sello) {
                        Room other = door.from() == exit ? door.to() : door.from();
                        assertEquals(boss, other,
                                context + " — every seal door must lead to the boss");
                    }
                    for (DoorEdge door : layout.doorsOf(exit)) {
                        if (door.kind() == DoorKind.DEVIL) {
                            Room other = door.from() == exit ? door.to() : door.from();
                            assertEquals(RoomType.DEVIL_DEAL, other.type(),
                                    context + " — a DEVIL edge must lead to the Acreedor's room");
                        }
                    }
                }
            }
        }
        assertTrue(missing <= floors / 100,
                "exit chamber missing on " + missing + " of " + floors + " floors");
        // A forced 2×2 boss almost always yields the aligned full-face attachment; a small tail
        // falls back to a single-edge door, but the centered grand door must be the common case.
        assertTrue(aligned >= (floors - missing) * 9 / 10,
                "only " + aligned + " of " + (floors - missing) + " exits got the aligned face");
    }

    /**
     * The same guarantee across the whole curve, where the small early floors used to be the weak
     * spot: they have the least room to spare, so a reroll hunting for a growable farthest dead end
     * was likeliest to run out of attempts there and fall back to a 1×1 boss. Adding the dead end
     * during the carve costs one cell and does not care how tight the floor is, so floor one now
     * holds the same bar as floor twelve. Pinned so a regression that stopped forcing the quad — or
     * stopped being able to place it — fails here.
     */
    @Test
    void forcedBossQuadIsAQuadSaveTheRareFallback() {
        GenConfig config = CONFIG.withForceBossQuad(true);
        int floors = 0;
        int quad = 0;
        for (int stage : new int[] {1, 3, 6, 12}) {
            for (int i = 0; i < 300; i++) {
                DungeonLayout layout = DungeonGenerator.generate(config, stage, Set.of(),
                        "forcequad-" + i);
                Room boss = layout.roomOfType(RoomType.BOSS).orElseThrow();
                floors++;
                if (boss.shape() == RoomShape.QUAD) {
                    quad++;
                }
            }
        }
        assertTrue(quad >= floors * 99 / 100, quad + " of " + floors + " bosses are 2×2");
    }

    /** The margin ring exists on every floor — post-room space is uniform, exit or not. */
    @Test
    void everyFloorCarriesThePostMargin() {
        DungeonLayout layout = DungeonGenerator.generate(CONFIG, 3, Set.of(), "margin-0");
        assertEquals(CONFIG.gridSize() + 2 * CONFIG.postMargin(), layout.grid().size());
        for (Room room : layout.rooms()) {
            for (GridPos cell : room.cells()) {
                assertTrue(cell.x() >= CONFIG.postMargin()
                        && cell.x() < CONFIG.postMargin() + CONFIG.gridSize()
                        && cell.y() >= CONFIG.postMargin()
                        && cell.y() < CONFIG.postMargin() + CONFIG.gridSize(),
                        "playfield room outside the window: " + room);
            }
        }
    }

    /** How deep into the floor a room sits: the distance of its nearest cell to the start. */
    private int distanceTo(Map<GridPos, Integer> distances, Room room) {
        return room.cells().stream()
                .mapToInt(cell -> distances.getOrDefault(cell, Integer.MAX_VALUE))
                .min().orElseThrow();
    }

    private void assertInvariants(DungeonLayout layout, int stage, Set<Curse> curses) {
        assertInvariants(layout, stage, curses, true);
    }

    /**
     * @param bossMustBeDeepest whether to require the boss to sit at least as deep as the treasure.
     *        True for ordinary generation, where {@code keepBossBeyondTreasure} guarantees it.
     *        <b>False under {@code forceBossQuad}</b>: forcing the boss to be a <i>growable</i> 2×2
     *        can, on rare floors, leave a deeper dead end that could not grow to serve as the boss,
     *        so that dead end becomes the treasure and the boss is a shade shallower. That is an
     *        accepted consequence of the grander-boss trade, not a placement bug.
     */
    private void assertInvariants(DungeonLayout layout, int stage, Set<Curse> curses,
                                  boolean bossMustBeDeepest) {
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
            // The boss chamber is the one PLACED special room allowed to be large, and only as a
            // 2x2; the exit chamber is appended after validation and is always a 2x2.
            boolean allowed = room.isSingle()
                    || (room.type() == RoomType.BOSS && room.shape() == RoomShape.QUAD)
                    || (room.type() == RoomType.EXIT && room.shape() == RoomShape.QUAD);
            assertTrue(allowed, context + ": special room with an unsupported shape: " + room);
        }

        Room boss = layout.roomOfType(RoomType.BOSS).orElseThrow();
        // One way in, 1x1 or grown. occupiedNeighborCount cannot say this any more — it counts a
        // quad's own cells as neighbours of its anchor — so ask the door graph, which is what the
        // seal actually operates on. The exit room's barred SELLO edge is not a way in.
        assertEquals(1, layout.doorsOf(boss).stream()
                .filter(d -> d.kind() != DoorKind.SELLO).count(),
                context + ": boss not on a dead end");

        if (bossMustBeDeepest) {
            Map<GridPos, Integer> distances = layout.grid().distancesFromCenter();
            Room treasure = layout.roomOfType(RoomType.TREASURE).orElseThrow();
            assertTrue(distanceTo(distances, boss) >= distanceTo(distances, treasure),
                    context + ": treasure farther than boss");
        }

        int minDeadEnds = DungeonGenerator.minDeadEnds(CONFIG, FloorDepth.of(CONFIG, stage), curses);
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
                assertTrue(layout.doorsOf(room).stream().allMatch(
                        d -> d.kind() == DoorKind.BOSS || d.kind() == DoorKind.SELLO),
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
