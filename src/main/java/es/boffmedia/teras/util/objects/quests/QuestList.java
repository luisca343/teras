package es.boffmedia.teras.util.objects.quests;

import es.boffmedia.teras.Teras;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.handler.IDialogHandler;
import noppes.npcs.api.handler.data.IQuest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuestList {
    Map<Integer, QuestData> quests;
    Map<String, ArrayList<Integer>> categories;
    Map<Integer, DialogData> dialogs;

    public QuestList() {
        NpcAPI npcApi = NpcAPI.Instance();

        quests = new HashMap<>();
        categories = new HashMap<>();
        dialogs = new HashMap<>();

        IDialogHandler dialogHandler = npcApi.getDialogs();
        dialogHandler.categories().forEach(category -> {
            category.dialogs().forEach(dialog -> {
                DialogData dialogData = new DialogData(dialog);
                dialogs.put(dialogData.getId(), dialogData);
                if (dialog.getQuest() != null) {
                    IQuest quest = dialog.getQuest();
                    QuestData questData = new QuestData(quest, dialog);
                    quests.put(questData.getId(), questData);
                    if (categories.containsKey(questData.getCategory())) {
                        categories.get(questData.getCategory()).add(questData.getId());
                    } else {
                        ArrayList<Integer> list = new ArrayList<>();
                        list.add(questData.getId());
                        categories.put(questData.getCategory(), list);
                    }
                } else {
                    Teras.getLogger().info("Skipping Dialog: " + dialog.getText());
                }
            });
        });

        // Enrich each dialog with its NPC locations from the persisted catalog.
        Map<Integer, List<NpcData>> npcCatalog = NpcCatalog.getCatalog();
        dialogs.forEach((id, dialogData) -> {
            List<NpcData> locations = npcCatalog.get(id);
            if (locations != null && !locations.isEmpty()) {
                dialogData.setNpcLocations(new ArrayList<>(locations));
            }
        });
    }
}
