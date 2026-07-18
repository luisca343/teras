package es.boffmedia.teras.quests;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.quests.model.DialogInfo;
import es.boffmedia.teras.quests.model.NpcData;
import es.boffmedia.teras.quests.model.PlayerQuestProgress;
import es.boffmedia.teras.quests.model.QuestCatalog;
import es.boffmedia.teras.quests.model.QuestInfo;
import es.boffmedia.teras.quests.model.QuestProgress;
import es.boffmedia.teras.quests.model.QuestStatus;
import es.boffmedia.teras.quests.model.UserQuestData;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.handler.data.IDialog;
import noppes.npcs.api.handler.data.IQuest;
import noppes.npcs.api.wrapper.PlayerWrapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Builds every quest payload the mod serves. Port of the 1.16.5 {@code QuestList} (catalog) and
 * {@code PlayerQuests} (progress), which the Wungill plugin called into on the old hybrid server.
 *
 * <h2>The two halves</h2>
 * The wire contract splits quest data in two, and the split is load-bearing:
 * <ul>
 *   <li>{@link #buildCatalog()} — player-independent definitions + dialog text. Expensive (walks every
 *       dialog), so the SmartRotom backend caches it for 4 hours.</li>
 *   <li>{@link #buildProgress(UUID)} — one player's live status/objectives/rewards. Never cached.</li>
 * </ul>
 * The backend merges them. Single-player has no backend, so {@link #buildMerged(UUID)} does it here.
 *
 * <h2>Quests are found through dialogs, not quest categories</h2>
 * Both builders walk {@code getDialogs().categories()} → {@code dialogs()} and pick up
 * {@code dialog.getQuest()}. That's not incidental: a quest's requirements come from the availability
 * of the <b>dialog that offers it</b>, and the dialog is the only link between a quest and its NPC.
 * Walking {@code getQuests().categories()} instead (as an earlier version of this class did) finds the
 * quests but no dialogs — which is exactly how the board lost every line of dialog text.
 *
 * <p>CustomNPCs-coupled: only reachable behind {@link QuestBridge#isAvailable()}.</p>
 */
public final class QuestService {
    private QuestService() {}

    // ---- Catalog: GET /quests/all ----

    /**
     * How long a built catalog is reused. Quest definitions change only when an admin edits them, and
     * both callers (the HTTP catalog route and every in-game board open) walk every dialog on the
     * server to build one. Well under the backend's own 4h cache, so an edit still shows up promptly.
     */
    private static final long CATALOG_TTL_MS = 60_000L;

    private static volatile QuestCatalog cachedCatalog;
    private static volatile long cachedCatalogExpiresAt;

    /**
     * Every quest definition and dialog on the server, from a short-lived cache
     * ({@link #CATALOG_TTL_MS}). Port of {@code QuestList}.
     *
     * <p>The returned catalog is shared between callers and must be treated as <b>read-only</b>;
     * {@link #buildMerged} copies the collections it hands on.</p>
     *
     * <p>Dialogs without a quest are still catalogued — they carry text the board shows — they just
     * contribute no quest.</p>
     */
    public static QuestCatalog buildCatalog() {
        QuestCatalog cached = cachedCatalog;
        if (cached != null && System.currentTimeMillis() < cachedCatalogExpiresAt) {
            return cached;
        }
        QuestCatalog built = buildCatalogUncached();
        cachedCatalog = built;
        cachedCatalogExpiresAt = System.currentTimeMillis() + CATALOG_TTL_MS;
        return built;
    }

    private static QuestCatalog buildCatalogUncached() {
        Map<Integer, QuestInfo> quests = new HashMap<>();
        Map<String, List<Integer>> categories = new HashMap<>();
        Map<Integer, DialogInfo> dialogs = new HashMap<>();

        NpcAPI.Instance().getDialogs().categories().forEach(category ->
                category.dialogs().forEach(dialog -> {
                    try {
                        dialogs.put(dialog.getId(), QuestBuilder.dialog(dialog));
                        IQuest quest = dialog.getQuest();
                        if (quest == null) return;

                        QuestInfo info = QuestBuilder.definition(quest, dialog);
                        quests.put(info.getId(), info);
                        categories.computeIfAbsent(info.getCategory(), k -> new ArrayList<>())
                                .add(info.getId());
                    } catch (Exception e) {
                        // One malformed dialog must not empty the whole board.
                        Teras.LOGGER.warn("Skipping dialog {} while building the quest catalog: {}",
                                dialog.getId(), e.toString());
                    }
                }));

        attachNpcLocations(dialogs);
        return new QuestCatalog(quests, categories, dialogs);
    }

    /**
     * Gives each dialog the NPCs that offer it, from the persisted {@link NpcCatalog} (keyed by dialog
     * id). This is the catalog's only reader — without it the scan would collect NPC positions that
     * nothing ever serves.
     */
    private static void attachNpcLocations(Map<Integer, DialogInfo> dialogs) {
        Map<Integer, List<NpcData>> catalog = NpcCatalog.getCatalog();
        dialogs.forEach((id, dialog) -> {
            List<NpcData> locations = catalog.get(id);
            if (locations != null && !locations.isEmpty()) {
                dialog.setNpcLocations(new ArrayList<>(locations));
            }
        });
    }

    // ---- Progress: GET /quests/user/{uuid} ----

    /**
     * One player's progress, or empty when the uuid has never played here. Port of
     * {@code PlayerQuests}; works for offline players (see {@link PlayerLookup}).
     */
    public static Optional<PlayerQuestProgress> buildProgress(UUID uuid) {
        Optional<PlayerWrapper<?>> maybeWrapper = PlayerLookup.wrapperFor(uuid);
        if (maybeWrapper.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(progressFor(maybeWrapper.get()));
    }

    private static PlayerQuestProgress progressFor(PlayerWrapper<?> wrapper) {
        Map<Integer, QuestProgress> quests = new HashMap<>();
        Map<Integer, String> categories = new HashMap<>();

        Set<Integer> activeIds = idsOf(wrapper.getActiveQuests());
        Set<Integer> completedIds = idsOf(wrapper.getFinishedQuests());

        NpcAPI.Instance().getDialogs().categories().forEach(category ->
                category.dialogs().forEach(dialog -> {
                    IQuest quest = dialog.getQuest();
                    if (quest == null) return;
                    try {
                        QuestProgress progress = progressFor(quest, dialog, wrapper, activeIds, completedIds);
                        quests.put(progress.getId(), progress);
                        // The DIALOG's category, not the quest's — 1.16.5's asymmetry, kept as-is.
                        categories.put(progress.getId(), category.getName());
                    } catch (Exception e) {
                        Teras.LOGGER.warn("Skipping quest {} while reading player progress: {}",
                                quest.getId(), e.toString());
                    }
                }));
        return new PlayerQuestProgress(quests, categories);
    }

    private static QuestProgress progressFor(IQuest quest, IDialog dialog, PlayerWrapper<?> wrapper,
                                             Set<Integer> activeIds, Set<Integer> completedIds) {
        QuestProgress progress = QuestProgress.of(quest.getId(),
                statusOf(quest.getId(), dialog, wrapper, activeIds, completedIds));
        progress.setDialogId(dialog.getId());
        progress.setNpcName(quest.getNpcName());
        progress.setObjectives(QuestBuilder.objectives(quest, wrapper));
        progress.setRewards(QuestBuilder.rewards(quest));
        return progress;
    }

    /**
     * 1.16.5's precedence, preserved exactly: <b>ACTIVE before COMPLETED</b> (a repeatable quest that's
     * been finished once and taken again reads as ACTIVE), then AVAILABLE if the dialog's availability
     * currently passes, else LOCKED.
     */
    private static QuestStatus statusOf(int questId, IDialog dialog, PlayerWrapper<?> wrapper,
                                        Set<Integer> activeIds, Set<Integer> completedIds) {
        if (activeIds.contains(questId)) return QuestStatus.ACTIVE;
        if (completedIds.contains(questId)) return QuestStatus.COMPLETED;
        if (QuestBuilder.isAvailable(dialog, wrapper)) return QuestStatus.AVAILABLE;
        return QuestStatus.LOCKED;
    }

    private static Set<Integer> idsOf(IQuest[] quests) {
        Set<Integer> ids = new HashSet<>();
        if (quests == null) return ids;
        for (IQuest quest : quests) {
            ids.add(quest.getId());
        }
        return ids;
    }

    // ---- Merged: the mcef getMisiones ----

    /**
     * Catalog + this player's progress, merged — what the backend would assemble, done in-process for
     * the in-game page. Quests sort by id (1.16.5's client sorted before rendering).
     *
     * <p>A player with no saved data still gets the catalog, with each quest at its
     * {@code NOT_STARTED} default: an empty board is a worse answer than an unstarted one.</p>
     */
    public static UserQuestData buildMerged(UUID uuid) {
        QuestCatalog catalog = buildCatalog();
        Map<Integer, QuestProgress> progress = buildProgress(uuid)
                .map(PlayerQuestProgress::quests)
                .orElseGet(Map::of);

        List<QuestInfo> quests = new ArrayList<>();
        catalog.quests().forEach((id, definition) -> quests.add(definition.mergedWith(progress.get(id))));
        quests.sort(Comparator.comparingInt(QuestInfo::getId));

        List<DialogInfo> dialogs = new ArrayList<>(catalog.dialogs().values());
        dialogs.sort(Comparator.comparingInt(DialogInfo::getId));

        // Category names are preserved as a map on purpose — see UserQuestData's javadoc.
        Map<String, List<Integer>> categories = new LinkedHashMap<>(catalog.categories());

        // No top-level npcs: givers ride on dialogs[].npcLocations. Sent empty for shape parity.
        return new UserQuestData(quests, categories, dialogs, List.of());
    }
}
