package es.boffmedia.teras.dungeon.gen;

import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.DoorEdge;
import es.boffmedia.teras.dungeon.model.DoorKind;
import es.boffmedia.teras.dungeon.model.DungeonLayout;
import es.boffmedia.teras.dungeon.model.GridDir;
import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.RoomShape;
import es.boffmedia.teras.dungeon.model.RoomType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seal chamber's satellites: El Acreedor and la Orden, hung off its flanks after generation.
 * Everything here is about geometry and doors — <i>whether</i> they are wanted is
 * {@link SatelliteOddsTest}'s job.
 */
class SatellitePlacementTest {

    private static final GenConfig CONFIG =
            GenConfig.defaults().withExitRoom(true).withForceBossQuad(true);

    private static DungeonLayout floor(String seed, int stage, SatelliteChances chances) {
        return DungeonGenerator.generate(CONFIG, FloorDepth.of(CONFIG, stage), Set.of(),
                EnumSet.allOf(RoomShape.class), seed, chances);
    }

    private static Room roomOf(DungeonLayout layout, RoomType type) {
        for (Room room : layout.rooms()) {
            if (room.type() == type) {
                return room;
            }
        }
        return null;
    }

    private static List<DoorEdge> edgesTouching(DungeonLayout layout, Room room) {
        List<DoorEdge> edges = new ArrayList<>();
        for (DoorEdge door : layout.doors()) {
            if (door.from() == room || door.to() == room) {
                edges.add(door);
            }
        }
        return edges;
    }

    /**
     * Which way the sala del sello lies from a satellite. Read off adjacency rather than centroids:
     * the exit is 2×2, so two satellites on the north and south flanks can share an x centroid and
     * still be on genuinely opposite walls.
     */
    private static GridDir towardExit(DungeonLayout layout, Room exit, Room satellite) {
        GridPos cell = satellite.cells().get(0);
        for (GridDir dir : GridDir.values()) {
            if (layout.grid().roomAt(cell.step(dir)) == exit) {
                return dir;
            }
        }
        return null;
    }

    @Test
    void noSatellitesAreAppendedWhenNobodyIsComing() {
        for (int i = 0; i < 200; i++) {
            DungeonLayout layout = floor("quiet-" + i, 4, SatelliteChances.NONE);
            assertNull(roomOf(layout, RoomType.ORDEN), "la Orden came uninvited");
            assertNull(roomOf(layout, RoomType.DEVIL_DEAL),
                    "the Acreedor came uninvited — the playfield devil must stay off exit pisos");
        }
    }

    @Test
    void eachSatelliteIsASingleCellHangingOffTheSealChamberByOneDoor() {
        int placed = 0;
        for (int i = 0; i < 400; i++) {
            DungeonLayout layout = floor("both-" + i, 5, new SatelliteChances(100, 100));
            Room exit = roomOf(layout, RoomType.EXIT);
            if (exit == null) {
                continue;
            }
            for (RoomType type : List.of(RoomType.DEVIL_DEAL, RoomType.ORDEN)) {
                Room satellite = roomOf(layout, type);
                if (satellite == null) {
                    continue;
                }
                placed++;
                assertEquals(RoomShape.SINGLE, satellite.shape(), type + " must be 1×1");

                // Exactly one way in, and it goes to the sala del sello — never to the boss, never
                // to a playfield room it happens to lean on. A second entrance is a way past the
                // seal, which is the whole reason these edges are appended by hand.
                List<DoorEdge> edges = edgesTouching(layout, satellite);
                assertEquals(1, edges.size(), type + " grew a second entrance");
                DoorEdge door = edges.get(0);
                assertTrue(door.from() == exit || door.to() == exit,
                        type + "'s door does not lead to the sala del sello");
                assertEquals(type == RoomType.ORDEN ? DoorKind.GRACIA : DoorKind.DEVIL, door.kind());
            }
        }
        assertTrue(placed > 0, "the sweep never placed a satellite at all");
    }

    @Test
    void theTwoFaceEachOtherAcrossTheChamber() {
        int both = 0;
        int trials = 600;
        for (int i = 0; i < trials; i++) {
            DungeonLayout layout = floor("facing-" + i, 6, new SatelliteChances(100, 100));
            Room exit = roomOf(layout, RoomType.EXIT);
            Room acreedor = roomOf(layout, RoomType.DEVIL_DEAL);
            Room orden = roomOf(layout, RoomType.ORDEN);
            if (exit == null || acreedor == null || orden == null) {
                continue;
            }
            both++;
            GridDir a = towardExit(layout, exit, acreedor);
            GridDir o = towardExit(layout, exit, orden);
            assertNotNull(a, "the Acreedor is not adjacent to the sala del sello");
            assertNotNull(o, "la Orden is not adjacent to the sala del sello");
            // Opposite flanks: temptation on one wall, salvation on the other.
            assertEquals(a.opposite(), o, "the satellites share a wall instead of facing");
        }
        // Measured at ~99.8 % even on the deepest floors; a loose floor keeps this from being flaky
        // while still catching a regression that stops placing the pair at all.
        assertTrue(both > trials * 0.95,
                "both satellites should fit on nearly every floor, got " + both + "/" + trials);
    }

    @Test
    void aFloorMayReceiveOnlyOneOfThem() {
        boolean sawLoneOrden = false;
        boolean sawLoneAcreedor = false;
        for (int i = 0; i < 200 && !(sawLoneOrden && sawLoneAcreedor); i++) {
            DungeonLayout onlyHer = floor("her-" + i, 3, new SatelliteChances(0, 100));
            if (roomOf(onlyHer, RoomType.ORDEN) != null) {
                assertNull(roomOf(onlyHer, RoomType.DEVIL_DEAL));
                sawLoneOrden = true;
            }
            DungeonLayout onlyHim = floor("him-" + i, 3, new SatelliteChances(100, 0));
            if (roomOf(onlyHim, RoomType.DEVIL_DEAL) != null) {
                assertNull(roomOf(onlyHim, RoomType.ORDEN));
                sawLoneAcreedor = true;
            }
        }
        assertTrue(sawLoneOrden, "la Orden never appeared alone");
        assertTrue(sawLoneAcreedor, "el Acreedor never appeared alone");
    }

    @Test
    void theSealChamberKeepsItsOwnDoorsToTheBoss() {
        // The satellites must not disturb what §61 built: the exit still reaches the boss, and the
        // grand door still needs its two parallel SELLO edges to be centred on the shared face.
        for (int i = 0; i < 300; i++) {
            DungeonLayout layout = floor("sello-" + i, 7, new SatelliteChances(100, 100));
            Room exit = roomOf(layout, RoomType.EXIT);
            if (exit == null) {
                continue;
            }
            List<DoorEdge> sello = new ArrayList<>();
            for (DoorEdge door : layout.doors()) {
                if (door.kind() == DoorKind.SELLO) {
                    sello.add(door);
                }
            }
            assertTrue(sello.size() >= 1, "the sala del sello lost its door to the arena");
            for (DoorEdge door : sello) {
                assertTrue(door.from().type() == RoomType.BOSS || door.to().type() == RoomType.BOSS,
                        "a SELLO edge that does not touch the boss");
            }
            assertNotNull(roomOf(layout, RoomType.BOSS));
        }
    }
}
