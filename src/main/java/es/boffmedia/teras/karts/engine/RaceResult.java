package es.boffmedia.teras.karts.engine;

import java.util.List;
import java.util.UUID;

/**
 * How a race ended. Consumed by rewards, the leaderboard, and the report sent to the backend, so it
 * carries everything any of them need and nothing that only made sense while the race was running.
 *
 * @param trackName  circuit raced
 * @param modeId     which mode was run
 * @param laps       laps the race was set to
 * @param placements finishing order — finishers first in the order they crossed the line, then
 *                   anyone who retired
 */
public record RaceResult(String trackName, String modeId, int laps, List<Placement> placements) {

    public RaceResult {
        placements = List.copyOf(placements);
    }

    /**
     * @param position      1-based finishing position; retired racers keep the position they would
     *                      have held, so a payout table can still decide they earn nothing
     * @param timeMs        total race time, or -1 if they never finished
     * @param bestLapMs     their quickest lap, or -1 if they never completed one
     * @param lapsCompleted laps actually driven. Not always the race's lap count even for a
     *                      finisher: an elimination survivor wins by being the last one left, not
     *                      by covering the distance, so a consumer that treats the time as a
     *                      full-distance result (the leaderboard) has to check this
     * @param dnf           true if they retired, were disconnected, or ran out of time
     */
    public record Placement(UUID playerId, String playerName, int position,
                            long timeMs, long bestLapMs, int lapsCompleted, boolean dnf) {}

    /** The winner, if anybody actually finished. */
    public Placement winner() {
        return placements.stream().filter(placement -> !placement.dnf()).findFirst().orElse(null);
    }

    public int finisherCount() {
        return (int) placements.stream().filter(placement -> !placement.dnf()).count();
    }
}
