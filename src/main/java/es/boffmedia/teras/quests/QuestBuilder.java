package es.boffmedia.teras.quests;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.quests.model.QuestInfo;
import es.boffmedia.teras.quests.model.QuestObjective;
import es.boffmedia.teras.quests.model.QuestRequirement;
import es.boffmedia.teras.quests.model.QuestReward;
import noppes.npcs.api.IContainer;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.api.handler.data.IDialog;
import noppes.npcs.api.handler.data.IQuest;
import noppes.npcs.api.handler.data.IQuestObjective;
import noppes.npcs.api.item.IItemStack;
import noppes.npcs.controllers.data.Availability;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates CustomNPCs' {@code IQuest}/{@code IDialog}/{@code ICustomNpc} into the CustomNPCs-free
 * {@link es.boffmedia.teras.quests.model} DTOs. This is the only place the two worlds meet, which is
 * what lets the model package load on a server without CustomNPCs.
 *
 * <p>Merges the 1.16.5 {@code QuestDataBase} + both {@code QuestData} constructors.</p>
 */
public final class QuestBuilder {
    private QuestBuilder() {}

    /** How many quest/dialog prerequisite slots a CustomNPCs {@code Availability} carries. */
    private static final int AVAILABILITY_SLOTS = 4;

    /** The quest's definition plus the NPC that offers it — the shape cached in {@code misiones.json}. */
    public static QuestInfo definition(IQuest quest, ICustomNpc<?> npc) {
        QuestInfo info = new QuestInfo();
        info.setId(quest.getId());
        info.setName(quest.getName());
        info.setLogText(quest.getLogText());
        info.setCompleteText(quest.getCompleteText());
        info.setRepeatable(quest.getIsRepeatable());
        info.setType(quest.getType());
        info.setNextQuest(quest.getNextQuest() != null ? quest.getNextQuest().getId() : -1);
        info.setCategory(quest.getCategory() != null ? quest.getCategory().getName() : "");

        if (npc != null) {
            info.setNpcName(npc.getDisplay().getName());
            info.setX(npc.getX());
            info.setY(npc.getY());
            info.setZ(npc.getZ());
            info.setSkin(NpcCatalog.extractTextureName(npc.getDisplay().getSkinTexture()));
        }
        return info;
    }

    /**
     * The gates on {@code dialog}, read off its {@code Availability}. {@code IAvailability} exposes
     * none of these, so this casts to the concrete {@code Availability} exactly as 1.16.5 did.
     */
    public static QuestRequirement requirements(IDialog dialog) {
        QuestRequirement req = new QuestRequirement();
        if (!(dialog.getAvailability() instanceof Availability availability)) return req;

        for (int i = 0; i < AVAILABILITY_SLOTS; i++) {
            req.addQuest(availability.getQuest(i));
            req.addDialog(availability.getDialog(i));
        }
        req.setRequiredTime(availability.getDaytime());
        req.setRequiredLevel(availability.getMinPlayerLevel());

        req.addFactionRequirement(availability.factionId,
                name(availability.factionAvailable), name(availability.factionStance));
        req.addFactionRequirement(availability.faction2Id,
                name(availability.faction2Available), name(availability.faction2Stance));

        req.addScoreboardRequirement(availability.scoreboardObjective,
                name(availability.scoreboardType), availability.scoreboardValue);
        req.addScoreboardRequirement(availability.scoreboard2Objective,
                name(availability.scoreboard2Type), availability.scoreboard2Value);
        return req;
    }

    /**
     * The player's progress on each objective. Reading objectives throws inside CustomNPCs when the
     * player hasn't started the quest, so that case yields an empty list rather than propagating —
     * matching 1.16.5, which swallowed it too.
     */
    public static List<QuestObjective> objectives(IQuest quest, IPlayer<?> player) {
        List<QuestObjective> result = new ArrayList<>();
        try {
            IQuestObjective[] objectives = quest.getObjectives(player);
            if (objectives == null) return result;
            for (IQuestObjective objective : objectives) {
                result.add(new QuestObjective(objective.getText(), objective.getProgress(),
                        objective.getMaxProgress()));
            }
        } catch (Exception e) {
            Teras.LOGGER.debug("Quest {} has no objectives for this player yet ({})",
                    quest.getId(), e.toString());
        }
        return result;
    }

    /**
     * The quest's item rewards. {@code IQuest.getRewards()} returns an {@code IContainer} on 1.21.1
     * (it was an {@code IItemStack[]} on 1.16.5); empty slots come back as air and are skipped.
     */
    public static List<QuestReward> rewards(IQuest quest) {
        List<QuestReward> result = new ArrayList<>();
        IContainer container = quest.getRewards();
        if (container == null) return result;
        IItemStack[] items = container.getItems();
        if (items == null) return result;
        for (IItemStack item : items) {
            if (item == null || item.isEmpty() || "minecraft:air".equals(item.getName())) continue;
            result.add(new QuestReward(item.getName(), item.getStackSize()));
        }
        return result;
    }

    private static String name(Enum<?> value) {
        return value != null ? value.name() : null;
    }
}
