package es.boffmedia.teras.util.objects.quests;

import es.boffmedia.teras.Teras;
import noppes.npcs.api.handler.data.IQuest;
import noppes.npcs.api.handler.data.IQuestObjective;
import noppes.npcs.api.item.IItemStack;
import noppes.npcs.api.wrapper.PlayerWrapper;

import java.util.ArrayList;

public class QuestDataBase {
    private int id;
    private QuestStatus status;

    private ArrayList<QuestObjective> objectives;
    private ArrayList<QuestReward>  rewards;
    private int dialogId;

    public QuestDataBase(IQuest quest, boolean simplified) {
        this.id = quest.getId();
        this.status = QuestStatus.NOT_STARTED;
    }

    public QuestDataBase(IQuest quest){
        this.id = quest.getId();
        this.status = QuestStatus.NOT_STARTED;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public QuestStatus getStatus() {
        return status;
    }

    public void setStatus(QuestStatus status) {
        this.status = status;
    }

    public void setObjectives(IQuest finishedQuest, PlayerWrapper wrapper) {
        this.objectives = new ArrayList<>();
        try{
            IQuestObjective[] objectives = finishedQuest.getObjectives(wrapper);
            for (IQuestObjective objective : objectives) {
                QuestObjective questObjective = new QuestObjective(objective.getText(), objective.getProgress(), objective.getMaxProgress());
                this.objectives.add(questObjective);
            }
        } catch (Exception e) {
            Teras.getLogger().warn("Player has not started quest yet");
        }
    }

    public ArrayList<QuestReward> getRewards() {
        return rewards;
    }

    public void setRewards(ArrayList<QuestReward> rewards) {
        this.rewards = rewards;
    }

    public void setRewards(IItemStack[] items) {
        this.rewards = new ArrayList<>();
        for (IItemStack item : items) {
            if(item.getName().equals("minecraft:air")) continue;
            QuestReward questReward = new QuestReward(item.getName(), item.getStackSize());
            this.rewards.add(questReward);
        }
    }

    public ArrayList<QuestObjective> getObjectives() {
        return objectives;
    }

    public void setObjectives(ArrayList<QuestObjective> objectives) {
        this.objectives = objectives;
    }

    public int getDialogId() {
        return dialogId;
    }

    public void setDialogId(int dialogId) {
        this.dialogId = dialogId;
    }
}
