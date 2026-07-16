package es.boffmedia.teras.battle.pixelmon.log;

import com.pixelmonmod.pixelmon.battles.BattleRegistry;
import com.pixelmonmod.pixelmon.battles.controller.BattleController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which Pixelmon battles Teras is building a Showdown log for, keyed by the
 * {@link BattleController} itself. The provider registers a battle at start; the
 * {@code BattleLog.logEvent} mixin looks it up per action (absent controller = a non-Teras battle,
 * ignored); the end handler renders and removes it.
 *
 * <p>Register with the controller handed back once the battle has started — {@code BattleBuilder.start}'s
 * future or {@code TeamSelectionRegistry}'s {@code battleStartConsumer}. {@code BattleBuilder.startHandler}
 * does not work: it binds to {@code BattleStartedEvent.Pre}, which Pixelmon posts straight to its event
 * bus instead of through {@code BattleController.publishEvent}, so builder handlers never see it.</p>
 */
public final class BattleLogRegistry {
    private BattleLogRegistry() {}

    private static final Map<BattleController, BattleLogSession> SESSIONS = new ConcurrentHashMap<>();

    /** Starts logging {@code controller}, replacing any stale entry for the same battle. */
    public static void register(BattleController controller) {
        if (controller == null) return;
        reapStale();
        SESSIONS.put(controller, new BattleLogSession(controller));
    }

    /** The active session for a battle, or {@code null} when the battle isn't logged by Teras. */
    public static BattleLogSession get(BattleController controller) {
        return controller == null ? null : SESSIONS.get(controller);
    }

    /** Renders the accumulated Showdown replay for a battle and stops logging it. */
    public static String finish(BattleController controller) {
        if (controller == null) return null;
        BattleLogSession session = SESSIONS.remove(controller);
        return session != null ? session.render() : null;
    }

    /** Drops sessions for battles that ended or never started, whose end handler never runs. */
    private static void reapStale() {
        SESSIONS.keySet().removeIf(c -> c.battleEnded || BattleRegistry.getBattle(c.battleIndex) != c);
    }
}
