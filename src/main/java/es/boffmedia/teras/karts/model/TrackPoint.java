package es.boffmedia.teras.karts.model;

/**
 * A position on a track, with the heading a kart should face when placed there.
 *
 * <p>Used both for grid slots (where {@code yaw} is the direction cars line up in) and as a plain
 * position for checkpoint tests and kart samples (where {@code yaw} is ignored — see
 * {@link #at(double, double, double)}).</p>
 *
 * <p>Replaces the 1.16.5 pairing of {@code CoordinatePoint} with a track-wide
 * {@code StartingDirection} enum: a per-slot yaw lets a grid follow a curved start line, and a
 * staggered grid is the normal shape for a race start.</p>
 */
public record TrackPoint(double x, double y, double z, float yaw) {

    /** A position with no meaningful heading. */
    public static TrackPoint at(double x, double y, double z) {
        return new TrackPoint(x, y, z, 0f);
    }

    public double distanceTo(TrackPoint other) {
        return Math.sqrt(distanceSquaredTo(other));
    }

    public double distanceSquaredTo(TrackPoint other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
