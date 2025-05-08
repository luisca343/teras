package es.boffmedia.teras.util.objects.quests;

import noppes.npcs.constants.EnumAvailabilityFaction;
import noppes.npcs.constants.EnumAvailabilityFactionType;
import noppes.npcs.constants.EnumAvailabilityScoreboard;

import java.util.ArrayList;

public class QuestRequirement {
    boolean available;
    private ArrayList<Integer> requiredQuests;
    private ArrayList<Integer> requiredDialogs;
    private int requiredLevel;
    private int requiredTime;
    private ArrayList<FactionRequirement> factionRequirements;
    private ArrayList<ScoreboardRequirement> scoreboardRequirements;

    public QuestRequirement() {
        requiredQuests = new ArrayList<>();
        requiredDialogs = new ArrayList<>();
        factionRequirements = new ArrayList<>();
        scoreboardRequirements = new ArrayList<>();
    }

    public void addQuest(int questId) {
        if(!requiredQuests.contains(questId) && questId != -1) requiredQuests.add(questId);
    }

    public void addDialog(int dialogId) {
        if(!requiredDialogs.contains(dialogId) && dialogId != -1) requiredDialogs.add(dialogId);
    }

    public void setTime(int daytime) {
        requiredTime = daytime;
    }

    public void setLevel(int level) {
        requiredLevel = level;
    }

    public void addFactionRequirement(int factionId, EnumAvailabilityFactionType factionAvailable, EnumAvailabilityFaction factionStance) {
        FactionRequirement factionRequirement = new FactionRequirement(factionId, factionAvailable, factionStance);
        addFactionRequirement(factionRequirement);
    }
    public void addFactionRequirement(FactionRequirement factionRequirement) {
        if(!factionRequirements.stream().anyMatch(req -> factionRequirement.getFactionId() == req.getFactionId())
                && factionRequirement.getFactionId()!= -1 ) factionRequirements.add(factionRequirement);
    }

    public void addScoreboardRequirement(String scoreboard2Objective, EnumAvailabilityScoreboard scoreboard2Type, int scoreboard2Value) {
        ScoreboardRequirement scoreboardRequirement = new ScoreboardRequirement(scoreboard2Objective, scoreboard2Type, scoreboard2Value);
        addScoreboardRequirement(scoreboardRequirement);
    }
    public void addScoreboardRequirement(ScoreboardRequirement scoreboardRequirement) {
        if(!scoreboardRequirements.stream().anyMatch(req -> scoreboardRequirement.getScoreboardObjective().equals(req.getScoreboardObjective()))
                && !scoreboardRequirement.getScoreboardObjective().isEmpty()) scoreboardRequirements.add(scoreboardRequirement);
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public ArrayList<Integer> getRequiredQuests() {
        return requiredQuests;
    }

    public void setRequiredQuests(ArrayList<Integer> requiredQuests) {
        this.requiredQuests = requiredQuests;
    }

    public ArrayList<Integer> getRequiredDialogs() {
        return requiredDialogs;
    }

    public void setRequiredDialogs(ArrayList<Integer> requiredDialogs) {
        this.requiredDialogs = requiredDialogs;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }

    public void setRequiredLevel(int requiredLevel) {
        this.requiredLevel = requiredLevel;
    }

    public int getRequiredTime() {
        return requiredTime;
    }

    public void setRequiredTime(int requiredTime) {
        this.requiredTime = requiredTime;
    }

    public ArrayList<FactionRequirement> getFactionRequirements() {
        return factionRequirements;
    }

    public void setFactionRequirements(ArrayList<FactionRequirement> factionRequirements) {
        this.factionRequirements = factionRequirements;
    }

    public ArrayList<ScoreboardRequirement> getScoreboardRequirements() {
        return scoreboardRequirements;
    }

    public void setScoreboardRequirements(ArrayList<ScoreboardRequirement> scoreboardRequirements) {
        this.scoreboardRequirements = scoreboardRequirements;
    }

}
