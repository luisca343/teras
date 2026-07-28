package es.boffmedia.teras.util.data;

import es.boffmedia.teras.Teras;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.Locale;

/**
 * Thin scoreboard helper, ported from the 1.16.5 {@code util.data.Scoreboard} to the 1.21 scores API
 * ({@code ScoreObjective}→{@link Objective}, {@code ScoreCriteria}→{@link ObjectiveCriteria},
 * {@code Score.setScore}→{@link net.minecraft.world.scores.ScoreAccess}). Named {@code TerasScoreboard}
 * to avoid clashing with vanilla {@link Scoreboard}.
 *
 * <p>Battles use a per-combat DUMMY objective (named after the config) to record whether a player has
 * beaten a given trainer — the SmartRotom objectives the web/quests key on.</p>
 */
public final class TerasScoreboard {
    private TerasScoreboard() {}

    /**
     * The objective for {@code objective}, creating it if the world has none — or <b>null</b> if
     * creating it failed.
     *
     * <p><b>Why creation is wrapped.</b> A trainer's objective is named after its config, so unlike
     * the dungeon's fixed set ({@code DungeonObjectives}) this cannot all be made at server start;
     * the first player to beat a trainer nobody has beaten before creates it mid-tick. That is the
     * shape that crashes a <i>server</i> in this pack: {@code Scoreboard.addObjective} calls
     * {@code ServerScoreboard.setDirty}, and CustomNPCs registers a dirty-listener at its own server
     * start that throws {@code NullPointerException} inside {@code Optional.of}.</p>
     *
     * <p>So the record is allowed to be lost — one line in the log, one trainer defeat unrecorded —
     * and the server is not. Long-lived worlds never notice either way, because their objectives
     * were created before that listener existed.</p>
     */
    public static Objective getOrCreateObjective(ServerPlayer player, String objective) {
        String tag = objective.replace("/", "").toLowerCase(Locale.ROOT);
        Scoreboard scoreboard = player.getScoreboard();
        Objective existing = scoreboard.getObjective(tag);
        if (existing != null) {
            return existing;
        }
        try {
            return scoreboard.addObjective(tag, ObjectiveCriteria.DUMMY, Component.literal(tag),
                    ObjectiveCriteria.RenderType.INTEGER, false, null);
        } catch (Throwable t) {
            Teras.LOGGER.error("Could not create the '{}' scoreboard objective, so this result is "
                    + "not recorded. Create it once with '/scoreboard objectives add {} dummy' and "
                    + "it will be written from then on.", tag, tag, t);
            return null;
        }
    }

    public static void set(ServerPlayer player, String objective, int value) {
        Objective o = getOrCreateObjective(player, objective);
        if (o != null) {
            player.getScoreboard().getOrCreatePlayerScore(player, o).set(value);
        }
    }

    public static int get(ServerPlayer player, String objective) {
        Objective o = getOrCreateObjective(player, objective);
        return o == null ? 0 : player.getScoreboard().getOrCreatePlayerScore(player, o).get();
    }
}
