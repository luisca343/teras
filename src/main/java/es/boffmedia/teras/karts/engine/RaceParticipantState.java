package es.boffmedia.teras.karts.engine;

import es.boffmedia.teras.karts.model.TrackPoint;

import java.util.UUID;

/**
 * One racer's progress. Pure state with no behaviour beyond bookkeeping — {@link RaceCore} decides
 * what any of it means.
 */
public final class RaceParticipantState {

    private final UUID playerId;
    private final String playerName;

    private int checkpointIndex;
    private int lapsCompleted;
    private TrackPoint lastPosition;
    private double lastProgress;
    private boolean hasProgress;

    private long lapStartTick;
    private long bestLapTicks = -1;
    private long finishTick = -1;
    private int finishPlace;

    private boolean voted;
    private boolean dnf;
    private boolean eliminated;

    private int missingTicks;
    private int wrongWayTicks;

    public RaceParticipantState(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
    }

    public UUID playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    public int checkpointIndex() {
        return checkpointIndex;
    }

    public int lapsCompleted() {
        return lapsCompleted;
    }

    /** The lap being driven right now, counting from 1 — what a player expects to see on a HUD. */
    public int currentLap() {
        return lapsCompleted + 1;
    }

    public TrackPoint lastPosition() {
        return lastPosition;
    }

    public void setLastPosition(TrackPoint position) {
        this.lastPosition = position;
    }

    public double lastProgress() {
        return lastProgress;
    }

    public boolean hasProgress() {
        return hasProgress;
    }

    public void setProgress(double progress) {
        this.lastProgress = progress;
        this.hasProgress = true;
    }

    public void advanceCheckpoint() {
        checkpointIndex++;
    }

    public void completeLap(long tick) {
        lapsCompleted++;
        checkpointIndex = 0;
        long lapTicks = tick - lapStartTick;
        if (bestLapTicks < 0 || lapTicks < bestLapTicks) {
            bestLapTicks = lapTicks;
        }
        lapStartTick = tick;
    }

    public void beginRacing(long tick) {
        this.lapStartTick = tick;
    }

    public long bestLapTicks() {
        return bestLapTicks;
    }

    public long finishTick() {
        return finishTick;
    }

    public void finish(long tick, int place) {
        this.finishTick = tick;
        this.finishPlace = place;
    }

    public int finishPlace() {
        return finishPlace;
    }

    public boolean hasFinished() {
        return finishTick >= 0;
    }

    public boolean voted() {
        return voted;
    }

    public void setVoted(boolean voted) {
        this.voted = voted;
    }

    public boolean dnf() {
        return dnf;
    }

    public void setDnf(boolean dnf) {
        this.dnf = dnf;
    }

    public boolean eliminated() {
        return eliminated;
    }

    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }

    /** Whether this racer is still being tracked: not finished, retired, or knocked out. */
    public boolean isActive() {
        return !hasFinished() && !dnf && !eliminated;
    }

    public int missingTicks() {
        return missingTicks;
    }

    public int addMissingTick() {
        return ++missingTicks;
    }

    public void clearMissingTicks() {
        missingTicks = 0;
    }

    public int wrongWayTicks() {
        return wrongWayTicks;
    }

    public int addWrongWayTick() {
        return ++wrongWayTicks;
    }

    public void clearWrongWayTicks() {
        wrongWayTicks = 0;
    }
}
