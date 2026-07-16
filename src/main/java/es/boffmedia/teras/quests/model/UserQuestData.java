package es.boffmedia.teras.quests.model;

import java.util.List;
import java.util.Map;

/**
 * Catalog + a player's progress, already merged — the mcef {@code getMisiones} reply.
 *
 * <p>Mirrors the SmartRotom backend's own {@code UserQuestData}, which it produces by merging
 * {@code /quests/all} with {@code /quests/user/{uuid}}. Single-player has no backend to do that, so
 * the mod does it here; the board can then read one shape whether it's talking to the mod directly
 * (single-player) or to the backend (multiplayer).</p>
 *
 * <p>{@code quests} and {@code dialogs} are <b>arrays</b> here, matching the backend's output after
 * its {@code Object.values(...)} transform — not the maps of {@link QuestCatalog}.</p>
 *
 * <p><b>Deviation, deliberate:</b> {@code categories} stays the {@code {name: [questId]}} <i>map</i>.
 * The backend emits {@code Object.values(categories)} here, which discards the category names and
 * yields {@code [[1,2],[3]]} — unusable, and typed as something it isn't. The board's own {@code Region}
 * type documents wanting the map, so this sends the map. See docs/QUESTS.md.</p>
 */
public record UserQuestData(List<QuestInfo> quests,
                            Map<String, List<Integer>> categories,
                            List<DialogInfo> dialogs,
                            List<NpcData> npcs) {}
