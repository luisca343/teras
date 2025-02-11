package es.boffmedia.teras.util.game.dungeons;

public class Room {
    private RoomType type;
    private int x;
    private int y;
    private RoomShape shape;
    private Integer parentX;
    private Integer parentY;

    public Room(RoomType type, int x, int y, RoomShape shape) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.shape = shape;
    }

    public Room(RoomType type, int x, int y, RoomShape shape, Integer parentX, Integer parentY) {
        this(type, x, y, shape);
        this.parentX = parentX;
        this.parentY = parentY;
    }

    // For backward compatibility
    public Room(RoomType type, int x, int y, int width, int height) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.shape = determineShape(width, height);
    }

    // For backward compatibility
    public Room(RoomType type, int x, int y, int width, int height, Integer parentX, Integer parentY) {
        this(type, x, y, width, height);
        this.parentX = parentX;
        this.parentY = parentY;
    }

    private RoomShape determineShape(int width, int height) {
        if (width == 1 && height == 1) return RoomShape.SINGLE;
        if (width == 2 && height == 1) return RoomShape.HORIZONTAL;
        if (width == 1 && height == 2) return RoomShape.VERTICAL;
        if (width == 2 && height == 2) return RoomShape.QUAD;
        throw new IllegalArgumentException("Invalid room dimensions: " + width + "x" + height);
    }

    // Getters and setters
    public RoomType getType() { return type; }
    public void setType(RoomType type) { this.type = type; }
    public int getX() { return x; }
    public void setX(int x) { this.x = x; }
    public int getY() { return y; }
    public void setY(int y) { this.y = y; }
    public RoomShape getShape() { return shape; }
    public void setShape(RoomShape shape) { this.shape = shape; }
    public Integer getParentX() { return parentX; }
    public void setParentX(Integer parentX) { this.parentX = parentX; }
    public Integer getParentY() { return parentY; }
    public void setParentY(Integer parentY) { this.parentY = parentY; }

    // Convenience methods
    public int getWidth() { return shape.getWidth(); }
    public int getHeight() { return shape.getHeight(); }
    public boolean isLarge() { return shape.isLarge(); }
    public boolean isLShaped() { return shape.isLShaped(); }
}