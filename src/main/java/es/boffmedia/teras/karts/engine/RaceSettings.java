package es.boffmedia.teras.karts.engine;

/**
 * The tunables a race reads while it runs. Passed in rather than read from config directly so the
 * engine stays testable and a running race cannot change shape underneath itself when an admin
 * edits the config mid-event.
 *
 * @param minPlayers          fewest entrants a race will start with. The 1.16.5 vote rule was
 *                            "more than half voted", which a lone player trivially satisfied — they
 *                            could start a one-kart race by voting for themselves.
 * @param voteThresholdPct    share of entrants that must vote to start, 0–100
 * @param countdownTicks      grid hold before the start
 * @param timeoutTicks        hard cap on a race; whoever is still running is recorded as DNF. In
 *                            1.16.5 this existed only as a number shown in the status text, so a
 *                            stuck kart kept a race and its grid occupied indefinitely
 * @param rankingIntervalTicks how often standings are recomputed
 * @param hudIntervalTicks    how often the HUD is refreshed while running
 * @param dismountGraceTicks  how long a racer may be out of their kart before being recorded DNF
 * @param wrongWayGraceTicks  how long a racer must be travelling backwards before being told
 */
public record RaceSettings(int minPlayers,
                           int voteThresholdPct,
                           int countdownTicks,
                           int timeoutTicks,
                           int rankingIntervalTicks,
                           int hudIntervalTicks,
                           int dismountGraceTicks,
                           int wrongWayGraceTicks) {

    public static final int TICKS_PER_SECOND = 20;

    public static RaceSettings defaults() {
        return new RaceSettings(
                2,
                60,
                3 * TICKS_PER_SECOND,
                600 * TICKS_PER_SECOND,
                10,
                20,
                5 * TICKS_PER_SECOND,
                3 * TICKS_PER_SECOND);
    }

    public RaceSettings {
        minPlayers = Math.max(1, minPlayers);
        voteThresholdPct = Math.min(100, Math.max(1, voteThresholdPct));
        countdownTicks = Math.max(0, countdownTicks);
        timeoutTicks = Math.max(0, timeoutTicks);
        rankingIntervalTicks = Math.max(1, rankingIntervalTicks);
        hudIntervalTicks = Math.max(1, hudIntervalTicks);
        dismountGraceTicks = Math.max(0, dismountGraceTicks);
        wrongWayGraceTicks = Math.max(0, wrongWayGraceTicks);
    }

    /** A single-entrant variant, for time trials where there is nobody to wait for. */
    public RaceSettings soloed() {
        return new RaceSettings(1, 1, countdownTicks, timeoutTicks, rankingIntervalTicks,
                hudIntervalTicks, dismountGraceTicks, wrongWayGraceTicks);
    }

    public static long ticksToMillis(long ticks) {
        return ticks * 1000L / TICKS_PER_SECOND;
    }
}
