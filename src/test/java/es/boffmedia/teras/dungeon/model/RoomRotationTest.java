package es.boffmedia.teras.dungeon.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The contract that lets one authored template serve every orientation of its family, checked
 * against the geometry the materializer actually performs rather than against the enum's own
 * comment.
 *
 * <p>Two halves have to agree or a room pastes crooked. {@code StructureTemplate} rotates about the
 * template origin, so a turned template lands partly in negative coordinates; the materializer
 * pins the rotated bounding box's minimum corner to the room's anchor cell. That only puts the
 * blocks over the right cells if turning the family's authored footprint the same way — and
 * re-pinning it the same way — reproduces the target shape's offsets exactly. Getting it wrong
 * looks like a generation bug (a room with its missing quadrant on the wrong side) rather than a
 * rotation one, which is why it is pinned here in the terms the paste is done in.</p>
 */
class RoomRotationTest {

    /** Every shape's offsets start at the origin, which is what the anchor means. */
    @Test
    void theAnchorIsAlwaysTheBoundingBoxMinimumCorner() {
        for (RoomShape shape : RoomShape.values()) {
            assertEquals(0, shape.offsets().stream().mapToInt(GridPos::x).min().orElseThrow(),
                    shape + " does not start at x=0");
            assertEquals(0, shape.offsets().stream().mapToInt(GridPos::y).min().orElseThrow(),
                    shape + " does not start at z=0");
        }
    }

    /**
     * L_BOTTOM_RIGHT is the one shape that does not own its anchor cell — its missing quadrant is
     * the top-left one, and the anchor has to stay the bounding box corner for the paste to land.
     * Pinned because the carver has to treat the anchor as a coordinate frame rather than as a cell
     * the room occupies; assuming otherwise is what made this shape four times rarer than its
     * siblings.
     */
    @Test
    void onlyLBottomRightDoesNotOwnItsAnchor() {
        for (RoomShape shape : RoomShape.values()) {
            boolean owned = shape.offsets().contains(new GridPos(0, 0));
            assertEquals(shape != RoomShape.L_BOTTOM_RIGHT, owned, shape.name());
        }
    }

    /**
     * Turning the family's authored footprint by the shape's own {@code baseRotation} must land on
     * the shape. This is the whole of "four L orientations, one L template".
     */
    @Test
    void everyShapeIsItsFamilysTemplateTurned() {
        for (RoomShape shape : RoomShape.values()) {
            Set<GridPos> turned = turn(authored(shape.family()).offsets(), shape.baseRotation());
            assertEquals(new HashSet<>(shape.offsets()), turned,
                    shape + " is not " + authored(shape.family()) + " turned "
                            + shape.baseRotation() + " degrees");
        }
    }

    /** Four quarter-turns are the identity, so no shape can be reached by two different rotations. */
    @Test
    void aFullTurnReturnsEveryShapeToItself() {
        for (RoomShape shape : RoomShape.values()) {
            assertEquals(new HashSet<>(shape.offsets()), turn(shape.offsets(), 360), shape.name());
        }
    }

    /** Every orientation of a family is reachable, and no two share a rotation. */
    @Test
    void aFamilysRotationsAreDistinctAndCoverIt() {
        for (ShapeFamily family : ShapeFamily.values()) {
            Set<Integer> rotations = new HashSet<>();
            for (RoomShape shape : family.shapes()) {
                assertTrue(rotations.add(shape.baseRotation()),
                        family + " has two shapes at " + shape.baseRotation() + " degrees");
                assertTrue(List.of(0, 90, 180, 270).contains(shape.baseRotation()),
                        shape + " has a rotation that is not a quarter turn");
            }
        }
    }

    /** The one shape of a family that is authored as-is — the template on disk. */
    private static RoomShape authored(ShapeFamily family) {
        for (RoomShape shape : family.shapes()) {
            if (shape.baseRotation() == 0) {
                return shape;
            }
        }
        throw new AssertionError(family + " has no unrotated shape to author");
    }

    /**
     * A footprint turned clockwise, re-pinned so its minimum corner is the origin — the cell-scale
     * equivalent of what the materializer does in blocks. A quarter turn maps a cell {@code (x,z)}
     * of a {@code w×d} footprint to {@code (d−1−z, x)}.
     */
    private static Set<GridPos> turn(List<GridPos> offsets, int degrees) {
        Set<GridPos> current = new HashSet<>(offsets);
        for (int quarters = (degrees / 90) % 4; quarters > 0; quarters--) {
            int depth = current.stream().mapToInt(GridPos::y).max().orElseThrow() + 1;
            Set<GridPos> next = new HashSet<>();
            for (GridPos cell : current) {
                next.add(new GridPos(depth - 1 - cell.y(), cell.x()));
            }
            current = next;
        }
        return current;
    }
}
