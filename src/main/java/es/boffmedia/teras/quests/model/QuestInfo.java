package es.boffmedia.teras.quests.model;

import java.util.List;
import java.util.Objects;

/**
 * One quest as the SmartRotom web sees it, and as it is cached in {@code config/teras/misiones.json}.
 *
 * <p>This consolidates the 1.16.5 {@code QuestDataBase} + the <b>two</b> unrelated {@code QuestData}
 * classes ({@code model.quests} and {@code util.objects.quests}) that had drifted apart. The union of
 * their live fields is kept, so the JSON written to {@code misiones.json} is unchanged; the dead
 * {@code PlayerQuests}/{@code DialogData} pair was dropped.</p>
 *
 * <p>Kept free of CustomNPCs types on purpose — {@code QuestBuilder} does the translation — so this
 * class loads even when CustomNPCs is absent.</p>
 */
public class QuestInfo {
    private int id = -1;
    private String name = "";
    private String skin = "";
    private double x;
    private double y;
    private double z;
    private String npcName = "";
    private String category = "";
    private int nextQuest = -1;
    private int type;
    private String completeText = "";
    private String logText = "";
    private boolean repeatable;
    private int dialogId;
    private QuestStatus status = QuestStatus.NOT_STARTED;
    private List<QuestReward> rewards = List.of();
    private List<QuestObjective> objectives = List.of();
    private QuestRequirement requirements;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSkin() { return skin; }
    public void setSkin(String skin) { this.skin = skin; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }

    public String getNpcName() { return npcName; }
    public void setNpcName(String npcName) { this.npcName = npcName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getNextQuest() { return nextQuest; }
    public void setNextQuest(int nextQuest) { this.nextQuest = nextQuest; }

    public int getType() { return type; }
    public void setType(int type) { this.type = type; }

    public String getCompleteText() { return completeText; }
    public void setCompleteText(String completeText) { this.completeText = completeText; }

    public String getLogText() { return logText; }
    public void setLogText(String logText) { this.logText = logText; }

    public boolean isRepeatable() { return repeatable; }
    public void setRepeatable(boolean repeatable) { this.repeatable = repeatable; }

    public int getDialogId() { return dialogId; }
    public void setDialogId(int dialogId) { this.dialogId = dialogId; }

    public QuestStatus getStatus() { return status; }
    public void setStatus(QuestStatus status) { this.status = status; }

    public List<QuestReward> getRewards() { return rewards; }
    public void setRewards(List<QuestReward> rewards) { this.rewards = rewards; }

    public List<QuestObjective> getObjectives() { return objectives; }
    public void setObjectives(List<QuestObjective> objectives) { this.objectives = objectives; }

    public QuestRequirement getRequirements() { return requirements; }
    public void setRequirements(QuestRequirement requirements) { this.requirements = requirements; }

    /**
     * Compares only the quest's <i>definition</i> — deliberately ignoring {@code status},
     * {@code objectives} and {@code rewards}, which are per-player and would otherwise make every
     * dialog open look like a change. This is the 1.16.5 dirty-check that decides whether
     * {@code misiones.json} gets rewritten, preserved field-for-field.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof QuestInfo other)) return false;
        return id == other.id
                && Objects.equals(npcName, other.npcName)
                && Objects.equals(name, other.name)
                && Objects.equals(skin, other.skin)
                && Double.compare(x, other.x) == 0
                && Double.compare(y, other.y) == 0
                && Double.compare(z, other.z) == 0
                && Objects.equals(category, other.category)
                && nextQuest == other.nextQuest
                && type == other.type
                && Objects.equals(completeText, other.completeText)
                && Objects.equals(logText, other.logText)
                && repeatable == other.repeatable;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, npcName, name, skin, x, y, z, category, nextQuest, type,
                completeText, logText, repeatable);
    }
}
