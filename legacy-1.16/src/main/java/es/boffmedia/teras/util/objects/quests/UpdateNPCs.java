package es.boffmedia.teras.util.objects.quests;

import es.boffmedia.teras.util.objects.post.SmartRotomPost;
import net.minecraft.entity.Entity;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.constants.EntitiesType;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.handler.data.IDialog;
import noppes.npcs.controllers.data.DialogOption;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class UpdateNPCs extends SmartRotomPost {
    HashMap<Integer, List<NpcData>> npcs;

    public UpdateNPCs(HashMap<Integer, List<NpcData>> npcs) {
        super();
        this.npcs = npcs;
    }

    // Single-world scan — builds a standalone map without touching the catalog.
    public UpdateNPCs(IWorld world) {
        super();
        npcs = new HashMap<>();
        String dimension;
        try {
            dimension = world.getDimension().getId().toString();
        } catch (Exception e) {
            dimension = "minecraft:overworld";
        }
        for (IEntity entity : world.getAllEntities(EntitiesType.NPC)) {
            processNpc((ICustomNpc) entity, dimension, npcs);
        }
    }

    // Full server scan — updates the persisted catalog with live data, then
    // returns the complete catalog (loaded + newly discovered) as the response.
    // This means unloaded NPCs from previous sessions are included in the result.
    public UpdateNPCs() {
        super();
        Iterable<ServerWorld> worlds = ServerLifecycleHooks.getCurrentServer().getAllLevels();
        NpcAPI npcAPI = NpcAPI.Instance();
        for (ServerWorld w : worlds) {
            String dimension = w.dimension().location().toString();
            IWorld world = npcAPI.getIWorld(w);
            for (IEntity entity : world.getAllEntities(EntitiesType.NPC)) {
                processNpcToCatalog((ICustomNpc) entity, dimension);
            }
        }
        NpcCatalog.saveIfDirty();
        npcs = new HashMap<>(NpcCatalog.getCatalog());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void processNpc(ICustomNpc npc, String dimension, HashMap<Integer, List<NpcData>> target) {
        String textureName = NpcCatalog.extractTextureName(npc.getDisplay().getSkinTexture());
        String uuid = resolveUuid(npc);
        for (int i = 0; i < 12; i++) {
            IDialog dialog = npc.getDialog(i);
            if (dialog == null) continue;
            NpcData data = buildData(npc, dialog.getId(), textureName, dimension, uuid);
            target.computeIfAbsent(dialog.getId(), k -> new ArrayList<>()).add(data);
            dialog.getOptions().forEach(option -> {
                if (option instanceof DialogOption) {
                    int linkedId = ((DialogOption) option).dialogId;
                    target.computeIfAbsent(linkedId, k -> new ArrayList<>())
                          .add(buildData(npc, linkedId, textureName, dimension, uuid));
                }
            });
        }
    }

    private static void processNpcToCatalog(ICustomNpc npc, String dimension) {
        String textureName = NpcCatalog.extractTextureName(npc.getDisplay().getSkinTexture());
        String uuid = resolveUuid(npc);
        for (int i = 0; i < 12; i++) {
            IDialog dialog = npc.getDialog(i);
            if (dialog == null) continue;
            NpcCatalog.update(dialog.getId(), buildData(npc, dialog.getId(), textureName, dimension, uuid));
            dialog.getOptions().forEach(option -> {
                if (option instanceof DialogOption) {
                    int linkedId = ((DialogOption) option).dialogId;
                    NpcCatalog.update(linkedId, buildData(npc, linkedId, textureName, dimension, uuid));
                }
            });
        }
    }

    private static NpcData buildData(ICustomNpc npc, int dialogId, String textureName,
                                      String dimension, String uuid) {
        return new NpcData(npc.getDisplay().getName(), dialogId, textureName,
                npc.getX(), npc.getY(), npc.getZ(), dimension, uuid);
    }

    private static String resolveUuid(ICustomNpc npc) {
        String uuid = npc.getUUID();
        if (uuid == null || uuid.isEmpty()) {
            uuid = ((Entity) npc.getMCEntity()).getStringUUID();
        }
        return uuid;
    }

    public HashMap<Integer, List<NpcData>> getNpcs() { return npcs; }
    public void setNpcs(HashMap<Integer, List<NpcData>> npcs) { this.npcs = npcs; }
}
