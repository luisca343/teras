package es.boffmedia.teras.util.game.dungeons;

public enum RoomShape {
    SINGLE(1, 1),
    HORIZONTAL(2, 1),
    VERTICAL(1, 2),
    QUAD(2, 2),
    L_SHAPE_TOP_LEFT(2, 2),
    L_SHAPE_TOP_RIGHT(2, 2),
    L_SHAPE_BOTTOM_LEFT(2, 2),
    L_SHAPE_BOTTOM_RIGHT(2, 2);

    private final int width;
    private final int height;

    RoomShape(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isLShaped() {
        return this == L_SHAPE_TOP_LEFT || this == L_SHAPE_TOP_RIGHT ||
                this == L_SHAPE_BOTTOM_LEFT || this == L_SHAPE_BOTTOM_RIGHT;
    }

    public boolean isLarge() {
        return width > 1 || height > 1;
    }
}