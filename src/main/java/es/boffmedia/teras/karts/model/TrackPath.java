package es.boffmedia.teras.karts.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A closed Hermite spline through the checkpoint midpoints, used to rank karts that are on the same
 * lap: whoever is further along the path is ahead.
 *
 * <p>Ported from the 1.16.5 {@code SplineTrackPath}, with its progress lookup rebuilt. That version
 * sampled the whole spline to find the nearest point, then <i>sampled it a second time</i> trying to
 * recognise that point by distance ({@code lengthSqr < 0.1}) in order to accumulate the distance to
 * it — quadratic work on every query, and when floating-point drift meant no sample matched within
 * the epsilon, it silently fell through and reported the racer a whole segment further along than
 * they were. Here the samples and their cumulative arc lengths are computed once at construction,
 * so a query is a single scan that cannot miss.</p>
 *
 * <p>Also drops the legacy habit of appending a duplicate of the first control point to close the
 * loop: with the duplicate present, the tangent at the seam was computed from the wrong neighbours
 * and the spline kinked exactly at the start/finish line. Closure here is modular indexing.</p>
 */
public final class TrackPath {

    /** Spline samples per section. Ten was the 1.16.5 resolution and reads smoothly in-world. */
    private static final int SAMPLES_PER_SECTION = 10;

    private final List<TrackPoint> controlPoints;
    private final List<TrackPoint> samples;
    /** Cumulative arc length at each sample; {@code cumulative[i]} is the distance to {@code samples[i]}. */
    private final double[] cumulative;
    private final double totalLength;

    public TrackPath(List<TrackCheckpoint> checkpoints) {
        this.controlPoints = new ArrayList<>();
        if (checkpoints != null) {
            for (TrackCheckpoint checkpoint : checkpoints) {
                controlPoints.add(checkpoint.midpoint());
            }
        }

        this.samples = new ArrayList<>();
        if (controlPoints.size() >= 2) {
            for (int section = 0; section < controlPoints.size(); section++) {
                for (int step = 0; step < SAMPLES_PER_SECTION; step++) {
                    samples.add(pointOn(section, step / (double) SAMPLES_PER_SECTION));
                }
            }
        }

        this.cumulative = new double[samples.size()];
        double running = 0;
        for (int i = 1; i < samples.size(); i++) {
            running += samples.get(i).distanceTo(samples.get(i - 1));
            cumulative[i] = running;
        }
        // Close the loop: the last sample's distance back to the first completes the circuit.
        this.totalLength = samples.isEmpty()
                ? 0
                : running + samples.get(samples.size() - 1).distanceTo(samples.get(0));
    }

    /**
     * How far around the lap a position is, from 0 at the first checkpoint to just under 1 back at
     * it. Returns 0 for a track too small to have a path.
     *
     * <p>This is a ranking key, not a measurement: it answers "who is ahead" for two karts on the
     * same lap, and is meaningless for a kart off the track.</p>
     */
    public double progressOf(TrackPoint position) {
        if (samples.isEmpty() || totalLength <= 0 || position == null) {
            return 0;
        }
        int nearest = 0;
        double nearestDistance = Double.MAX_VALUE;
        for (int i = 0; i < samples.size(); i++) {
            double distance = samples.get(i).distanceSquaredTo(position);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = i;
            }
        }
        return cumulative[nearest] / totalLength;
    }

    /** The sampled polyline, for the editor's particle preview. */
    public List<TrackPoint> samplePoints() {
        return List.copyOf(samples);
    }

    public List<TrackPoint> controlPoints() {
        return List.copyOf(controlPoints);
    }

    public double totalLength() {
        return totalLength;
    }

    public boolean isUsable() {
        return totalLength > 0;
    }

    /**
     * Hermite interpolation within one section, with Catmull-Rom tangents (half the vector between
     * a control point's neighbours). Indices wrap, so the last section runs back into the first.
     */
    private TrackPoint pointOn(int section, double t) {
        int count = controlPoints.size();
        TrackPoint p0 = controlPoints.get(section % count);
        TrackPoint p1 = controlPoints.get((section + 1) % count);
        TrackPoint m0 = tangentAt(section % count);
        TrackPoint m1 = tangentAt((section + 1) % count);

        double t2 = t * t;
        double t3 = t2 * t;
        double h0 = 2 * t3 - 3 * t2 + 1;
        double h1 = -2 * t3 + 3 * t2;
        double h2 = t3 - 2 * t2 + t;
        double h3 = t3 - t2;

        return TrackPoint.at(
                h0 * p0.x() + h1 * p1.x() + h2 * m0.x() + h3 * m1.x(),
                h0 * p0.y() + h1 * p1.y() + h2 * m0.y() + h3 * m1.y(),
                h0 * p0.z() + h1 * p1.z() + h2 * m0.z() + h3 * m1.z());
    }

    private TrackPoint tangentAt(int index) {
        int count = controlPoints.size();
        TrackPoint previous = controlPoints.get((index - 1 + count) % count);
        TrackPoint next = controlPoints.get((index + 1) % count);
        return TrackPoint.at(
                (next.x() - previous.x()) * 0.5,
                (next.y() - previous.y()) * 0.5,
                (next.z() - previous.z()) * 0.5);
    }
}
