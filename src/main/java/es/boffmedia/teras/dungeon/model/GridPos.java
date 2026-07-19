package es.boffmedia.teras.dungeon.model;

/** A cell on the floor grid. {@code x} grows east, {@code y} grows south. */
public record GridPos(int x, int y) {

    public GridPos offset(int dx, int dy) {
        return new GridPos(x + dx, y + dy);
    }

    public GridPos step(GridDir dir) {
        return new GridPos(x + dir.dx(), y + dir.dy());
    }
}
