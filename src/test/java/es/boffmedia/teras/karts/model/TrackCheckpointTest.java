package es.boffmedia.teras.karts.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checkpoint geometry, including the case that drove the redesign: a kart fast enough to be outside
 * the gate on two consecutive ticks having passed straight through it in between.
 */
class TrackCheckpointTest {

    /** A gate spanning x∈[0,4], y∈[64,68], z∈[0,4], plus the default 1-block padding. */
    private static TrackCheckpoint gate() {
        return new TrackCheckpoint(TrackPoint.at(0, 64, 0), TrackPoint.at(4, 68, 4));
    }

    @Test
    @DisplayName("contains a point inside the box")
    void containsInside() {
        assertTrue(gate().contains(TrackPoint.at(2, 66, 2)));
    }

    @Test
    @DisplayName("padding widens the box by a block on every side")
    void paddingWidensTheBox() {
        TrackCheckpoint gate = gate();
        assertTrue(gate.contains(TrackPoint.at(-0.5, 66, 2)), "half a block outside is still caught");
        assertFalse(gate.contains(TrackPoint.at(-1.5, 66, 2)), "beyond the padding is outside");
    }

    @Test
    @DisplayName("rejects a point outside on any single axis")
    void rejectsOutside() {
        TrackCheckpoint gate = gate();
        assertFalse(gate.contains(TrackPoint.at(2, 100, 2)));
        assertFalse(gate.contains(TrackPoint.at(50, 66, 2)));
        assertFalse(gate.contains(TrackPoint.at(2, 66, -50)));
    }

    @Test
    @DisplayName("a kart that tunnels straight through in one tick still counts the gate")
    void catchesTunneling() {
        // Ten blocks of travel in a single tick, starting and ending well clear of the box: the
        // 1.16.5 containment-only test missed this entirely and the lap never counted.
        TrackPoint before = TrackPoint.at(2, 66, -10);
        TrackPoint after = TrackPoint.at(2, 66, 10);
        assertFalse(gate().contains(before));
        assertFalse(gate().contains(after));
        assertTrue(gate().crossed(before, after));
    }

    @Test
    @DisplayName("travel that passes nearby but misses the gate does not count")
    void ignoresNearMiss() {
        TrackPoint before = TrackPoint.at(20, 66, -10);
        TrackPoint after = TrackPoint.at(20, 66, 10);
        assertFalse(gate().crossed(before, after));
    }

    @Test
    @DisplayName("travel that stops short of the gate does not count")
    void ignoresStoppingShort() {
        assertFalse(gate().crossed(TrackPoint.at(2, 66, -20), TrackPoint.at(2, 66, -10)));
    }

    @Test
    @DisplayName("sitting inside the gate counts, however it was reached")
    void countsWhenInside() {
        assertTrue(gate().crossed(TrackPoint.at(2, 66, -10), TrackPoint.at(2, 66, 2)));
        assertTrue(gate().crossed(null, TrackPoint.at(2, 66, 2)));
    }

    @Test
    @DisplayName("the very first sample cannot cross anything, only be inside it")
    void firstSampleNeedsContainment() {
        assertFalse(gate().crossed(null, TrackPoint.at(2, 66, -10)));
    }

    @Test
    @DisplayName("travel parallel to the gate but level with it still counts when it passes through")
    void catchesParallelTravel() {
        // Constant y and z, moving only in x, straight along the inside of the box.
        assertTrue(gate().crossed(TrackPoint.at(-20, 66, 2), TrackPoint.at(20, 66, 2)));
    }

    @Test
    @DisplayName("travel parallel to the gate but off to one side misses it")
    void parallelButOffAxisMisses() {
        assertFalse(gate().crossed(TrackPoint.at(-20, 200, 2), TrackPoint.at(20, 200, 2)));
    }

    @Test
    @DisplayName("corners are unordered — either diagonal defines the same box")
    void cornersAreUnordered() {
        TrackCheckpoint reversed = new TrackCheckpoint(TrackPoint.at(4, 68, 4), TrackPoint.at(0, 64, 0));
        assertTrue(reversed.contains(TrackPoint.at(2, 66, 2)));
        assertTrue(reversed.crossed(TrackPoint.at(2, 66, -10), TrackPoint.at(2, 66, 10)));
    }

    @Test
    @DisplayName("the midpoint sits at the centre of the two corners")
    void midpointIsCentre() {
        TrackPoint mid = gate().midpoint();
        org.junit.jupiter.api.Assertions.assertEquals(2, mid.x(), 1.0e-9);
        org.junit.jupiter.api.Assertions.assertEquals(66, mid.y(), 1.0e-9);
        org.junit.jupiter.api.Assertions.assertEquals(2, mid.z(), 1.0e-9);
    }
}
