package es.boffmedia.teras.util.objects.quests;

public class NpcData {
    String name;
    int dialogId;
    String skin;
    double x;
    double y;
    double z;
    String world;
    String uuid;

    public NpcData(String name, int dialogId, String skin, double x, double y, double z, String world, String uuid) {
        this.name = name;
        this.dialogId = dialogId;
        this.skin = skin;
        this.x = x;
        this.y = y;
        this.z = z;
        this.world = world;
        this.uuid = uuid;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getDialogId() { return dialogId; }
    public void setDialogId(int dialogId) { this.dialogId = dialogId; }

    public String getSkin() { return skin; }
    public void setSkin(String skin) { this.skin = skin; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }

    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
}
