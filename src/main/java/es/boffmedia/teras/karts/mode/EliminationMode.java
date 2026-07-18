package es.boffmedia.teras.karts.mode;

import es.boffmedia.teras.karts.engine.RaceCore;
import es.boffmedia.teras.karts.engine.RaceParticipantState;

/**
 * Last place is knocked out at the end of every lap, until one racer is left.
 *
 * <p>Elimination happens on the <b>leader's</b> lap, not on each racer's own: cutting whoever is
 * last when the front-runner comes round is what keeps the field tightening. Doing it per-racer
 * would eliminate on a schedule nobody could see, and could cut someone who had just been lapped
 * through no fault of their pace.</p>
 *
 * <p>The configured lap count still applies as a backstop, so an elimination race on a long circuit
 * with a small field cannot run forever.</p>
 */
public final class EliminationMode implements RaceMode {

    public static final String ID = "eliminacion";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Eliminación";
    }

    @Override
    public void onLapCompleted(RaceCore race, RaceParticipantState participant) {
        // Only the racer out in front triggers a cut.
        if (race.positionOf(participant.playerId()) != 1) {
            return;
        }
        // Never cut down to nobody: with two left, the lap that eliminates one leaves a winner.
        if (race.activeParticipants().size() <= 1) {
            return;
        }
        RaceParticipantState last = race.lastPlaceActive();
        if (last == null || last.playerId().equals(participant.playerId())) {
            return;
        }
        race.eliminate(last);
        race.announce(last.playerName() + " ha sido eliminado.");
    }

    /**
     * Ends as soon as one racer is left standing, rather than waiting for them to finish the
     * remaining laps against nobody.
     */
    @Override
    public boolean isFinished(RaceCore race) {
        return race.hasStarted() && race.activeParticipants().size() <= 1;
    }
}
