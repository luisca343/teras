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

    /** Cell offsets relative to the room's anchor (its top-left-most claimed cell). */
    public List<GridPos> offsets() {
        return offsets;
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
