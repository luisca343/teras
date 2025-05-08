package es.boffmedia.teras.util.objects.quests;

import es.boffmedia.teras.util.objects.post.SmartRotomPost;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.constants.EntitiesType;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.handler.data.IDialog;
import noppes.npcs.controllers.data.DialogOption;

import java.util.HashMap;

public class UpdateNPCs extends SmartRotomPost {
    HashMap<Integer, NpcData> npcs;

    public UpdateNPCs(HashMap<Integer, NpcData> npcs) {
        super();
        this.npcs = npcs;
    }
    public UpdateNPCs(IWorld world) {
        super();
        npcs = new HashMap<>();
        for (IEntity allEntity : world.getAllEntities(EntitiesType.NPC)) {
            ICustomNpc npc = (ICustomNpc) allEntity;

            for (int i = 0; i < 12; i++) {
                IDialog dialog = npc.getDialog(i);
                if(dialog != null) {
                    String texture = npc.getDisplay().getSkinTexture();
                    String textureName = texture.contains("pixelmon") ? texture.split("pixelmon:textures/steve/")[1] : texture.split("customnpcs:textures/entity/humanmale/")[1];
                    NpcData npcData = new NpcData(npc.getDisplay().getName(), dialog.getId(), textureName);
                    npcs.put(dialog.getId(), npcData);

                    dialog.getOptions().forEach(option -> {
                        if(option instanceof DialogOption){
                            DialogOption dialogOption = (DialogOption) option;
                            NpcData npcDataOption = new NpcData(npc.getDisplay().getName(), dialogOption.dialogId, textureName);
                            npcs.put(dialogOption.dialogId, npcDataOption);
                        }
                    });

                }
            }
        }
    }

    public UpdateNPCs() {
        super();
        Iterable<ServerWorld> worlds = ServerLifecycleHooks.getCurrentServer().getAllLevels();
        NpcAPI npcAPI = NpcAPI.Instance();
        npcs = new HashMap<>();
        for (ServerWorld w : worlds) {
            IWorld world = npcAPI.getIWorld(w);
            for (IEntity allEntity : world.getAllEntities(EntitiesType.NPC)) {
                ICustomNpc npc = (ICustomNpc) allEntity;

                for (int i = 0; i < 12; i++) {
                    IDialog dialog = npc.getDialog(i);
                    if(dialog != null) {
                        String texture = npc.getDisplay().getSkinTexture();
                        String textureName = texture.contains("pixelmon") ? texture.split("pixelmon:textures/steve/")[1] : texture.split("customnpcs:textures/entity/humanmale/")[1];
                        NpcData npcData = new NpcData(npc.getDisplay().getName(), dialog.getId(), textureName);
                        npcs.put(dialog.getId(), npcData);

                        dialog.getOptions().forEach(option -> {
                            if(option instanceof DialogOption){
                                DialogOption dialogOption = (DialogOption) option;
                                NpcData npcDataOption = new NpcData(npc.getDisplay().getName(), dialogOption.dialogId, textureName);
                                npcs.put(dialogOption.dialogId, npcDataOption);
                            }
                        });

                    }
                }
            }
        }
    }

    public HashMap<Integer, NpcData> getNpcs() {
        return npcs;
    }

    public void setNpcs(HashMap<Integer, NpcData> npcs) {
        this.npcs = npcs;
    }


}
