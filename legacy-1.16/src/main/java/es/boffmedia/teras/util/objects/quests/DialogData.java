package es.boffmedia.teras.util.objects.quests;

import es.boffmedia.teras.Teras;
import noppes.npcs.api.handler.data.IDialog;
import noppes.npcs.controllers.data.Availability;

import java.util.ArrayList;
import java.util.List;

public class DialogData {
    private int id;
    private String name;
    private String text;
    private int questId;
    private QuestRequirement requirements;
    private List<NpcData> npcLocations = new ArrayList<>();

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
        }



        requirement.setTime(availability.getDaytime());
        requirement.setLevel(availability.getMinPlayerLevel());

        requirement.addFactionRequirement(availability.factionId, availability.factionAvailable, availability.factionStance);
        requirement.addFactionRequirement(availability.faction2Id, availability.faction2Available, availability.faction2Stance);


        requirement.addScoreboardRequirement(availability.scoreboardObjective, availability.scoreboardType, availability.scoreboardValue);
        requirement.addScoreboardRequirement(availability.scoreboard2Objective, availability.scoreboard2Type, availability.scoreboard2Value);

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

    public List<NpcData> getNpcLocations() {
        return npcLocations;
    }

    public void setNpcLocations(List<NpcData> npcLocations) {
        this.npcLocations = npcLocations;
    }
}
