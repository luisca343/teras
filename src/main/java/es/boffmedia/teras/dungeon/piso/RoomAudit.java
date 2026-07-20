package es.boffmedia.teras.dungeon.piso;

import es.boffmedia.teras.dungeon.model.DoorwayZone;
import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.RoomShape;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The rules a finished room obeys, checked mechanically.
 *
 * <p>DUNGEONS_PISOS.md §21 states them, and until now nothing enforced them: an apron built shut,
 * a spawn marker dropped in a doorway or a room with fewer spawn points than the wave it will be
 * asked to hold all look fine on the editor pad and only surface mid-run, as an enemy inside a wall
 * or a door that will not open. The author is the last person able to see any of it, so the check
 * belongs at save and audit time rather than in a playtest report.</p>
 *
 * <p>No Minecraft here on purpose. A room reduces to a set of occupied local positions and a list of
 * markers, and everything §21 asks is a question about those — so the rules can be held against
 * fixtures in a unit test rather than only against whatever happens to be on disk.</p>
 */
public final class RoomAudit {
    private RoomAudit() {}

    /** How bad a finding is: a room still builds with warnings, and is broken with errors. */
    public enum Level { ERROR, WARNING }

    public record Finding(Level level, String message) {}

    /**
     * What a room looks like to the audit.
     *
     * @param shape    the footprint the key is authored at
     * @param solid    every local position holding something that is not air or structure void
     * @param markers  marker tag to the local positions carrying it, e.g. {@code spawn} → …
     * @param sizeY    the template's height, to catch a room shorter than the configured cell
     */
    public record Room(RoomShape shape,
                       Set<Pos> solid,
                       Map<String, List<Pos>> markers,
                       int sizeY) {}

    /** A block position local to the template's minimum corner. */
    public record Pos(int x, int y, int z) {}

    /**
     * Every finding for one room. {@code waveMax} is the largest wave the floor may ask it to hold —
     * the spawner cycles marker positions, so fewer markers than that stacks enemies on one block.
     */
    public static List<Finding> audit(Room room, String roomKey, int roomSize, int roomHeight,
                                      int doorWidth, int doorHeight, int waveMax) {
        List<Finding> findings = new ArrayList<>();
        List<GridPos> cells = room.shape().offsets();

        checkHeight(room, roomHeight, findings);
        checkFloor(room, roomSize, cells, findings);
        checkAprons(room, roomSize, doorWidth, doorHeight, cells, findings);
        checkMarkersOutOfDoorways(room, roomSize, doorWidth, doorHeight, cells, findings);
        checkRequiredMarkers(room, roomKey, findings);
        checkSpawnCount(room, roomKey, waveMax, findings);
        return findings;
    }

    /**
     * A floor with a hole in it is a hole into the void — nothing exists below a template, so a
     * player who steps in leaves the world. Only cells the shape owns are checked; an L's empty
     * quadrant is supposed to have no floor.
     */
    private static void checkFloor(Room room, int roomSize, List<GridPos> cells,
                                   List<Finding> findings) {
        int holes = 0;
        Pos first = null;
        for (GridPos cell : cells) {
            for (int lx = 0; lx < roomSize; lx++) {
                for (int lz = 0; lz < roomSize; lz++) {
                    Pos pos = new Pos(cell.x() * roomSize + lx, 0, cell.y() * roomSize + lz);
                    if (!room.solid().contains(pos)) {
                        holes++;
                        if (first == null) {
                            first = pos;
                        }
                    }
                }
            }
        }
        if (holes > 0) {
            findings.add(new Finding(Level.ERROR, holes + " floor block(s) missing at y=0 — a hole "
                    + "in the floor drops a player out of the world (first at " + describe(first)
                    + ")"));
        }
    }

    /**
     * The band a doorway is cut through must be clear inward. The outermost layer is the wall and is
     * meant to be solid — {@code DoorCarver} cuts the opening through it at build time — so the
     * check starts one block in. Anything solid there is a door built shut.
     */
    private static void checkAprons(Room room, int roomSize, int doorWidth, int doorHeight,
                                    List<GridPos> cells, List<Finding> findings) {
        int blocked = 0;
        Pos first = null;
        for (GridPos cell : cells) {
            for (int lx = 0; lx < roomSize; lx++) {
                for (int lz = 0; lz < roomSize; lz++) {
                    // The wall itself is exempt; everything deeper in the reserved volume is not.
                    if (isWall(lx, lz, roomSize)) {
                        continue;
                    }
                    for (int ly = 1; ly <= doorHeight; ly++) {
                        if (!DoorwayZone.contains(cell, room.shape(), lx, ly, lz,
                                roomSize, doorWidth, doorHeight)) {
                            continue;
                        }
                        Pos pos = new Pos(cell.x() * roomSize + lx, ly, cell.y() * roomSize + lz);
                        if (room.solid().contains(pos)) {
                            blocked++;
                            if (first == null) {
                                first = pos;
                            }
                        }
                    }
                }
            }
        }
        if (blocked > 0) {
            findings.add(new Finding(Level.ERROR, blocked + " block(s) inside a doorway apron — the "
                    + "layout may put a door on any wall, and this one would open into a wall "
                    + "(first at " + describe(first) + ")"));
        }
    }

    private static boolean isWall(int lx, int lz, int roomSize) {
        return lx == 0 || lz == 0 || lx == roomSize - 1 || lz == roomSize - 1;
    }

    /**
     * A marker in a reserved volume is at best in the way: a nest blocks the door it stands in, and
     * a spawn puts an enemy inside the wall the moment the opening is carved.
     */
    private static void checkMarkersOutOfDoorways(Room room, int roomSize, int doorWidth,
                                                  int doorHeight, List<GridPos> cells,
                                                  List<Finding> findings) {
        for (Map.Entry<String, List<Pos>> entry : room.markers().entrySet()) {
            for (Pos pos : entry.getValue()) {
                GridPos cell = new GridPos(Math.floorDiv(pos.x(), roomSize),
                        Math.floorDiv(pos.z(), roomSize));
                if (!cells.contains(cell)) {
                    findings.add(new Finding(Level.ERROR, "marker '" + entry.getKey() + "' at "
                            + describe(pos) + " is outside every cell the room owns"));
                    continue;
                }
                if (DoorwayZone.contains(cell, room.shape(),
                        Math.floorMod(pos.x(), roomSize), pos.y(), Math.floorMod(pos.z(), roomSize),
                        roomSize, doorWidth, doorHeight)) {
                    findings.add(new Finding(Level.ERROR, "marker '" + entry.getKey() + "' at "
                            + describe(pos) + " sits in a doorway — move it a few blocks inside"));
                }
            }
        }
    }

    private static void checkRequiredMarkers(Room room, String roomKey, List<Finding> findings) {
        for (String required : RoomKeys.requiredMarkers(roomKey)) {
            if (!room.markers().containsKey(required)) {
                findings.add(new Finding(Level.WARNING, "no '" + required + "' marker — a '"
                        + roomKey + "' room wants one, or a calculated position is used instead"));
            }
        }
    }

    /**
     * The spawner cycles marker positions, so a wave larger than the marker count stacks enemies on
     * one block. A warning rather than an error: it is ugly, not broken.
     */
    private static void checkSpawnCount(Room room, String roomKey, int waveMax,
                                        List<Finding> findings) {
        if (!RoomKeys.requiredMarkers(roomKey).contains("spawn")) {
            return;
        }
        int spawns = room.markers().getOrDefault("spawn", List.of()).size();
        if (spawns > 0 && spawns < waveMax) {
            findings.add(new Finding(Level.WARNING, spawns + " spawn marker(s) for waves of up to "
                    + waveMax + " — the spawner cycles positions, so the extra enemies stack on "
                    + "blocks already used"));
        }
    }

    private static void checkHeight(Room room, int roomHeight, List<Finding> findings) {
        if (room.sizeY() < roomHeight) {
            findings.add(new Finding(Level.WARNING, "the room is " + room.sizeY() + " tall but the "
                    + "cell is " + roomHeight + " — the top " + (roomHeight - room.sizeY())
                    + " layer(s) build as whatever the discard left behind"));
        }
    }

    private static String describe(Pos pos) {
        return pos == null ? "?" : pos.x() + "," + pos.y() + "," + pos.z();
    }

    /** Groups findings by level so a caller can report errors and warnings separately. */
    public static Map<Level, List<Finding>> byLevel(List<Finding> findings) {
        Map<Level, List<Finding>> grouped = new LinkedHashMap<>();
        for (Finding finding : findings) {
            grouped.computeIfAbsent(finding.level(), k -> new ArrayList<>()).add(finding);
        }
        return grouped;
    }
}
