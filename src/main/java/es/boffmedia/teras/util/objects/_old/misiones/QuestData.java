package es.boffmedia.teras.util.objects._old.misiones;

import es.boffmedia.teras.util.objects.quests.QuestObjective;
import es.boffmedia.teras.util.objects.quests.QuestStatus;
import es.boffmedia.teras.util.objects.quests.QuestReward;

import java.util.ArrayList;



public class QuestData {
    private int id;
    private String name;
    private String skin;
    private double x;
    private double y;
    private double z;
    private String npcName;
    private String category;
    private int nextQuest;
    private int type;
    private String completeText;
    private String logText;
    private boolean repeatable;
    private ArrayList<QuestReward> rewards;
    private ArrayList<QuestObjective> objectives;
    private QuestStatus status;


    public QuestData() {
        this.rewards = new ArrayList<>();
        this.nextQuest = -1;
        this.type = 0;
        this.completeText = "";
        this.logText = "";
        this.repeatable = false;
        this.category = "";
        this.npcName = "";
        this.skin = "";
        this.x = 0;
        this.y = 0;
        this.z = 0;
        this.name = "";
        this.id = -1;

        /*
        this.activa = false;
        this.objetivos = new ArrayList<>();
        this.estado = "";
        */
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSkin() {
        return skin;
    }

    public void setSkin(String skin) {
        this.skin = skin;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public String getNpcName() {
        return npcName;
    }

    public void setNpcName(String npcName) {
        this.npcName = npcName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getNextQuest() {
        return nextQuest;
    }

    public void setNextQuest(int nextQuest) {
        this.nextQuest = nextQuest;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getCompleteText() {
        return completeText;
    }

    public void setCompleteText(String completeText) {
        this.completeText = completeText;
    }

    public String getLogText() {
        return logText;
    }

    public void setLogText(String logText) {
        this.logText = logText;
    }

    public boolean isRepeatable() {
        return repeatable;
    }

    public void setRepeatable(boolean repeatable) {
        this.repeatable = repeatable;
    }

    public ArrayList<QuestReward> getRewards() {
        return rewards;
    }

    public void setRewards(ArrayList<QuestReward> rewards) {
        this.rewards = rewards;
    }

    public ArrayList<QuestObjective> getObjectives() {
        return objectives;
    }

    public void setObjectives(ArrayList<QuestObjective> objectives) {
        this.objectives = objectives;
    }



    public QuestStatus getStatus() {
        return status;
    }

    public void setStatus(QuestStatus status) {
        this.status = status;
    }


    @Override
    public boolean equals(Object obj) {
        if(obj instanceof QuestData){
            QuestData quest = (QuestData) obj;
            return quest.getId() == this.getId()
                    && quest.getNpcName().equals(this.getNpcName())
                    && quest.getName().equals(this.getName())
                    && quest.getSkin().equals(this.getSkin())
                    && quest.getX() == this.getX()
                    && quest.getY() == this.getY()
                    && quest.getZ() == this.getZ()
                    && quest.getCategory().equals(this.getCategory())
                    && quest.getNextQuest() == this.getNextQuest()
                    && quest.getType() == this.getType()
                    && quest.getCompleteText().equals(this.getCompleteText())
                    && quest.getLogText().equals(this.getLogText())
                    && quest.isRepeatable() == this.isRepeatable();
        }
        return false;
    }
}
