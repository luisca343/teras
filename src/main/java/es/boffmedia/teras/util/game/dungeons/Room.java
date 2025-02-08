package es.boffmedia.teras.util.game.dungeons;

public class Room {
    private RoomType type;
    private int x;
    private int y;
    private int width;
    private int height;
    private Integer parentX;
    private Integer parentY;

    public Room(RoomType type, int x, int y, int width, int height) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public Room(RoomType type, int x, int y, int width, int height, Integer parentX, Integer parentY) {
        this(type, x, y, width, height);
        this.parentX = parentX;
        this.parentY = parentY;
    }

    // Getters and setters
    public RoomType getType() { return type; }
    public void setType(RoomType type) { this.type = type; }
    public int getX() { return x; }
    public void setX(int x) { this.x = x; }
    public int getY() { return y; }
    public void setY(int y) { this.y = y; }
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    public Integer getParentX() { return parentX; }
    public void setParentX(Integer parentX) { this.parentX = parentX; }
    public Integer getParentY() { return parentY; }
    public void setParentY(Integer parentY) { this.parentY = parentY; }
}

