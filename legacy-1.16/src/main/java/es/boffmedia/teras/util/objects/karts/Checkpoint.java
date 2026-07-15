package es.boffmedia.teras.util.objects.karts;

public class Checkpoint {
    private final CoordinatePoint start;
    private final CoordinatePoint end;
    private final double padding = 1.0; // Configurable padding

    public Checkpoint(CoordinatePoint start, CoordinatePoint end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Checkpoint points cannot be null");
        }
        if (start.equals(end)) {
            throw new IllegalArgumentException("Checkpoint start and end points cannot be the same");
        }
        // Create defensive copies to ensure immutability
        this.start = new CoordinatePoint(start.getX(), start.getY(), start.getZ());
        this.end = new CoordinatePoint(end.getX(), end.getY(), end.getZ());
    }

    public boolean isInCheckpoint(CoordinatePoint loc) {
        if (loc == null) {
            return false;
        }

        double playerX = loc.getX();
        double playerY = loc.getY();
        double playerZ = loc.getZ();

        // Calculate bounds with padding
        double minX = Math.min(start.getX(), end.getX()) - padding;
        double maxX = Math.max(start.getX(), end.getX()) + padding;
        double minY = Math.min(start.getY(), end.getY()) - padding;
        double maxY = Math.max(start.getY(), end.getY()) + padding;
        double minZ = Math.min(start.getZ(), end.getZ()) - padding;
        double maxZ = Math.max(start.getZ(), end.getZ()) + padding;

        return playerX >= minX && playerX <= maxX &&
                playerY >= minY && playerY <= maxY &&
                playerZ >= minZ && playerZ <= maxZ;
    }

    // Return defensive copies to prevent modification
    public CoordinatePoint getStart() {
        return new CoordinatePoint(start.getX(), start.getY(), start.getZ());
    }

    public CoordinatePoint getEnd() {
        return new CoordinatePoint(end.getX(), end.getY(), end.getZ());
    }

    // Calculate checkpoint dimensions
    public double getWidth() {
        return Math.abs(end.getX() - start.getX());
    }

    public double getHeight() {
        return Math.abs(end.getY() - start.getY());
    }

    public double getLength() {
        return Math.abs(end.getZ() - start.getZ());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Checkpoint that = (Checkpoint) obj;
        return start.equals(that.start) && end.equals(that.end);
    }

    @Override
    public int hashCode() {
        return 31 * start.hashCode() + end.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Checkpoint[start=%s, end=%s, dimensions=%.1fx%.1fx%.1f]",
                start, end, getWidth(), getHeight(), getLength());
    }
}