package es.boffmedia.teras.util.objects.quests;

import noppes.npcs.constants.EnumAvailabilityScoreboard;

public class ScoreboardRequirement {
    private String scoreboardObjective;
    private EnumAvailabilityScoreboard scoreboardType;
    private int scoreboardValue;
    public ScoreboardRequirement(String scoreboardObjective, EnumAvailabilityScoreboard scoreboardType, int scoreboardValue) {
        this.scoreboardObjective = scoreboardObjective;
        this.scoreboardType = scoreboardType;
        this.scoreboardValue = scoreboardValue;
    }

    public String getScoreboardObjective() {
        return scoreboardObjective;
    }

    public void setScoreboardObjective(String scoreboardObjective) {
        this.scoreboardObjective = scoreboardObjective;
    }

    public EnumAvailabilityScoreboard getScoreboardType() {
        return scoreboardType;
    }

    public void setScoreboardType(EnumAvailabilityScoreboard scoreboardType) {
        this.scoreboardType = scoreboardType;
    }

    public int getScoreboardValue() {
        return scoreboardValue;
    }

    public void setScoreboardValue(int scoreboardValue) {
        this.scoreboardValue = scoreboardValue;
    }
}
