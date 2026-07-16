package es.boffmedia.teras.quests;

import com.google.gson.Gson;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.quests.model.MisionesResponse;
import es.boffmedia.teras.quests.model.QuestInfo;
import es.boffmedia.teras.quests.model.QuestStatus;
import net.minecraft.server.level.ServerPlayer;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.handler.data.IQuest;
import noppes.npcs.api.handler.data.IQuestCategory;
import noppes.npcs.api.wrapper.PlayerWrapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the {@code getMisiones} reply: every quest CustomNPCs knows about, carrying this player's
 * status, progress and rewards.
 *
 * <p><b>Not a port — a rewrite.</b> 1.16.5 never implemented the server half: its {@code getMisiones}
 * handler stashed the JS callback and sent nothing (the request packet had been unregistered), so the
 * quest viewer always hung. The response <i>shape</i> is preserved from the client-side
 * {@code CMessageVerMisiones}/{@code MisionesJugador} that would have decoded it, but the data is
 * assembled here, server-side, from the CustomNPCs API.</p>
 *
 * <p>Quest definitions come from the API; the offering NPC's position and skin come from
 * {@link MisionesStore}, which is the only place that mapping exists (the quest API doesn't know
 * which NPC offers a quest — only a dialog does).</p>
 *
 * <p>CustomNPCs-coupled: only reachable behind {@link QuestBridge#isAvailable()}.</p>
 */
public final class QuestService {
    private QuestService() {}

    private static final Gson GSON = new Gson();

    /** Serialized {@link MisionesResponse} for {@code player}; never throws. */
    public static String getMisionesJson(ServerPlayer player) {
        try {
            return GSON.toJson(build(player));
        } catch (Exception e) {
            Teras.LOGGER.error("Failed building getMisiones for {}",
                    player.getGameProfile().getName(), e);
            return GSON.toJson(new MisionesResponse(List.of(), Map.of()));
        }
    }

    private static MisionesResponse build(ServerPlayer player) {
        PlayerWrapper<?> wrapper = new PlayerWrapper<>(player);
        List<QuestInfo> misiones = new ArrayList<>();
        Map<String, Integer> categorias = new LinkedHashMap<>();
        Map<Integer, QuestInfo> known = MisionesStore.getQuests();

        for (IQuestCategory category : NpcAPI.Instance().getQuests().categories()) {
            List<IQuest> quests = category.quests();
            categorias.put(category.getName(), quests.size());
            for (IQuest quest : quests) {
                misiones.add(toInfo(quest, wrapper, known.get(quest.getId())));
            }
        }
        misiones.sort(Comparator.comparingInt(QuestInfo::getId));
        return new MisionesResponse(misiones, categorias);
    }

    /**
     * The quest's definition plus this player's state. {@code cached} is what we learned when a player
     * last opened the offering dialog; it carries the NPC name/position/skin, which the quest API has
     * no way to provide.
     */
    private static QuestInfo toInfo(IQuest quest, PlayerWrapper<?> wrapper, QuestInfo cached) {
        QuestInfo info = QuestBuilder.definition(quest, null);
        if (cached != null) {
            info.setNpcName(cached.getNpcName());
            info.setSkin(cached.getSkin());
            info.setX(cached.getX());
            info.setY(cached.getY());
            info.setZ(cached.getZ());
            info.setDialogId(cached.getDialogId());
        }
        info.setStatus(statusOf(quest.getId(), wrapper));
        info.setObjectives(QuestBuilder.objectives(quest, wrapper));
        info.setRewards(QuestBuilder.rewards(quest));
        return info;
    }

    /**
     * Maps CustomNPCs' player state onto {@link QuestStatus}. 1.16.5 declared this enum but never
     * populated it (nothing reached the server), so the mapping is defined here: finished beats
     * active, and a quest that can't be accepted yet is locked rather than merely not started.
     */
    private static QuestStatus statusOf(int questId, PlayerWrapper<?> wrapper) {
        if (wrapper.hasFinishedQuest(questId)) return QuestStatus.COMPLETED;
        if (wrapper.hasActiveQuest(questId)) return QuestStatus.ACTIVE;
        if (wrapper.canQuestBeAccepted(questId)) return QuestStatus.AVAILABLE;
        return QuestStatus.LOCKED;
    }
}
