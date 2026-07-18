package es.boffmedia.teras.karts.mode;

/**
 * A solo run against the clock. No opponents, no vote — the racer is alone on the circuit and the
 * race starts as soon as they are on the grid.
 *
 * <p>The point is the leaderboard: a time trial exists to set a time, so the result is compared
 * against the circuit's records rather than against other entrants.</p>
 */
public final class TimeTrialMode implements RaceMode {

    public static final String ID = "contrarreloj";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Contrarreloj";
    }

    @Override
    public int minPlayers(int configuredMinimum) {
        return 1;
    }

    @Override
    public boolean usesVoting() {
        return false;
    }
}
