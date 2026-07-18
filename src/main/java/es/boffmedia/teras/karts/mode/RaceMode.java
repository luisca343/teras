package es.boffmedia.teras.karts.mode;

import es.boffmedia.teras.karts.engine.RaceCore;
import es.boffmedia.teras.karts.engine.RaceParticipantState;

/**
 * What kind of race this is. Consulted by {@link RaceCore} at a few fixed points rather than
 * subclassing it, so there is exactly one state machine to reason about and modes stay small enough
 * to test on their own.
 */
public interface RaceMode {

    /** Stable id, used in results, the backend report, and command arguments. */
    String id();

    /** Human-readable Spanish name for chat and the HUD. */
    String displayName();

    /** Fewest entrants this mode needs. A time trial overrides the normal minimum to one. */
    default int minPlayers(int configuredMinimum) {
        return configuredMinimum;
    }

    /** Whether entrants vote to start. Solo modes start as soon as the racer is on the grid. */
    default boolean usesVoting() {
        return true;
    }

    /** Called after a racer completes a lap — where elimination knocks out the last-placed kart. */
    default void onLapCompleted(RaceCore race, RaceParticipantState participant) {
    }

    /**
     * Whether the race is over. The default ends it once nobody is still driving, which is what
     * every mode wants; elimination additionally ends it when one racer is left.
     */
    default boolean isFinished(RaceCore race) {
        return race.activeParticipants().isEmpty();
    }
}
