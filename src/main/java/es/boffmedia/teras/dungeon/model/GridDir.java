package es.boffmedia.teras.dungeon.model;

/**
 * The four grid directions, in the pure core's own terms so the model never imports Minecraft's
 * {@code Direction}. The materializer maps these onto world directions when it carves doorways.
 */
public enum GridDir {
    NORTH(0, -1),
    SOUTH(0, 1),
    EAST(1, 0),
    WEST(-1, 0);

    private final int dx;
    private final int dy;

    GridDir(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    public GridDir opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
        };
    }
}
