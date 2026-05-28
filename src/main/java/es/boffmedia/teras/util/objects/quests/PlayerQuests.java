package es.boffmedia.teras.util.objects.quests;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.FakePlayer;
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

    public PlayerQuests(UUID uuid) {
        NpcAPI npcApi = NpcAPI.Instance();
        quests = new HashMap<>();
        categories = new HashMap<>();

        PlayerWrapper wrapper = buildWrapper(uuid);

        IQuest[] activas = wrapper.getActiveQuests();
        IQuest[] completadas = wrapper.getFinishedQuests();

        Set<Integer> activeIds = new HashSet<>();
        for (IQuest q : activas) activeIds.add(q.getId());
        Set<Integer> completedIds = new HashSet<>();
        for (IQuest q : completadas) completedIds.add(q.getId());

        IDialogHandler dialogHandler = npcApi.getDialogs();
        dialogHandler.categories().forEach(category -> {
            category.dialogs().forEach(dialog -> {
                if (dialog.getQuest() != null) {
                    addPlayerQuest(dialog.getQuest(), wrapper, dialog, activeIds, completedIds, category.getName());
                }
            });
        });
    }

    // Builds a PlayerWrapper for online or offline players.
    // For offline players, a FakePlayer is created and loaded from disk so that
    // CustomNPCs can read saved quest progress without a live connection.
    private PlayerWrapper buildWrapper(UUID uuid) {
        ServerPlayerEntity online = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(uuid);
        if (online != null) {
            return new PlayerWrapper(online);
        }
        ServerWorld world = ServerLifecycleHooks.getCurrentServer().overworld();
        FakePlayer fake = new FakePlayer(world, new GameProfile(uuid, ""));
        ServerLifecycleHooks.getCurrentServer().getPlayerList().load(fake);
        return new PlayerWrapper(fake);
    }

    public void addPlayerQuest(IQuest quest, PlayerWrapper wrapper, IDialog dialog,
                               Set<Integer> activeIds, Set<Integer> completedIds, String categoryName) {
        QuestDataBase questData = new QuestDataBase(quest);
        questData.setDialogId(dialog.getId());
        questData.setNpcName(quest.getNpcName());

        boolean available;
        try {
            Availability availability = (Availability) dialog.getAvailability();
            available = availability.isAvailable(wrapper);
        } catch (Exception e) {
            available = false;
        }

        if (activeIds.contains(quest.getId())) {
            questData.setStatus(QuestStatus.ACTIVE);
        } else if (completedIds.contains(quest.getId())) {
            questData.setStatus(QuestStatus.COMPLETED);
        } else if (available) {
            questData.setStatus(QuestStatus.AVAILABLE);
        } else {
            questData.setStatus(QuestStatus.LOCKED);
        }

        questData.setObjectives(quest, wrapper);
        questData.setRewards(quest.getRewards().getItems());

        quests.put(questData.getId(), questData);
        categories.put(questData.getId(), categoryName);
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
