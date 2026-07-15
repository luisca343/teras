package es.boffmedia.teras.util.objects.quests;

import noppes.npcs.api.handler.data.IDialog;
import noppes.npcs.api.handler.data.IQuest;
import noppes.npcs.controllers.data.Availability;

public class QuestData extends QuestDataBase{
    private String name;
    private String logText;
    private String completeText;
    private boolean repeatable;
    private int type;
    private int nextQuest;
    private String category;

    private QuestRequirement requirements;

    public QuestData(IQuest quest, boolean simplified) {
        super(quest, simplified);
        if(!simplified){
            this.name = quest.getName();
            this.logText = quest.getLogText();
            this.completeText = quest.getCompleteText();
            this.repeatable = quest.getIsRepeatable();
            this.type = quest.getType();
            this.nextQuest = quest.getNextQuest() != null ? quest.getNextQuest().getId() : -1;
            this.category = quest.getCategory().getName();
        }
    }

    public QuestData(IQuest quest, IDialog dialog){
        super(quest);
        this.name = quest.getName();
        this.logText = quest.getLogText();
        this.completeText = quest.getCompleteText();
        this.repeatable = quest.getIsRepeatable();
        this.type = quest.getType();
        this.nextQuest = quest.getNextQuest() != null ? quest.getNextQuest().getId() : -1;
        this.category = quest.getCategory().getName();

        QuestRequirement requirement = new QuestRequirement();

        Availability availability = (Availability) dialog.getAvailability();
        for (int i = 0; i < 4; i++) {
            requirement.addQuest(availability.getQuest(i));
            requirement.addDialog(availability.getDialog(i));
        }

        requirement.setTime(availability.getDaytime());
        requirement.setLevel(availability.getMinPlayerLevel());

        requirement.addFactionRequirement(availability.factionId, availability.factionAvailable, availability.factionStance);
        requirement.addFactionRequirement(availability.faction2Id, availability.faction2Available, availability.faction2Stance);

        requirement.addScoreboardRequirement(availability.scoreboardObjective, availability.scoreboardType, availability.scoreboardValue);
        requirement.addScoreboardRequirement(availability.scoreboard2Objective, availability.scoreboard2Type, availability.scoreboard2Value);

        this.requirements = requirement;
    }
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLogText() {
        return logText;
    }

    public void setLogText(String logText) {
        this.logText = logText;
    }

    public String getCompleteText() {
        return completeText;
    }

    public void setCompleteText(String completeText) {
        this.completeText = completeText;
    }

    public boolean isRepeatable() {
        return repeatable;
    }

    public void setRepeatable(boolean repeatable) {
        this.repeatable = repeatable;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getNextQuest() {
        return nextQuest;
    }

    public void setNextQuest(int nextQuest) {
        this.nextQuest = nextQuest;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setRequirements(QuestRequirement requirements) {
        this.requirements = requirements;
    }

    public QuestRequirement getRequirements() {
        return requirements;
    }

}
