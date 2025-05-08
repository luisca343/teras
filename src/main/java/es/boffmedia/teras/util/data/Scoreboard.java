package es.boffmedia.teras.util.data;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreCriteria;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.util.text.StringTextComponent;

public class Scoreboard {

    public static ScoreObjective getOrCreateObjective(ServerPlayerEntity player, String objective){
        String tag = objective.replace("/","").toLowerCase();
        ServerScoreboard scoreboard = player.server.getScoreboard();
        ScoreObjective objetivo = scoreboard.getObjective(tag);

        if(objetivo == null){
            objetivo = scoreboard.addObjective(tag, ScoreCriteria.DUMMY, new StringTextComponent(tag), ScoreCriteria.RenderType.INTEGER);
            Score score = player.getScoreboard().getOrCreatePlayerScore(player.getName().getString(), objetivo);
            score.setScore(0);
        }
        return objetivo;
    }

    public static void set(ServerPlayerEntity player, String objective, int value){
        ScoreObjective objetivo = getOrCreateObjective(player, objective);

        Score score = player.getScoreboard().getOrCreatePlayerScore(player.getName().getString(), objetivo);
        score.setScore(value);
    }

    public static int get(ServerPlayerEntity player, String objective){
        ScoreObjective objetivo = getOrCreateObjective(player, objective);

        Score score = player.getScoreboard().getOrCreatePlayerScore(player.getName().getString(), objetivo);
        return score.getScore();
    }

    public static void add(ServerPlayerEntity player, String objective, int value){
        ScoreObjective objetivo = getOrCreateObjective(player, objective);

        Score score = player.getScoreboard().getOrCreatePlayerScore(player.getName().getString(), objetivo);
        score.setScore(score.getScore() + value);
    }

    public static void remove(ServerPlayerEntity player, String objective, int value){
        ScoreObjective objetivo = getOrCreateObjective(player, objective);

        Score score = player.getScoreboard().getOrCreatePlayerScore(player.getName().getString(), objetivo);
        score.setScore(score.getScore() - value);
    }
}
