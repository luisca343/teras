package es.boffmedia.teras.karts.model;

/**
 * One gate on a track: an axis-aligned box a kart must pass through, in order, to complete a lap.
 *
 * <p>Built from two opposite corners (an admin's WorldEdit selection) and grown by
 * {@link #DEFAULT_PADDING} on every axis, so a gate drawn tightly around the road still catches a
 * kart riding its edge.</p>
 *
 * <p><b>Why {@link #crossed} and not just {@link #contains}.</b> The 1.16.5 version tested
 * containment of the kart's current position once per tick. A kart at racing speed moves several
 * blocks per tick, so it could be outside the gate on one tick and past it on the next — the lap
 * silently never counted, and the racer was stuck on a checkpoint they had visibly driven through.
 * {@code crossed} tests the <i>segment</i> between the previous and current position against the
 * box, so a gate is registered no matter how fast the kart is travelling.</p>
 */
public record TrackCheckpoint(TrackPoint cornerA, TrackPoint cornerB, double padding) {

    public static final double DEFAULT_PADDING = 1.0;

    public TrackCheckpoint(TrackPoint cornerA, TrackPoint cornerB) {
        this(cornerA, cornerB, DEFAULT_PADDING);
    }

    public TrackCheckpoint {
        if (cornerA == null || cornerB == null) {
            throw new IllegalArgumentException("Un checkpoint necesita dos esquinas");
        }
        if (padding < 0) {
            padding = DEFAULT_PADDING;
        }
    }

    public double minX() {
        return Math.min(cornerA.x(), cornerB.x()) - padding;
    }

    public double maxX() {
        return Math.max(cornerA.x(), cornerB.x()) + padding;
    }

    public double minY() {
        return Math.min(cornerA.y(), cornerB.y()) - padding;
    }

    public double maxY() {
        return Math.max(cornerA.y(), cornerB.y()) + padding;
    }

    public double minZ() {
        return Math.min(cornerA.z(), cornerB.z()) - padding;
    }

    public double maxZ() {
        return Math.max(cornerA.z(), cornerB.z()) + padding;
    }

    /** The centre of the gate — the spline's control point for this section of track. */
    public TrackPoint midpoint() {
        return TrackPoint.at(
                (cornerA.x() + cornerB.x()) / 2.0,
                (cornerA.y() + cornerB.y()) / 2.0,
                (cornerA.z() + cornerB.z()) / 2.0);
    }

    public boolean contains(TrackPoint point) {
        return point != null
                && point.x() >= minX() && point.x() <= maxX()
                && point.y() >= minY() && point.y() <= maxY()
                && point.z() >= minZ() && point.z() <= maxZ();
    }

    /**
     * Whether a kart moving from {@code previous} to {@code current} passed through this gate,
     * including the case where it is sitting inside it.
     *
     * <p>A null {@code previous} (the kart's first sample) falls back to a containment test: there
     * is no travel to intersect yet.</p>
     */
    public boolean crossed(TrackPoint previous, TrackPoint current) {
        if (contains(current)) {
            return true;
        }
        if (previous == null || contains(previous)) {
            return previous != null;
        }
        return segmentIntersects(previous, current);
    }

    /**
     * Slab method: clip the segment against each pair of parallel box faces in turn and see whether
     * any of it survives.
     */
    private boolean segmentIntersects(TrackPoint from, TrackPoint to) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();

        double enter = 0.0;
        double exit = 1.0;

        double[] origins = {from.x(), from.y(), from.z()};
        double[] deltas = {dx, dy, dz};
        double[] mins = {minX(), minY(), minZ()};
        double[] maxs = {maxX(), maxY(), maxZ()};

        for (int axis = 0; axis < 3; axis++) {
            double delta = deltas[axis];
            double origin = origins[axis];
            if (Math.abs(delta) < 1.0e-9) {
                // Travelling parallel to this pair of faces: it can only intersect if it already
                // lies between them.
                if (origin < mins[axis] || origin > maxs[axis]) {
                    return false;
                }
                continue;
            }
            double t1 = (mins[axis] - origin) / delta;
            double t2 = (maxs[axis] - origin) / delta;
            if (t1 > t2) {
                double swap = t1;
                t1 = t2;
                t2 = swap;
            }
            enter = Math.max(enter, t1);
            exit = Math.min(exit, t2);
            if (enter > exit) {
                return false;
            }
        }
        return true;
    }
}
