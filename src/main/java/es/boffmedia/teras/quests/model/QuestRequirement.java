package es.boffmedia.teras.quests.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything gating a quest/dialog: prerequisite quests and dialogs, a minimum player level, a
 * daytime window, plus faction and scoreboard gates. Ported from the 1.16.5 {@code QuestRequirement};
 * the {@code add*} methods keep the original's dedup + sentinel rules, since those decide what the
 * SmartRotom web is told:
 *
 * <ul>
 *   <li>quest/dialog ids of {@code -1} mean "unset" and are dropped;</li>
 *   <li>a faction gate with id {@code -1} is dropped, and only the first gate per faction id is kept;</li>
 *   <li>a scoreboard gate with a blank objective is dropped, and only the first per objective is kept.</li>
 * </ul>
 */
public class QuestRequirement {
    private boolean available;
    private final List<Integer> requiredQuests = new ArrayList<>();
    private final List<Integer> requiredDialogs = new ArrayList<>();
    private int requiredLevel;
    private int requiredTime;
    private final List<FactionRequirement> factionRequirements = new ArrayList<>();
    private final List<ScoreboardRequirement> scoreboardRequirements = new ArrayList<>();

    public void addQuest(int questId) {
        if (questId != -1 && !requiredQuests.contains(questId)) requiredQuests.add(questId);
    }

    public void addDialog(int dialogId) {
        if (dialogId != -1 && !requiredDialogs.contains(dialogId)) requiredDialogs.add(dialogId);
    }

    public void addFactionRequirement(int factionId, String factionAvailable, String factionStance) {
        if (factionId == -1) return;
        if (factionRequirements.stream().anyMatch(r -> r.getFactionId() == factionId)) return;
        factionRequirements.add(new FactionRequirement(factionId, factionAvailable, factionStance));
    }

    public void addScoreboardRequirement(String objective, String type, int value) {
        if (objective == null || objective.isEmpty()) return;
        if (scoreboardRequirements.stream().anyMatch(r -> r.getScoreboardObjective().equals(objective))) return;
        scoreboardRequirements.add(new ScoreboardRequirement(objective, type, value));
    }

    public void setAvailable(boolean available) { this.available = available; }
    public boolean isAvailable() { return available; }

    public void setRequiredLevel(int requiredLevel) { this.requiredLevel = requiredLevel; }
    public int getRequiredLevel() { return requiredLevel; }

    public void setRequiredTime(int requiredTime) { this.requiredTime = requiredTime; }
    public int getRequiredTime() { return requiredTime; }

    public List<Integer> getRequiredQuests() { return requiredQuests; }
    public List<Integer> getRequiredDialogs() { return requiredDialogs; }
    public List<FactionRequirement> getFactionRequirements() { return factionRequirements; }
    public List<ScoreboardRequirement> getScoreboardRequirements() { return scoreboardRequirements; }
}
