package es.boffmedia.teras.quests.model;

/**
 * A scoreboard gate on a dialog/quest. As with {@link FactionRequirement}, the CustomNPCs
 * {@code EnumAvailabilityScoreboard} is stored as its {@code name()} string to keep this package
 * free of CustomNPCs classes; the serialized JSON is identical.
 */
public class ScoreboardRequirement {
    private final String scoreboardObjective;
    private final String scoreboardType;
    private final int scoreboardValue;

    public ScoreboardRequirement(String scoreboardObjective, String scoreboardType, int scoreboardValue) {
        this.scoreboardObjective = scoreboardObjective;
        this.scoreboardType = scoreboardType;
        this.scoreboardValue = scoreboardValue;
    }

    public String getScoreboardObjective() { return scoreboardObjective; }
    public String getScoreboardType() { return scoreboardType; }
    public int getScoreboardValue() { return scoreboardValue; }
}
