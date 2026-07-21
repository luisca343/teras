package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.dungeon.gen.DungeonGenerator;
import es.boffmedia.teras.dungeon.gen.GenConfig;
import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.DungeonLayout;
import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.Room;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A multi-cell room must be discovered from <b>any</b> of its cells, and the minimap must mark the
 * cell the player is standing in.
 *
 * <p>The minimap draws the current-cell outline where the payload's cell matches the player's, so
 * "the room is not marked" is always one of two things: the room was never discovered, or the
 * player's cell is not among the cells the payload sent. Both are properties of the model rather
 * than of the renderer, so both are testable here without a game.</p>
 */
class MultiCellDiscoveryTest {

    private static DungeonLayout layout(String seed) {
        // LABYRINTH widens the floor, which is what makes multi-cell rooms plentiful in one roll.
        return DungeonGenerator.generate(GenConfig.defaults(), 6, Set.of(Curse.LABYRINTH), seed);
    }

    /** Every cell of a room resolves back to that room — the grid contract the map depends on. */
    @Test
    void everyCellOfEveryRoomResolvesToIt() {
        for (int seed = 0; seed < 60; seed++) {
            DungeonLayout layout = layout("cells-" + seed);
            for (Room room : layout.rooms()) {
                for (GridPos cell : room.cells()) {
                    assertSame(room, layout.grid().roomAt(cell),
                            "seed " + seed + ": " + room + " is not registered at " + cell);
                }
            }
        }
    }

    /**
     * Entering any cell of a multi-cell room discovers the whole room. This is the invariant the
     * minimap marking rests on: the payload sends every cell of a discovered room, so if discovery
     * works from each cell the player's cell is always among them.
     */
    @Test
    void anyCellDiscoversTheWholeRoom() {
        int multiCellRoomsSeen = 0;
        for (int seed = 0; seed < 60; seed++) {
            DungeonLayout layout = layout("discover-" + seed);
            for (Room room : layout.rooms()) {
                if (room.isSingle()) {
                    continue;
                }
                multiCellRoomsSeen++;
                for (GridPos cell : room.cells()) {
                    RunCore core = new RunCore(layout, new NoopCallbacks());
                    core.start();
                    core.playerEnteredCell(UUID.randomUUID(), cell);
                    assertTrue(core.discovered().contains(room),
                            "seed " + seed + ": standing in " + cell + " did not discover " + room);
                }
            }
        }
        assertTrue(multiCellRoomsSeen > 50,
                "expected plenty of multi-cell rooms to exercise, saw " + multiCellRoomsSeen);
    }

    /**
     * A discovered room contributes every one of its cells to the map, so whichever cell the player
     * occupies is drawable. Mirrors the payload's first pass in {@code RunEngine.sendMap}.
     */
    @Test
    void aDiscoveredRoomContributesAllItsCells() {
        for (int seed = 0; seed < 40; seed++) {
            DungeonLayout layout = layout("cover-" + seed);
            for (Room room : layout.rooms()) {
                if (room.isSingle()) {
                    continue;
                }
                RunCore core = new RunCore(layout, new NoopCallbacks());
                core.start();
                GridPos entered = room.cells().get(room.cells().size() - 1);
                core.playerEnteredCell(UUID.randomUUID(), entered);
                Room discovered = core.discovered().stream()
                        .filter(r -> r == room).findFirst().orElse(null);
                if (discovered == null) {
                    continue;
                }
                assertTrue(discovered.cells().contains(entered),
                        "the cell walked into is not among the cells the map would send");
                assertEquals(room.shape().cellCount(), discovered.cells().size());
            }
        }
    }

    /** Callbacks that do nothing; this test is about the model, not the engine around it. */
    private static final class NoopCallbacks implements RunCallbacks {
        @Override public void roomDiscovered(Room room, UUID discoverer) {}
        @Override public void sealRoom(Room room) {}
        @Override public void openRoom(Room room) {}
        @Override public void sound(DungeonSound sound, Room room) {}
        // 0 means "nothing could spawn", which the core treats as instantly cleared — exactly what
        // this test wants, since it is about discovery and not about fighting.
        @Override public int spawnEncounter(Room room) { return 0; }
        @Override public int spawnChallengeWave(Room room, int wave) { return 0; }
        @Override public int challengeWaves(Room room) { return 1; }
        @Override public void challengeCompleted(Room room) {}
        @Override public void roomCleared(Room room) {}
        @Override public void openTrapdoor(Room bossRoom) {}
        @Override public void syncMap() {}
    }
}
