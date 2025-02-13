package es.boffmedia.teras.util.objects.karts;

import net.minecraft.util.math.vector.Vector3d;
import java.util.ArrayList;
import java.util.List;

public class SplineTrackPath {
    private final List<Vector3d> controlPoints;
    private final List<Double> segmentLengths;
    private final double totalLength;
    private static final int SEGMENTS_PER_SECTION = 10; // Resolution of the spline

    public SplineTrackPath(List<Checkpoint> checkpoints) {
        this.controlPoints = new ArrayList<>();

        // Convert checkpoints to control points
        for (Checkpoint checkpoint : checkpoints) {
            // Use checkpoint midpoints as control points
            Vector3d midpoint = new Vector3d(
                    (checkpoint.getStart().getX() + checkpoint.getEnd().getX()) / 2.0,
                    (checkpoint.getStart().getY() + checkpoint.getEnd().getY()) / 2.0,
                    (checkpoint.getStart().getZ() + checkpoint.getEnd().getZ()) / 2.0
            );
            controlPoints.add(midpoint);
        }

        // Add the first point again to close the circuit
        if (!controlPoints.isEmpty()) {
            controlPoints.add(controlPoints.get(0));
        }

        this.segmentLengths = calculateSegmentLengths();
        this.totalLength = segmentLengths.stream().mapToDouble(Double::doubleValue).sum();
    }

    // Calculate lengths of each spline segment for progress calculation
    private List<Double> calculateSegmentLengths() {
        List<Double> lengths = new ArrayList<>();
        if (controlPoints.size() < 2) return lengths;

        for (int i = 0; i < controlPoints.size() - 1; i++) {
            double segmentLength = 0;
            Vector3d prev = getSplinePoint(i, 0);

            for (int j = 1; j <= SEGMENTS_PER_SECTION; j++) {
                double t = j / (double) SEGMENTS_PER_SECTION;
                Vector3d current = getSplinePoint(i, t);
                segmentLength += current.subtract(prev).length();
                prev = current;
            }

            lengths.add(segmentLength);
        }

        return lengths;
    }

    // Get point on spline at segment i and interpolation parameter t
    public Vector3d getSplinePoint(int i, double t) {
        if (controlPoints.size() < 2) return new Vector3d(0, 0, 0);

        Vector3d p0 = controlPoints.get(i);
        Vector3d p1 = controlPoints.get((i + 1) % controlPoints.size());

        // Calculate tangent vectors
        Vector3d t0 = getTangent(i);
        Vector3d t1 = getTangent((i + 1) % controlPoints.size());

        // Hermite basis functions
        double h0 = 2*t*t*t - 3*t*t + 1;
        double h1 = -2*t*t*t + 3*t*t;
        double h2 = t*t*t - 2*t*t + t;
        double h3 = t*t*t - t*t;

        // Calculate interpolated point
        return new Vector3d(
                h0*p0.x + h1*p1.x + h2*t0.x + h3*t1.x,
                h0*p0.y + h1*p1.y + h2*t0.y + h3*t1.y,
                h0*p0.z + h1*p1.z + h2*t0.z + h3*t1.z
        );
    }

    // Calculate tangent vector at control point i
    private Vector3d getTangent(int i) {
        if (controlPoints.size() < 2) return new Vector3d(0, 0, 0);

        Vector3d prev = controlPoints.get((i - 1 + controlPoints.size()) % controlPoints.size());
        Vector3d next = controlPoints.get((i + 1) % controlPoints.size());

        return next.subtract(prev).scale(0.5);
    }

    // Find closest point on spline to given position
    public Vector3d findClosestPoint(Vector3d position) {
        Vector3d closestPoint = null;
        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < controlPoints.size() - 1; i++) {
            for (int j = 0; j <= SEGMENTS_PER_SECTION; j++) {
                double t = j / (double) SEGMENTS_PER_SECTION;
                Vector3d point = getSplinePoint(i, t);
                double distance = point.subtract(position).lengthSqr();

                if (distance < minDistance) {
                    minDistance = distance;
                    closestPoint = point;
                }
            }
        }

        return closestPoint != null ? closestPoint : new Vector3d(0, 0, 0);
    }

    // Calculate progress (0 to 1) along the track
    public double calculateProgress(Vector3d position) {
        Vector3d closestPoint = findClosestPoint(position);
        double distanceAlongSpline = 0;
        boolean foundPoint = false;

        for (int i = 0; i < controlPoints.size() - 1 && !foundPoint; i++) {
            for (int j = 0; j <= SEGMENTS_PER_SECTION; j++) {
                double t = j / (double) SEGMENTS_PER_SECTION;
                Vector3d point = getSplinePoint(i, t);

                if (point.subtract(closestPoint).lengthSqr() < 0.1) {
                    foundPoint = true;
                    break;
                }

                if (j > 0) {
                    Vector3d prevPoint = getSplinePoint(i, (j-1) / (double) SEGMENTS_PER_SECTION);
                    distanceAlongSpline += point.subtract(prevPoint).length();
                }
            }

            if (!foundPoint) {
                distanceAlongSpline += segmentLengths.get(i);
            }
        }

        return distanceAlongSpline / totalLength;
    }

    // Get total track length
    public double getTotalLength() {
        return totalLength;
    }

    // Get list of control points
    public List<Vector3d> getControlPoints() {
        return new ArrayList<>(controlPoints);
    }
}