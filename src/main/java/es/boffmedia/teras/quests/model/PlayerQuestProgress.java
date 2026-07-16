package es.boffmedia.teras.quests.model;

import java.util.Map;

/**
 * A player's quest progress — the body of {@code GET /quests/user/{uuid}}. Port of the 1.16.5
 * {@code PlayerQuests}.
 *
 * <p>{@code quests} is {@code {questId: QuestProgress}}: the backend reads {@code response.data.quests}
 * (note: <b>no</b> {@code ApiResponse} envelope on this route, unlike {@code /quests/all} — a 1.16.5
 * inconsistency reproduced deliberately so the backend needs no change).</p>
 *
 * <p>{@code categories} here is {@code {questId: categoryName}} — the <i>dialog's</i> category, which
 * is a different mapping from {@link QuestCatalog#categories()}'s {@code {categoryName: [questId]}}.
 * That asymmetry is 1.16.5's; the backend ignores this field entirely (it only reads {@code quests}),
 * so it is kept purely for wire fidelity.</p>
 */
public record PlayerQuestProgress(Map<Integer, QuestProgress> quests,
                                  Map<Integer, String> categories) {}
