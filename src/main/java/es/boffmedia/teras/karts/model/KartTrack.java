package es.boffmedia.teras.karts.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A circuit: where karts line up, and the ordered gates they must pass through each lap.
 *
 * <p>Mutable, because it is what the {@code /karts circuito} editor builds up incrementally; the
 * store persists a snapshot after every change. The spline is derived, so it is rebuilt on demand
 * rather than stored — see {@link #path()}.</p>
 */
public final class KartTrack {

    public static final int DEFAULT_LAPS = 3;

    private final String name;
    private String displayName;
    private String dimension;
    private int defaultLaps = DEFAULT_LAPS;
    private final List<TrackPoint> startingPoints = new ArrayList<>();
    private final List<TrackCheckpoint> checkpoints = new ArrayList<>();
    // Unmodifiable views over the lists above, not copies: a race reads the checkpoints once per
    // racer per tick, and copying a circuit's worth of gates that often is pure garbage.
    private final List<TrackPoint> startingPointsView = java.util.Collections.unmodifiableList(startingPoints);
    private final List<TrackCheckpoint> checkpointsView = java.util.Collections.unmodifiableList(checkpoints);

    private TrackPath cachedPath;
    private int cachedPathGeneration = -1;
    private int generation;

    public KartTrack(String name) {
        this.name = name;
        this.displayName = name;
    }

    public String name() {
        return name;
    }

    public String displayName() {
        return displayName == null || displayName.isBlank() ? name : displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /** The level this circuit lives in, as a {@code ResourceLocation} string. */
    public String dimension() {
        return dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension;
    }

    public int defaultLaps() {
        return defaultLaps;
    }

    public void setDefaultLaps(int defaultLaps) {
        this.defaultLaps = Math.max(1, defaultLaps);
    }

    public List<TrackPoint> startingPoints() {
        return startingPointsView;
    }

    public List<TrackCheckpoint> checkpoints() {
        return checkpointsView;
    }

    /** How many karts can line up — the grid size, and so the cap on entrants. */
    public int gridSize() {
        return startingPoints.size();
    }

    public void addStartingPoint(TrackPoint point) {
        startingPoints.add(point);
        touch();
    }

    public boolean removeStartingPoint(int index) {
        if (index < 0 || index >= startingPoints.size()) {
            return false;
        }
        startingPoints.remove(index);
        touch();
        return true;
    }

    public void addCheckpoint(TrackCheckpoint checkpoint) {
        checkpoints.add(checkpoint);
        touch();
    }

    public boolean removeCheckpoint(int index) {
        if (index < 0 || index >= checkpoints.size()) {
            return false;
        }
        checkpoints.remove(index);
        touch();
        return true;
    }

    public void clearCheckpoints() {
        checkpoints.clear();
        touch();
    }

    public void clearStartingPoints() {
        startingPoints.clear();
        touch();
    }

    /**
     * The ranking spline, rebuilt only when the checkpoints have actually changed. A race queries
     * this every ranking tick, and building it samples the whole circuit.
     */
    public TrackPath path() {
        if (cachedPath == null || cachedPathGeneration != generation) {
            cachedPath = new TrackPath(checkpoints);
            cachedPathGeneration = generation;
        }
        return cachedPath;
    }

    private void touch() {
        generation++;
    }
}
