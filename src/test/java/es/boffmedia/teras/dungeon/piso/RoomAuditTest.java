package es.boffmedia.teras.dungeon.piso;

import es.boffmedia.teras.dungeon.model.RoomShape;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules a finished room obeys. Every one of these describes a room that looks correct on the
 * editor pad and fails in a run — which is exactly why they are worth checking mechanically, and
 * why the check is pure enough to hold against fixtures instead of against whatever is on disk.
 */
class RoomAuditTest {

    private static final int SIZE = 21;
    private static final int HEIGHT = 12;
    private static final int DOOR_W = 3;
    private static final int DOOR_H = 3;
    private static final int WAVE_MAX = 5;

    /** A room that satisfies every rule: solid floor over each owned cell, clear aprons. */
    private static Builder good(RoomShape shape) {
        return new Builder(shape);
    }

    private static final class Builder {
        private final RoomShape shape;
        private final Set<RoomAudit.Pos> solid = new HashSet<>();
        private final Map<String, List<RoomAudit.Pos>> markers = new LinkedHashMap<>();
        private int sizeY = HEIGHT;

        Builder(RoomShape shape) {
            this.shape = shape;
            for (var cell : shape.offsets()) {
                for (int lx = 0; lx < SIZE; lx++) {
                    for (int lz = 0; lz < SIZE; lz++) {
                        solid.add(new RoomAudit.Pos(cell.x() * SIZE + lx, 0, cell.y() * SIZE + lz));
                    }
                }
            }
        }

        Builder solidAt(int x, int y, int z) {
            solid.add(new RoomAudit.Pos(x, y, z));
            return this;
        }

        Builder holeAt(int x, int y, int z) {
            solid.remove(new RoomAudit.Pos(x, y, z));
            return this;
        }

        Builder marker(String kind, int x, int y, int z) {
            markers.computeIfAbsent(kind, k -> new ArrayList<>())
                    .add(new RoomAudit.Pos(x, y, z));
            return this;
        }

        Builder spawns(int count) {
            for (int i = 0; i < count; i++) {
                // Spread along the middle row, well clear of every apron.
                marker("spawn", SIZE / 2, 1, 6 + i);
            }
            return this;
        }

        Builder height(int y) {
            this.sizeY = y;
            return this;
        }

        List<RoomAudit.Finding> audit(String key) {
            return RoomAudit.audit(new RoomAudit.Room(shape, solid, markers, sizeY),
                    key, SIZE, HEIGHT, DOOR_W, DOOR_H, WAVE_MAX);
        }
    }

    private static boolean mentions(List<RoomAudit.Finding> findings, String fragment) {
        return findings.stream().anyMatch(f -> f.message().contains(fragment));
    }

    @Test
    void aWellFormedRoomHasNoFindings() {
        assertEquals(List.of(), good(RoomShape.SINGLE).spawns(WAVE_MAX).audit("normal"));
    }

    /** Nothing exists below a template, so a missing floor block is a hole out of the world. */
    @Test
    void aHoleInTheFloorIsAnError() {
        var findings = good(RoomShape.SINGLE).spawns(WAVE_MAX).holeAt(10, 0, 10).audit("normal");
        assertTrue(mentions(findings, "floor block"), findings.toString());
        assertEquals(RoomAudit.Level.ERROR, findings.get(0).level());
    }

    /**
     * The layout decides which walls get doors and the template cannot know, so a block in any
     * apron is a door that will open into a wall.
     */
    @Test
    void aBlockedApronIsAnError() {
        // The north apron: the door band is centred, and one block in from the wall.
        var findings = good(RoomShape.SINGLE).spawns(WAVE_MAX).solidAt(10, 1, 1).audit("normal");
        assertTrue(mentions(findings, "doorway apron"), findings.toString());
    }

    /**
     * The wall itself must stay solid — DoorCarver cuts the opening through it at build time — so a
     * room whose walls are built is not thereby failing the apron rule. This is the check's own
     * trap: counting the wall flags every correctly-built room in the game.
     */
    @Test
    void theWallItselfIsNotAnApronViolation() {
        var b = good(RoomShape.SINGLE).spawns(WAVE_MAX);
        for (int lx = 0; lx < SIZE; lx++) {
            for (int ly = 1; ly <= DOOR_H; ly++) {
                b.solidAt(lx, ly, 0).solidAt(lx, ly, SIZE - 1)
                        .solidAt(0, ly, lx).solidAt(SIZE - 1, ly, lx);
            }
        }
        assertEquals(List.of(), b.audit("normal"));
    }

    /** A spawn in an entrance puts an enemy inside the wall the moment the opening is carved. */
    @Test
    void aMarkerInsideADoorwayIsAnError() {
        var findings = good(RoomShape.SINGLE).spawns(WAVE_MAX)
                .marker("nido", 10, 1, 2).audit("normal");
        assertTrue(mentions(findings, "sits in a doorway"), findings.toString());
    }

    /**
     * An L's interior boundary faces a cell the room owns, so it is a neck rather than a doorway and
     * nothing there is reserved. Getting this backwards would condemn every L room ever authored.
     */
    @Test
    void anLsInteriorNeckIsNotADoorway() {
        // L_TOP_LEFT owns (0,0),(1,0),(0,1). The boundary between (0,0) and (1,0) is interior.
        var findings = good(RoomShape.L_TOP_LEFT).spawns(WAVE_MAX)
                .solidAt(SIZE - 2, 1, 10).audit("normal_l");
        assertFalse(mentions(findings, "doorway apron"), findings.toString());
    }

    /** The spawner cycles positions, so too few markers stacks the wave on one block. */
    @Test
    void tooFewSpawnMarkersWarns() {
        var findings = good(RoomShape.SINGLE).spawns(2).audit("normal");
        assertTrue(mentions(findings, "spawn marker"), findings.toString());
        assertEquals(RoomAudit.Level.WARNING, findings.get(0).level());
    }

    /** Enough markers is enough — the bar is the largest wave, not more. */
    @Test
    void exactlyEnoughSpawnMarkersIsClean() {
        assertEquals(List.of(), good(RoomShape.SINGLE).spawns(WAVE_MAX).audit("normal"));
    }

    /** A room with no spawns at all is a different complaint, and the required-marker one covers it. */
    @Test
    void noSpawnMarkersAtAllReportsTheMissingMarkerNotTheCount() {
        var findings = good(RoomShape.SINGLE).audit("normal");
        assertTrue(mentions(findings, "no 'spawn' marker"), findings.toString());
        assertFalse(mentions(findings, "the spawner cycles"), findings.toString());
    }

    /** The trapdoor moved to the exit room (sala del sello); the boss room owes only its stand. */
    @Test
    void aBossRoomWantsItsBoss() {
        var findings = good(RoomShape.SINGLE).audit("boss");
        assertTrue(mentions(findings, "no 'boss' marker"), findings.toString());
        assertFalse(mentions(findings, "no 'trapdoor' marker"), findings.toString());
    }

    /** Room keys carry the shape family, so the 2x2 boss chamber wants the same markers. */
    @Test
    void theQuadBossChamberWantsTheSameMarkers() {
        var findings = good(RoomShape.QUAD).marker("boss", 20, 1, 20).audit("boss_big");
        assertEquals(List.of(), findings);
    }

    /** La sala del sello owes the pit down and the boss reward stand. */
    @Test
    void anExitRoomWantsItsTrapdoorAndPremio() {
        var findings = good(RoomShape.SINGLE).audit("exit");
        assertTrue(mentions(findings, "no 'trapdoor' marker"), findings.toString());
        assertTrue(mentions(findings, "no 'premio' marker"), findings.toString());
    }

    /** A template shorter than the cell leaves the top layers as whatever the discard left. */
    @Test
    void aShortTemplateWarns() {
        var findings = good(RoomShape.SINGLE).spawns(WAVE_MAX).height(8).audit("normal");
        assertTrue(mentions(findings, "8 tall but the cell is 12"), findings.toString());
    }

    /** An L's empty quadrant is supposed to have no floor; the audit must not call it a hole. */
    @Test
    void anLsEmptyQuadrantIsNotAHole() {
        var findings = good(RoomShape.L_TOP_LEFT).spawns(WAVE_MAX).audit("normal_l");
        assertFalse(mentions(findings, "floor block"), findings.toString());
    }

    /** A marker outside every owned cell would be pasted into a neighbouring room. */
    @Test
    void aMarkerOutsideTheRoomIsAnError() {
        var findings = good(RoomShape.L_TOP_LEFT).spawns(WAVE_MAX)
                .marker("loot", SIZE + 10, 1, SIZE + 10).audit("normal_l");
        assertTrue(mentions(findings, "outside every cell"), findings.toString());
    }

    /**
     * The rule that {@code anfiteatro} shipped past. Every other check passed on that room — floor
     * solid, aprons clear, markers placed — and all four of its doors opened onto a wall of terrace
     * five blocks high. It was found by someone standing in the doorway, unable to get in.
     */
    @Test
    void aWallBuiltAgainstTheApronsCutsTheRoomInTwo() {
        Builder blocked = good(RoomShape.SINGLE);
        // A ring of raised rock just inside every apron: the doors open, and nothing can step up.
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                boolean ring = x == 6 || x == 14 || z == 6 || z == 14;
                if (ring) {
                    for (int y = 1; y <= 3; y++) {
                        blocked.solidAt(x, y, z);
                    }
                }
            }
        }
        assertTrue(mentions(blocked.spawns(WAVE_MAX).audit("normal"), "cut in two"),
                "a room whose doorways cannot reach each other has to be an error");
    }

    /** A room with nothing built in it is trivially crossable and must stay silent. */
    @Test
    void anOpenRoomIsWalkable() {
        assertFalse(mentions(good(RoomShape.SINGLE).spawns(WAVE_MAX).audit("normal"),
                "cut in two"));
    }

    /**
     * A step is a step, not a wall. Terrain that rises one block at a time has to pass, or the rule
     * would forbid every terrace and ledge in the game.
     */
    @Test
    void aStaircaseIsNotAWall() {
        Builder stepped = good(RoomShape.SINGLE);
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                int height = Math.min(3, Math.max(0, Math.min(x, z) - 1));
                for (int y = 1; y <= height; y++) {
                    stepped.solidAt(x, y, z);
                }
            }
        }
        assertFalse(mentions(stepped.spawns(WAVE_MAX).audit("normal"), "cut in two"));
    }

    /**
     * A mob may drop any distance, so a spawn on a raised rock is fine. Holding spawns to the
     * player's symmetric rule would condemn every ranged perch the piso design depends on.
     */
    @Test
    void aSpawnOnARaisedRockIsNotSealedOff() {
        Builder perch = good(RoomShape.SINGLE);
        for (int y = 1; y <= 3; y++) {
            perch.solidAt(4, y, 4);
        }
        assertFalse(mentions(perch.marker("spawn", 4, 4, 4).audit("normal"), "sealed off"),
                "it can step off the rock; it does not need to climb back on");
    }

    /** A spawn walled in on every side is scenery, and the wave that draws it is a body short. */
    @Test
    void aSpawnInASealedBoxIsReported() {
        Builder boxed = good(RoomShape.SINGLE);
        for (int y = 1; y <= 4; y++) {
            boxed.solidAt(3, y, 4);
            boxed.solidAt(5, y, 4);
            boxed.solidAt(4, y, 3);
            boxed.solidAt(4, y, 5);
        }
        assertTrue(mentions(boxed.marker("spawn", 4, 1, 4).audit("normal"), "sealed off"));
    }
}
