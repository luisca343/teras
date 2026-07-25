package es.boffmedia.teras.dungeon.gen;

import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.DungeonLayout;
import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.RoomShape;
import es.boffmedia.teras.dungeon.model.ShapeFamily;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That a floor tiles the world cell by cell: every block a room's template paste writes lands in a
 * cell that room owns, and no two rooms ever write into the same cell.
 *
 * <p>This works at block resolution, from {@code StructureTemplate.transform}'s own formula, rather
 * than from {@link RoomShape}'s offsets. That is the point — checking the offsets against the
 * offsets is a tautology, and the thing that can actually be wrong is the step between them: a
 * template is authored in its family's base orientation and rotated about the template origin, which
 * throws the result into negative coordinates, and the materializer pins the rotated bounding box's
 * minimum corner back onto the room's anchor cell. If that pin is off by a cell, a 2×1 lands half
 * inside its neighbour and the floor looks miscarved rather than mispasted.</p>
 *
 * <p>{@code ROOM_SIZE} is deliberately not the configured one: the geometry must hold for any cell
 * pitch, and a test that shares the constant with the code cannot see a pitch-dependent error.</p>
 */
class RoomPlacementCellTest {

    private static final int ROOM_SIZE = 21;
    private static final GenConfig CONFIG = GenConfig.defaults();

    /**
     * The world cells a room's paste writes into, derived the way the materializer derives them.
     *
     * <p>Mirrors {@code DungeonMaterializer.placeRoom}: take the template authored for the shape's
     * family, transform every block by the shape's rotation about the origin, shift so the rotated
     * bounding box's minimum corner sits on the anchor cell, and see which cells the blocks land in.
     */
    private static Set<GridPos> pastedCells(Room room) {
        RoomShape authored = authored(room.shape().family());
        int sizeX = (max(authored, GridPos::x) + 1) * ROOM_SIZE;
        int sizeZ = (max(authored, GridPos::y) + 1) * ROOM_SIZE;
        int rotation = room.shape().baseRotation();

        // The pin: getBoundingBox transforms the template's two extreme corners, so the shift is
        // whatever it takes to bring the smaller of them back to zero.
        int[] cornerA = transform(0, 0, rotation);
        int[] cornerB = transform(sizeX - 1, sizeZ - 1, rotation);
        int shiftX = -Math.min(cornerA[0], cornerB[0]);
        int shiftZ = -Math.min(cornerA[1], cornerB[1]);

        Set<GridPos> cells = new HashSet<>();
        for (GridPos cell : authored.offsets()) {
            // Every block the template actually holds — the authored L omits its empty quadrant,
            // so those coordinates are never visited and never claim a cell.
            for (int x = cell.x() * ROOM_SIZE; x < (cell.x() + 1) * ROOM_SIZE; x++) {
                for (int z = cell.y() * ROOM_SIZE; z < (cell.y() + 1) * ROOM_SIZE; z++) {
                    int[] turned = transform(x, z, rotation);
                    int worldX = turned[0] + shiftX + room.anchor().x() * ROOM_SIZE;
                    int worldZ = turned[1] + shiftZ + room.anchor().y() * ROOM_SIZE;
                    cells.add(new GridPos(Math.floorDiv(worldX, ROOM_SIZE),
                            Math.floorDiv(worldZ, ROOM_SIZE)));
                }
            }
        }
        return cells;
    }

    /** {@code StructureTemplate.transform} with a zero pivot, in the horizontal plane. */
    private static int[] transform(int x, int z, int degrees) {
        return switch (((degrees % 360) + 360) % 360) {
            case 90 -> new int[] {-z, x};
            case 180 -> new int[] {-x, -z};
            case 270 -> new int[] {z, -x};
            default -> new int[] {x, z};
        };
    }

    private static RoomShape authored(ShapeFamily family) {
        for (RoomShape shape : family.shapes()) {
            if (shape.baseRotation() == 0) {
                return shape;
            }
        }
        throw new AssertionError(family + " has no unrotated shape to author");
    }

    private static int max(RoomShape shape, java.util.function.ToIntFunction<GridPos> axis) {
        return shape.offsets().stream().mapToInt(axis).max().orElseThrow();
    }

    /** Every shape, at every anchor, pastes over exactly the cells the grid says it owns. */
    @Test
    void everyShapePastesOverExactlyTheCellsItOwns() {
        for (RoomShape shape : RoomShape.values()) {
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    Room room = new Room(es.boffmedia.teras.dungeon.model.RoomType.NORMAL,
                            new GridPos(x, z), shape);
                    assertEquals(new HashSet<>(room.cells()), pastedCells(room),
                            shape + " at " + x + "," + z + " does not paste over its own cells");
                }
            }
        }
    }

    /**
     * The whole-floor version, over generated layouts: no cell is written by two rooms, and every
     * cell the grid hands a room is one the paste actually fills. A room that pastes short leaves a
     * hole in the floor to fall through; one that pastes long overwrites its neighbour.
     */
    @Test
    void generatedFloorsTileTheWorldWithoutOverlapOrGap() {
        for (int stage : new int[] {1, 3, 6, 12}) {
            for (int i = 0; i < 120; i++) {
                DungeonLayout layout = DungeonGenerator.generate(CONFIG,
                        FloorDepth.of(CONFIG, stage), Set.<Curse>of(),
                        EnumSet.allOf(RoomShape.class), "tile-" + stage + "-" + i);
                assertTiles(layout, "stage " + stage + " seed " + layout.seedString());
            }
        }
    }

    private static void assertTiles(DungeonLayout layout, String context) {
        Map<GridPos, Room> written = new HashMap<>();
        for (Room room : layout.rooms()) {
            Set<GridPos> pasted = pastedCells(room);
            assertEquals(new HashSet<>(room.cells()), pasted,
                    context + ": " + room + " pastes over " + pasted + " but owns " + room.cells());
            for (GridPos cell : pasted) {
                Room other = written.put(cell, room);
                assertNull(other, context + ": " + room + " and " + other + " both write " + cell);
                assertEquals(room, layout.grid().roomAt(cell),
                        context + ": " + room + " writes " + cell + ", which the grid gives to "
                                + layout.grid().roomAt(cell));
            }
        }
        // The converse: every occupied cell of the grid is written by exactly one room, so no room
        // leaves part of its own footprint as void.
        for (int y = 0; y < layout.grid().size(); y++) {
            for (int x = 0; x < layout.grid().size(); x++) {
                GridPos cell = new GridPos(x, y);
                Room room = layout.grid().roomAt(cell);
                assertTrue(room == null || written.get(cell) == room,
                        context + ": " + cell + " belongs to " + room + " but nothing pastes there");
            }
        }
    }
}
