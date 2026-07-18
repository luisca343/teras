package es.boffmedia.teras.karts.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validation runs both when an admin asks and again before a race starts, so it has to catch the
 * states that would otherwise fail mid-race in a way nobody can diagnose.
 */
class TrackValidatorTest {

    private static KartTrack raceableTrack() {
        KartTrack track = new KartTrack("circuito");
        track.setDimension("minecraft:overworld");
        track.addStartingPoint(new TrackPoint(200, 64, 200, 0f));
        track.addCheckpoint(new TrackCheckpoint(TrackPoint.at(0, 64, 0), TrackPoint.at(4, 68, 4)));
        track.addCheckpoint(new TrackCheckpoint(TrackPoint.at(50, 64, 0), TrackPoint.at(54, 68, 4)));
        track.addCheckpoint(new TrackCheckpoint(TrackPoint.at(50, 64, 50), TrackPoint.at(54, 68, 54)));
        return track;
    }

    @Test
    @DisplayName("a complete circuit passes")
    void acceptsCompleteTrack() {
        assertTrue(TrackValidator.validate(raceableTrack()).isEmpty());
        assertTrue(TrackValidator.isRaceable(raceableTrack()));
    }

    @Test
    @DisplayName("too few checkpoints is reported")
    void rejectsTooFewCheckpoints() {
        KartTrack track = new KartTrack("c");
        track.setDimension("minecraft:overworld");
        track.addStartingPoint(new TrackPoint(0, 64, 0, 0f));
        track.addCheckpoint(new TrackCheckpoint(TrackPoint.at(0, 64, 0), TrackPoint.at(4, 68, 4)));

        assertTrue(hasProblemMentioning(TrackValidator.validate(track), "checkpoints"));
        assertFalse(TrackValidator.isRaceable(track));
    }

    @Test
    @DisplayName("no grid means nowhere to line up")
    void rejectsMissingStartingPoints() {
        KartTrack track = raceableTrack();
        track.clearStartingPoints();
        assertTrue(hasProblemMentioning(TrackValidator.validate(track), "salida"));
    }

    @Test
    @DisplayName("a circuit with no dimension is rejected rather than raced in the wrong world")
    void rejectsMissingDimension() {
        KartTrack track = raceableTrack();
        track.setDimension(null);
        assertTrue(hasProblemMentioning(TrackValidator.validate(track), "dimensión"));
    }

    @Test
    @DisplayName("a grid slot inside the first gate would hand out a free checkpoint")
    void rejectsStartInsideFirstCheckpoint() {
        KartTrack track = raceableTrack();
        track.clearStartingPoints();
        track.addStartingPoint(new TrackPoint(2, 66, 2, 0f));
        assertTrue(hasProblemMentioning(TrackValidator.validate(track), "primer checkpoint"));
    }

    @Test
    @DisplayName("a null track is reported rather than throwing")
    void handlesNullTrack() {
        assertFalse(TrackValidator.validate(null).isEmpty());
        assertFalse(TrackValidator.isRaceable(null));
    }

    private static boolean hasProblemMentioning(List<String> problems, String fragment) {
        return problems.stream().anyMatch(problem -> problem.toLowerCase().contains(fragment.toLowerCase()));
    }
}
