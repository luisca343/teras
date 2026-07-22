package es.boffmedia.teras.dungeon.instance;

import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.DungeonLayout;
import es.boffmedia.teras.dungeon.run.DungeonWallet;
import es.boffmedia.teras.dungeon.run.PlayerRunState;

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

    /**
     * Where a member goes back to; the dimension is stored as a location string for the journal.
     * {@code gameMode} is the mode they entered with: runs force adventure (dungeon geometry is
     * not the party's to mine through), so going home has to give back whatever they had.
     */
    public record ReturnPoint(String dimension, double x, double y, double z, float yaw, float pitch,
                              String gameMode) {}

    private final int id;
    private final int slot;
    private final String dungeonId;
    /**
     * Curses are per floor now, not per run: a piso declares which it accepts, so a run that fixed
     * them at the start could hand LABYRINTH to a piso whose two shapes would sprawl to the room cap
     * in one repeated footprint. This is the <i>current</i> floor's set.
     */
    private Set<Curse> curses;
    /**
     * What the current floor <i>is</i>: its piso, its depth in the tramo, its difficulty. Held on
     * the run so everything downstream — the title card, spawn tables, the mechanic — reads one
     * decided answer instead of re-deriving it and risking a different one.
     */
    private es.boffmedia.teras.dungeon.piso.FloorPlan plan;
    /** Every curse this run has met, for the end-of-run report, which is about the run not a floor. */
    private final Set<Curse> cursesSeen = java.util.EnumSet.noneOf(Curse.class);
    private final Map<UUID, ReturnPoint> party = new LinkedHashMap<>();
    /**
     * The purse is the run's, not a player's: it outlives every floor (each stage rebuilds the
     * engine's {@code ActiveFloor}, never this) and it is shared, so clearing a room is not a race
     * to the drops.
     */
    private final DungeonWallet wallet = new DungeonWallet();
    private final Map<UUID, PlayerRunState> playerStates = new LinkedHashMap<>();

    /** Run statistics, for the end-of-run report. */
    private final long startedAtMs = System.currentTimeMillis();
    private final int startStage;
    private final Map<UUID, String> names = new LinkedHashMap<>();
    private int stagesCleared;
    private int coinsConverted;
    private boolean reported;

    private int stage;
    private DungeonLayout layout;
    private State state = State.BUILDING;
    private int builtId = -1;
    private int padIndex;

    public DungeonRun(int id, int slot, String dungeonId, int stage,
                      es.boffmedia.teras.dungeon.piso.FloorPlan plan, DungeonLayout layout) {
        this.id = id;
        this.slot = slot;
        this.dungeonId = dungeonId;
        this.stage = stage;
        this.startStage = stage;
        this.plan = plan;
        this.curses = plan.curses();
        this.cursesSeen.addAll(plan.curses());
        this.layout = layout;
    }

    /** What the current floor is. */
    public es.boffmedia.teras.dungeon.piso.FloorPlan plan() {
        return plan;
    }

    /** Which mazmorra this run is descending — its length is what decides when the run is over. */
    public String dungeonId() {
        return dungeonId;
    }

    /** Everything this run has been cursed with, across every floor. */
    public Set<Curse> cursesSeen() {
        return cursesSeen;
    }

    public long startedAtMs() {
        return startedAtMs;
    }

    public int startStage() {
        return startStage;
    }

    public int stagesCleared() {
        return stagesCleared;
    }

    public int coinsConverted() {
        return coinsConverted;
    }

    public void recordConversion(int coins) {
        coinsConverted += coins;
    }

    /** Names are captured at entry: a member who logs out still has to appear in the report. */
    public Map<UUID, String> names() {
        return names;
    }

    /**
     * One report per run. {@code completeRun} finishes by calling {@code end}, so without this the
     * same run would post twice — once as completed and once as whatever {@code end} inferred.
     */
    public boolean markReported() {
        if (reported) {
            return false;
        }
        reported = true;
        return true;
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

    /** The party's shared coin purse. */
    /**
     * What the whole party is carrying. One player accepts an offer and everyone lives with it,
     * which is what makes taking one a conversation rather than a click.
     */
    private final es.boffmedia.teras.dungeon.run.AfflictionSet afflictions =
            new es.boffmedia.teras.dungeon.run.AfflictionSet();

    public es.boffmedia.teras.dungeon.run.AfflictionSet afflictions() {
        return afflictions;
    }

    public DungeonWallet wallet() {
        return wallet;
    }

    /** Per-member run state (hearts owed, charms, deaths), created on first use. */
    public PlayerRunState stateOf(UUID member) {
        return playerStates.computeIfAbsent(member, id -> new PlayerRunState());
    }

    public Map<UUID, PlayerRunState> playerStates() {
        return playerStates;
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

    /** The floor now being entered; its curses fold into {@link #cursesSeen()} for the report. */
    public void enterFloor(es.boffmedia.teras.dungeon.piso.FloorPlan next) {
        this.plan = next;
        this.curses = next.curses();
        this.cursesSeen.addAll(next.curses());
    }

    public void advanceFloor(int stage, DungeonLayout layout, int builtId, int padIndex) {
        this.stage = stage;
        this.layout = layout;
        this.builtId = builtId;
        this.padIndex = padIndex;
        this.state = State.ACTIVE;
        this.stagesCleared++;
    }
}
