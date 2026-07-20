package es.boffmedia.teras.dungeon.model;

import java.util.List;

/**
 * Room footprints as explicit cell-offset lists. The legacy code held width×height pairs plus a
 * separate {@code boolean[2][2]} layout switch for L-shapes, and every consumer special-cased the
 * two representations; one offset list serves the carver, the validator and the materializer alike.
 *
 * <p>{@code L_BOTTOM_RIGHT} is back: the legacy carver disabled it because the schematic paster
 * could not rotate its one L schematic into that orientation without hand-tuned origin offsets.
 * Native {@code StructureTemplate} rotation has no such problem.</p>
 *
 * <p>Eight shapes, but only <b>four things to build</b>. A vertical 2×1 is a horizontal one turned
 * ninety degrees and the four L orientations are one L turned four ways, so templates are authored
 * per {@link ShapeFamily} and rotated into place by {@link #baseRotation()}. Authoring them
 * separately produced four L rooms that had to be kept looking like each other by hand, which is
 * four times the work for none of the variety.</p>
 */
public enum RoomShape {
    SINGLE(new int[][] {{0, 0}}),
    HORIZONTAL(new int[][] {{0, 0}, {1, 0}}),
    VERTICAL(new int[][] {{0, 0}, {0, 1}}),
    QUAD(new int[][] {{0, 0}, {1, 0}, {0, 1}, {1, 1}}),
    L_TOP_LEFT(new int[][] {{0, 0}, {1, 0}, {0, 1}}),
    L_TOP_RIGHT(new int[][] {{0, 0}, {1, 0}, {1, 1}}),
    L_BOTTOM_LEFT(new int[][] {{0, 0}, {0, 1}, {1, 1}}),
    L_BOTTOM_RIGHT(new int[][] {{1, 0}, {0, 1}, {1, 1}});

    private final List<GridPos> offsets;

    RoomShape(int[][] offsets) {
        this.offsets = List.of(java.util.Arrays.stream(offsets)
                .map(o -> new GridPos(o[0], o[1]))
                .toArray(GridPos[]::new));
    }

    /**
     * Which template this shape is built from. Several shapes share one: the family is what gets
     * authored, the shape is what the layout asked for.
     */
    public ShapeFamily family() {
        return switch (this) {
            case SINGLE -> ShapeFamily.SINGLE;
            case HORIZONTAL, VERTICAL -> ShapeFamily.LARGE;
            case L_TOP_LEFT, L_TOP_RIGHT, L_BOTTOM_LEFT, L_BOTTOM_RIGHT -> ShapeFamily.L;
            case QUAD -> ShapeFamily.BIG;
        };
    }

    /**
     * Degrees clockwise to turn the family's template so it lands in this orientation.
     *
     * <p>The L cycle follows from the offsets: rotating a 2×2 block ninety degrees clockwise maps
     * a cell {@code (x,z)} to {@code (1-z,x)}, which walks the missing quadrant
     * TOP_LEFT → TOP_RIGHT → BOTTOM_RIGHT → BOTTOM_LEFT. The authored L is TOP_LEFT.</p>
     *
     * <p>A 2×1 turned ninety degrees is a 1×2, and the materializer pins a rotated template's
     * minimum corner to the room's anchor cell, so the footprint lands correctly without any
     * per-orientation offset — the exact thing the legacy paster could not do.</p>
     */
    public int baseRotation() {
        return switch (this) {
            case SINGLE, QUAD, HORIZONTAL, L_TOP_LEFT -> 0;
            case VERTICAL, L_TOP_RIGHT -> 90;
            case L_BOTTOM_RIGHT -> 180;
            case L_BOTTOM_LEFT -> 270;
        };
    }

    /** Cell offsets relative to the room's anchor (its bounding box's minimum corner). */
    public List<GridPos> offsets() {
        return offsets;
    }

    /**
     * Cells spanned west to east. With {@link #cellsDeep()} this is the footprint a template of this
     * shape must measure once rotated — the check that keeps a room inside its own cells.
     */
    public int cellsWide() {
        return offsets.stream().mapToInt(GridPos::x).max().orElse(0) + 1;
    }

    /** Cells spanned north to south. */
    public int cellsDeep() {
        return offsets.stream().mapToInt(GridPos::y).max().orElse(0) + 1;
    }

    public int cellCount() {
        return offsets.size();
    }

    public boolean isLarge() {
        return offsets.size() > 1;
    }

    public boolean isLShaped() {
        return this == L_TOP_LEFT || this == L_TOP_RIGHT
                || this == L_BOTTOM_LEFT || this == L_BOTTOM_RIGHT;
    }
}
