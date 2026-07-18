package es.boffmedia.teras.karts.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The spline is the tie-breaker that ranks two karts on the same lap, so what matters is that
 * progress increases as a kart drives the circuit in order and never jumps backwards.
 */
class TrackPathTest {

    /** Four gates at the corners of a 100×100 square, in clockwise order. */
    private static List<TrackCheckpoint> squareCircuit() {
        List<TrackCheckpoint> gates = new ArrayList<>();
        gates.add(gateAt(0, 0));
        gates.add(gateAt(100, 0));
        gates.add(gateAt(100, 100));
        gates.add(gateAt(0, 100));
        return gates;
    }

    private static TrackCheckpoint gateAt(double x, double z) {
        return new TrackCheckpoint(TrackPoint.at(x - 1, 64, z - 1), TrackPoint.at(x + 1, 66, z + 1));
    }

    @Test
    @DisplayName("progress increases as a kart drives the circuit in order")
    void progressIncreasesAlongTheCircuit() {
        TrackPath path = new TrackPath(squareCircuit());
        double atStart = path.progressOf(TrackPoint.at(0, 65, 0));
        double afterFirst = path.progressOf(TrackPoint.at(100, 65, 0));
        double afterSecond = path.progressOf(TrackPoint.at(100, 65, 100));
        double afterThird = path.progressOf(TrackPoint.at(0, 65, 100));

        assertTrue(atStart < afterFirst, "first corner is ahead of the start");
        assertTrue(afterFirst < afterSecond, "second corner is ahead of the first");
        assertTrue(afterSecond < afterThird, "third corner is ahead of the second");
    }

    @Test
    @DisplayName("progress stays within 0..1")
    void progressIsNormalised() {
        TrackPath path = new TrackPath(squareCircuit());
        for (TrackPoint sample : path.samplePoints()) {
            double progress = path.progressOf(sample);
            assertTrue(progress >= 0 && progress <= 1, "progress out of range: " + progress);
        }
    }

    @Test
    @DisplayName("the leader on a shared lap is whoever is further along")
    void ranksTwoKartsOnTheSameLap() {
        TrackPath path = new TrackPath(squareCircuit());
        double behind = path.progressOf(TrackPoint.at(30, 65, 0));
        double ahead = path.progressOf(TrackPoint.at(70, 65, 0));
        assertTrue(ahead > behind);
    }

    @Test
    @DisplayName("a kart back at the start line reads near the end of the lap, not the beginning")
    void wrapsAtTheSeam() {
        TrackPath path = new TrackPath(squareCircuit());
        // Just before completing the loop, coming back down the final straight toward the start.
        double nearlyHome = path.progressOf(TrackPoint.at(0, 65, 20));
        assertTrue(nearlyHome > 0.5, "expected to be past halfway, was " + nearlyHome);
    }

    @Test
    @DisplayName("a circuit that cannot form a path reports itself unusable instead of dividing by zero")
    void degenerateCircuitIsUnusable() {
        assertFalse(new TrackPath(List.of()).isUsable());
        assertFalse(new TrackPath(null).isUsable());
        assertEquals(0, new TrackPath(List.of()).progressOf(TrackPoint.at(1, 2, 3)));
    }

    @Test
    @DisplayName("total length is in the right ballpark for the circuit's shape")
    void totalLengthIsPlausible() {
        TrackPath path = new TrackPath(squareCircuit());
        // A 100×100 square is 400 blocks around; the spline rounds the corners, so expect roughly
        // that, and certainly not an order of magnitude off.
        assertTrue(path.totalLength() > 300 && path.totalLength() < 500,
                "unexpected circuit length: " + path.totalLength());
    }

    @Test
    @DisplayName("control points are the checkpoint midpoints, in order")
    void controlPointsAreCheckpointMidpoints() {
        TrackPath path = new TrackPath(squareCircuit());
        List<TrackPoint> controls = path.controlPoints();
        assertEquals(4, controls.size());
        assertEquals(0, controls.get(0).x(), 1.0e-9);
        assertEquals(100, controls.get(1).x(), 1.0e-9);
    }
}
