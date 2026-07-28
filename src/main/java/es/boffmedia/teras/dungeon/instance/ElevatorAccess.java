package es.boffmedia.teras.dungeon.instance;

import es.boffmedia.teras.dungeon.piso.PisoCatalog;
import es.boffmedia.teras.dungeon.run.DungeonObjectives;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * The ascensor ledger's half that needs a server: loading it, and telling CustomNPCs about it.
 *
 * <p>Split from {@link ElevatorLedger} because that one holds the rules and the rules are tested,
 * and the unit-test classpath has no Minecraft on it.</p>
 */
public final class ElevatorAccess {
    private ElevatorAccess() {}

    /**
     * The objective el Guardián's dialogue reads.
     *
     * <p>A plain number rather than the pre-computed 0/1 booleans the Acreedor needs. CustomNPCs
     * compares an objective against a <b>literal typed in the editor</b> and never against another
     * objective — fatal when the threshold is dynamic ("purse ≥ price"), harmless when it is a
     * constant. Each tramo's dialogue asks {@code teras_ascensor BIGGER n-1}, and n is known when
     * the dialogue is authored.</p>
     *
     * <p><b>Deliberately not published by {@code DungeonNpcs.publishConditions}</b>, which clears
     * every objective it owns whenever the player is not in a run — which is exactly when el
     * Guardián needs to read this one. It is written at login and again whenever it changes, and it
     * is not in the set {@code RunEngine.unregister} zeroes.</p>
     */
    public static final String OBJECTIVE = "teras_ascensor";

    static void load() {
        ElevatorLedger.adopt(RunJournal.loadElevators(),
                () -> RunJournal.saveElevators(ElevatorLedger.all()));
    }

    /** Whether the player may board to {@code stage} of the default dungeon. */
    public static boolean canBoard(UUID player, int stage) {
        String dungeonId = PisoCatalog.defaultDungeonId();
        return ElevatorLedger.canBoard(PisoCatalog.dungeon(dungeonId), player, dungeonId, stage);
    }

    /** Records an unlock by dungeon id, looking the dungeon up so callers need not. */
    public static boolean unlock(UUID player, String dungeonId, int tramoIndex) {
        return ElevatorLedger.unlock(PisoCatalog.dungeon(dungeonId), player, dungeonId, tramoIndex);
    }

    /**
     * Writes {@link #OBJECTIVE} for the player, <b>if the world happens to have it</b>.
     *
     * <p>El Guardián's dialogues can therefore be picked by an ordinary CustomNPCs availability
     * ({@code teras_ascensor BIGGER n-1}). What availability still cannot do is hide a
     * <b>Command</b> option, so the per-tramo buttons stay gated by {@code CnpcBridge.gateDescents};
     * this objective is what lets the dialogue around them differ.</p>
     */
    public static void publish(ServerPlayer player) {
        DungeonObjectives.set(player, OBJECTIVE,
                ElevatorLedger.deepest(player.getUUID(), PisoCatalog.defaultDungeonId()));
    }
}
