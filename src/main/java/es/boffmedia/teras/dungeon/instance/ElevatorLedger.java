package es.boffmedia.teras.dungeon.instance;

import es.boffmedia.teras.dungeon.piso.DungeonDef;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who may board the ascensor, and how deep.
 *
 * <h2>What is stored, and why it is a tramo</h2>
 *
 * <p>Per player, per dungeon, <b>the deepest tramo index they may start at</b>. A tramo rather than
 * a stage because a tramo is what a run actually earns — you clear one by leaving it — and because
 * it survives a dungeon being re-cut: adding a floor to tramo 1 moves every stage number and moves
 * no unlock. {@link DungeonDef#firstStageOf} is the one place the two meet.</p>
 *
 * <p><b>Per player and not per party.</b> A veteran must not be able to hand a newcomer depth they
 * have never seen; what a party may do together is decided at the door, from the shallowest member
 * ({@code DungeonEntrance}).</p>
 *
 * <h2>Where the "is there anywhere to go" half lives</h2>
 *
 * <p>The fixture rule and the unlock rule are deliberately different. An ascensor <i>stands</i> on
 * the last floor of every tramo, including the dungeon's last, where it is the monument to
 * finishing. An unlock is only <i>recorded</i> when the tramo it would open actually exists, which
 * is the check in {@link #unlock}. Keeping the two apart is what lets a lift start working the day
 * a tramo is added behind it, with no change to any template.</p>
 *
 * <h2>Lifetime</h2>
 *
 * <p>Unlike {@code returns.json}, an entry here <b>never expires</b>. A return is consumed once, by
 * its owner logging back in, so a player who never comes back leaves litter; an unlock is a
 * permanent fact about a player, and re-earning it after a quiet month would be a punishment for
 * not playing.</p>
 *
 * <p><b>No Minecraft here</b>, the rule {@code Afliccion}, {@code RoomKeys} and {@code OrdenPool}
 * already follow: the unit-test classpath has no game on it, and these are rules worth testing.
 * {@link ElevatorAccess} is the half that talks to a server.</p>
 */
public final class ElevatorLedger {
    private ElevatorLedger() {}

    /** player -> dungeon id -> deepest tramo index unlocked. */
    private static Map<UUID, Map<String, Integer>> unlocks = new LinkedHashMap<>();

    /**
     * What to do when the table changes.
     *
     * <p>A no-op until a server exists. Persistence is wired in at server start rather than called
     * by name from {@link #unlock}, for the same reason the generator takes its randomness as a
     * parameter: the rules have to be exercisable with no config directory to write to.</p>
     */
    private static Runnable onChange = () -> {};

    static void adopt(Map<UUID, Map<String, Integer>> loaded, Runnable saver) {
        unlocks = loaded == null ? new LinkedHashMap<>() : loaded;
        onChange = saver == null ? () -> {} : saver;
    }

    /** Test seam: an in-memory table that is never written anywhere. */
    static void reset(Map<UUID, Map<String, Integer>> seed) {
        unlocks = seed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(seed);
        onChange = () -> {};
    }

    static Map<UUID, Map<String, Integer>> all() {
        return unlocks;
    }

    /** The deepest tramo {@code player} may board to in {@code dungeonId}; 0 is the entrance. */
    public static int deepest(UUID player, String dungeonId) {
        Map<String, Integer> byDungeon = unlocks.get(player);
        return byDungeon == null ? 0 : Math.max(0, byDungeon.getOrDefault(dungeonId, 0));
    }

    /**
     * Whether {@code player} may start a run at {@code stage}.
     *
     * <p>Stage 1 and anything inside the first tramo is always allowed — that is the front door.
     * Anything deeper needs the tramo it falls in to have been anchored. Unknown dungeon or
     * unplaceable stage allows it: refusing on a question we cannot answer would lock people out of
     * a dungeon whose config changed under them.</p>
     */
    public static boolean canBoard(DungeonDef dungeon, UUID player, String dungeonId, int stage) {
        if (dungeon == null || stage <= 1) {
            return true;
        }
        DungeonDef.Position at = dungeon.locate(stage);
        return at == null || at.tierIndex() <= 0 || deepest(player, dungeonId) >= at.tierIndex();
    }

    /**
     * Records that {@code player} may now start at {@code tramoIndex}, and returns whether anything
     * changed — so the caller only tells them when it is news.
     *
     * <p>Refuses a tramo the dungeon does not have. That is the whole of the "is there anywhere to
     * go" rule: the last tramo's lift banks nothing, and says nothing, while still standing.</p>
     */
    public static boolean unlock(DungeonDef dungeon, UUID player, String dungeonId,
                                 int tramoIndex) {
        if (dungeon == null || tramoIndex <= 0 || !dungeon.hasTramo(tramoIndex)) {
            return false;
        }
        Map<String, Integer> byDungeon = unlocks.computeIfAbsent(player, k -> new LinkedHashMap<>());
        if (byDungeon.getOrDefault(dungeonId, 0) >= tramoIndex) {
            return false;
        }
        byDungeon.put(dungeonId, tramoIndex);
        onChange.run();
        return true;
    }
}
