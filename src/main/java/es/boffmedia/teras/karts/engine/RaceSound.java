package es.boffmedia.teras.karts.engine;

/**
 * The sounds a race makes, named by what they mean rather than by a Minecraft sound event, so the
 * engine stays free of game types and the adapter can re-skin them.
 */
public enum RaceSound {
    /** Each of the 3-2-1 pips. */
    COUNTDOWN_TICK,
    /** The start. */
    COUNTDOWN_GO,
    LAP_COMPLETED,
    FINISH_WINNER,
    FINISH_OTHER,
    ELIMINATED,
    WRONG_WAY
}
