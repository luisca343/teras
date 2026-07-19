package es.boffmedia.teras.dungeon.instance;

import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.DungeonLayout;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One live run: a party descending stages inside one instance slot. The party is a map from day
 * one — co-op is a design commitment (DUNGEONS.md §6) even while the entry command only admits
 * the caller — and every member carries the return point they teleport back to when the run ends.
 *
 * <p>The floor fields (stage, layout, builtId, pad) advance together through
 * {@link #advanceFloor}: a run alternates between its slot's two pads so the next floor builds
 * while the party still stands on the current one.</p>
 */
public final class DungeonRun {

    public enum State { BUILDING, ACTIVE }

    /** Where a member goes back to; the dimension is stored as a location string for the journal. */
    public record ReturnPoint(String dimension, double x, double y, double z, float yaw, float pitch) {}

    private final int id;
    private final int slot;
    private final Set<Curse> curses;
    private final Map<UUID, ReturnPoint> party = new LinkedHashMap<>();

    private int stage;
    private DungeonLayout layout;
    private State state = State.BUILDING;
    private int builtId = -1;
    private int padIndex;

    public DungeonRun(int id, int slot, int stage, Set<Curse> curses, DungeonLayout layout) {
        this.id = id;
        this.slot = slot;
        this.stage = stage;
        this.curses = curses;
        this.layout = layout;
    }

    public int id() {
        return id;
    }

    public int slot() {
        return slot;
    }

    public int stage() {
        return stage;
    }

    public Set<Curse> curses() {
        return curses;
    }

    public DungeonLayout layout() {
        return layout;
    }

    public Map<UUID, ReturnPoint> party() {
        return party;
    }

    public State state() {
        return state;
    }

    public int builtId() {
        return builtId;
    }

    public int padIndex() {
        return padIndex;
    }

    public void activate(int builtId) {
        this.builtId = builtId;
        this.state = State.ACTIVE;
    }

    /** The next floor started building; the run is in transit until {@link #advanceFloor}. */
    public void beginAdvance() {
        this.state = State.BUILDING;
    }

    public void advanceFloor(int stage, DungeonLayout layout, int builtId, int padIndex) {
        this.stage = stage;
        this.layout = layout;
        this.builtId = builtId;
        this.padIndex = padIndex;
        this.state = State.ACTIVE;
    }
}
