package es.boffmedia.teras.util.data;

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

    public static Objective getOrCreateObjective(ServerPlayer player, String objective) {
        String tag = objective.replace("/", "").toLowerCase(Locale.ROOT);
        Scoreboard scoreboard = player.getScoreboard();
        Objective existing = scoreboard.getObjective(tag);
        if (existing != null) {
            return existing;
        }
        return scoreboard.addObjective(tag, ObjectiveCriteria.DUMMY, Component.literal(tag),
                ObjectiveCriteria.RenderType.INTEGER, false, null);
    }

    public static void set(ServerPlayer player, String objective, int value) {
        Objective o = getOrCreateObjective(player, objective);
        player.getScoreboard().getOrCreatePlayerScore(player, o).set(value);
    }

    public static int get(ServerPlayer player, String objective) {
        Objective o = getOrCreateObjective(player, objective);
        return player.getScoreboard().getOrCreatePlayerScore(player, o).get();
    }
}
