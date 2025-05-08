package es.boffmedia.teras.util.objects.quests;

public class NpcData {
    String name;
    int dialogId;
    String skin;

    public NpcData(String name, int dialogId, String skin) {
        this.name = name;
        this.dialogId = dialogId;
        this.skin = skin;
    }

    public String getName() {
        return name;
    }

    public int getDialogId() {
        return dialogId;
    }

    public String getSkin() {
        return skin;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDialogId(int dialogId) {
        this.dialogId = dialogId;
    }

    public void setSkin(String skin) {
        this.skin = skin;
    }
}
