package es.boffmedia.teras.karts.engine;

/**
 * What one racer's HUD should show right now. Produced by {@link RaceCore} each HUD tick and turned
 * into a packet by the adapter.
 *
 * @param phase        which part of the race is happening
 * @param countdown    3, 2, 1 during the countdown; 0 is "GO"; -1 when not counting down
 * @param position     current standing, 1-based; 0 when not applicable
 * @param totalRacers  how many started
 * @param lap          lap being driven, 1-based
 * @param totalLaps    laps in the race
 * @param elapsedMs    time since the start
 * @param bestLapMs    this racer's best lap so far, or -1
 * @param wrongWay     whether they are currently driving the circuit backwards
 */
public record RaceHudState(RaceCore.Phase phase,
                           int countdown,
                           int position,
                           int totalRacers,
                           int lap,
                           int totalLaps,
                           long elapsedMs,
                           long bestLapMs,
                           boolean wrongWay) {

    /** The "nothing to show" state, sent once when a racer leaves a race so the HUD clears. */
    public static RaceHudState hidden() {
        return new RaceHudState(RaceCore.Phase.FINISHED, -1, 0, 0, 0, 0, 0, -1, false);
    }
}
