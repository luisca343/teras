package es.boffmedia.teras.util.objects.karts;

public final class CoordinatePoint {
    private final double x, y, z;

    public CoordinatePoint(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    /**
     * Creates a new CoordinatePoint with the specified X coordinate
     */
    public CoordinatePoint withX(double newX) {
        return new CoordinatePoint(newX, this.y, this.z);
    }

    /**
     * Creates a new CoordinatePoint with the specified Y coordinate
     */
    public CoordinatePoint withY(double newY) {
        return new CoordinatePoint(this.x, newY, this.z);
    }

    /**
     * Creates a new CoordinatePoint with the specified Z coordinate
     */
    public CoordinatePoint withZ(double newZ) {
        return new CoordinatePoint(this.x, this.y, newZ);
    }

    /**
     * Calculates the squared distance to another point.
     * This is more efficient than distance() when you just need to compare distances.
     */
    public double distanceSquared(CoordinatePoint other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Calculates the exact distance to another point
     */
    public double distance(CoordinatePoint other) {
        return Math.sqrt(distanceSquared(other));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CoordinatePoint)) return false;

        CoordinatePoint other = (CoordinatePoint) obj;
        // Use epsilon comparison for floating point equality
        return Math.abs(x - other.x) < 0.000001
                && Math.abs(y - other.y) < 0.000001
                && Math.abs(z - other.z) < 0.000001;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + Double.hashCode(x);
        result = 31 * result + Double.hashCode(y);
        result = 31 * result + Double.hashCode(z);
        return result;
    }

    @Override
    public String toString() {
        return String.format("%.2f,%.2f,%.2f", x, y, z);
    }
}