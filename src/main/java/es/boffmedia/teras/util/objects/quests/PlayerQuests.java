package es.boffmedia.teras.util.objects.quests;

import es.boffmedia.teras.Teras;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.handler.IDialogHandler;
import noppes.npcs.api.handler.data.IDialog;
import noppes.npcs.api.handler.data.IQuest;
import noppes.npcs.api.wrapper.PlayerWrapper;
import noppes.npcs.controllers.data.Availability;

import java.util.*;

public class PlayerQuests {
    HashMap<Integer, QuestDataBase> quests;
    HashMap<Integer, String> categories;


    transient IQuest[] mActivas;
    transient IQuest[] mCompletadas;
    public PlayerQuests(UUID uuid){
        NpcAPI npcApi = NpcAPI.Instance();

        ServerPlayerEntity player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(uuid);
        PlayerWrapper wrapper = new PlayerWrapper(player);

        quests = new HashMap<>();
        categories = new HashMap<>();

        mActivas = wrapper.getActiveQuests();
        mCompletadas = wrapper.getFinishedQuests();
        IDialogHandler dialogHandler = npcApi.getDialogs();

        dialogHandler.categories().forEach(category -> {
            Teras.getLogger().info("Category: " + category.getName());
            category.dialogs().forEach(dialog -> {
                if(dialog.getQuest() !=null){
                    IQuest quest = dialog.getQuest();
                    addPlayerQuest(quest, wrapper, dialog);
                } else {
                    Teras.getLogger().info("Skipping Dialog: " + dialog.getText());
                }
            });
        });
    }

    public void addPlayerQuest(IQuest quest, PlayerWrapper wrapper, IDialog dialog) {
        QuestDataBase questData = new QuestDataBase(quest);
        questData.setDialogId(dialog.getId());

        Availability availability = (Availability) dialog.getAvailability();
        boolean available = availability.isAvailable(wrapper);

        if(Arrays.stream(mActivas).anyMatch(mActiva -> mActiva.getId() == quest.getId())) {
            questData.setStatus(QuestStatus.ACTIVE);
        } else if(Arrays.stream(mCompletadas).anyMatch(mCompleta -> mCompleta.getId() == quest.getId())) {
            questData.setStatus(QuestStatus.COMPLETED);
        } else if(available){
            questData.setStatus(QuestStatus.AVAILABLE);
        } else {
            questData.setStatus(QuestStatus.LOCKED);
        }

        questData.setObjectives(quest, wrapper);
        questData.setRewards(quest.getRewards().getItems());
        quests.put(questData.getId(), questData);


    }

    public HashMap<Integer, QuestDataBase> getQuests() {
        return quests;
    }

    public void setQuests(HashMap<Integer, QuestDataBase> quests) {
        this.quests = quests;
    }

    public HashMap<Integer, String> getCategories() {
        return categories;
    }

    public void setCategories(HashMap<Integer, String> categories) {
        this.categories = categories;
    }
}
