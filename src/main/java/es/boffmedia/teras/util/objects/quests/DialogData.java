package es.boffmedia.teras.util.objects.quests;

import es.boffmedia.teras.Teras;
import noppes.npcs.api.handler.data.IDialog;
import noppes.npcs.controllers.data.Availability;

public class DialogData {
    private int id;
    private String name;
    private String text;
    private int questId;
    private QuestRequirement requirements;

    public DialogData(IDialog dialog) {
        Teras.getLogger().info("Creating Dialog: " + dialog.getText());
        this.id = dialog.getId();
        this.text = dialog.getText();
        this.questId = dialog.getQuest() != null ? dialog.getQuest().getId() : -1;
        this.name = dialog.getName();


        QuestRequirement requirement = new QuestRequirement();


        Availability availability = (Availability) dialog.getAvailability();
        for (int i = 0; i < 4; i++) {
            requirement.addQuest(availability.getQuest(i));
            requirement.addDialog(availability.getDialog(i));
            Teras.getLogger().info("= = = Availability = = =");
            Teras.getLogger().info("Quest: " + availability.getQuest(i));
            Teras.getLogger().info("Dialog: " + availability.getDialog(i));
        }



        requirement.setTime(availability.getDaytime());
        requirement.setLevel(availability.getMinPlayerLevel());

        Teras.getLogger().info("Time: " + availability.getDaytime());
        Teras.getLogger().info("Level: " + availability.getMinPlayerLevel());

        requirement.addFactionRequirement(availability.factionId, availability.factionAvailable, availability.factionStance);
        requirement.addFactionRequirement(availability.faction2Id, availability.faction2Available, availability.faction2Stance);

        Teras.getLogger().info("Faction: " + availability.factionId + " " + availability.factionAvailable + " " + availability.factionStance);
        Teras.getLogger().info("Faction2: " + availability.faction2Id + " " + availability.faction2Available + " " + availability.faction2Stance);

        requirement.addScoreboardRequirement(availability.scoreboardObjective, availability.scoreboardType, availability.scoreboardValue);
        requirement.addScoreboardRequirement(availability.scoreboard2Objective, availability.scoreboard2Type, availability.scoreboard2Value);
        Teras.getLogger().info("Scoreboard: " + availability.scoreboardObjective + " " + availability.scoreboardType + " " + availability.scoreboardValue);
        Teras.getLogger().info("Scoreboard2: " + availability.scoreboard2Objective + " " + availability.scoreboard2Type + " " + availability.scoreboard2Value);

        this.requirements = requirement;

    }

    public int getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public int getQuestId() {
        return questId;
    }

    public QuestRequirement getRequirements() {
        return requirements;
    }

    public void setRequirements(QuestRequirement requirements) {
        this.requirements = requirements;
    }
}
