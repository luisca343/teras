package es.boffmedia.teras.quests.model;

/**
 * One NPC-dialog pairing in the catalog, ported 1:1 from the 1.16.5 {@code NpcData}. Serialized by
 * Gson both to {@code config/teras/npc_catalog.json} and to the SmartRotom API, so the field names
 * are the contract on both sides: {@code name, dialogId, skin, x, y, z, world, uuid}.
 */
public class NpcData {
    private String name;
    private int dialogId;
    private String skin;
    private double x;
    private double y;
    private double z;
    private String world;
    private String uuid;

    public NpcData(String name, int dialogId, String skin, double x, double y, double z,
                   String world, String uuid) {
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
    public int getDialogId() { return dialogId; }
    public String getSkin() { return skin; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public String getWorld() { return world; }
    public String getUuid() { return uuid; }
}
