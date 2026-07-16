package es.boffmedia.teras.quests.model;

import java.util.List;
import java.util.Map;

/**
 * The player-independent quest catalog — the body of {@code GET /quests/all}, and what the SmartRotom
 * backend caches for 4 hours. Port of the 1.16.5 {@code QuestList}.
 *
 * <p>All three are <b>maps</b>, not arrays: that's what the backend's {@code QuestRepository} reads
 * before doing {@code Object.values(...)} on each.</p>
 *
 * <ul>
 *   <li>{@code quests} — {@code {questId: QuestInfo}}, definitions only (no per-player progress).</li>
 *   <li>{@code categories} — {@code {categoryName: [questId]}}, the category → quest index.</li>
 *   <li>{@code dialogs} — {@code {dialogId: DialogInfo}}, carrying the dialog text.</li>
 * </ul>
 *
 * <p>There is deliberately no {@code npcs} key: 1.16.5 never sent one (the backend defaults it to
 * {@code []}), and givers ride on {@link DialogInfo#getNpcLocations()} instead.</p>
 */
public record QuestCatalog(Map<Integer, QuestInfo> quests,
                           Map<String, List<Integer>> categories,
                           Map<Integer, DialogInfo> dialogs) {}
