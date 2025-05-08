package es.boffmedia.teras.util.objects.quests;

import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.handler.IQuestHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class QuestListBak {
    ArrayList<QuestData> questDataList;
    Map<Integer, String> categories;

    public QuestListBak() {
        NpcAPI npcApi = NpcAPI.Instance();
        IQuestHandler quests = npcApi.getQuests();

        questDataList = new ArrayList<>();
        categories = new HashMap<>();


        /*
        quests.categories().forEach(category -> {
            System.out.println(category.getName());
            category.quests().forEach(quest -> {
                QuestData questData = new QuestData(quest, dialog);
                questDataList.add(questData);
                categories.put(questData.getId(), questData.getCategory());
            });
        });*/

    }
}
