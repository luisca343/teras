package es.boffmedia.teras.quests.model;

import java.util.List;

/**
 * One quest's <b>per-player</b> half: what changes as a player plays. Port of the 1.16.5
 * {@code QuestDataBase}, and the exact body of {@code GET /quests/user/{uuid}}.
 *
 * <p>This is deliberately the base class of {@link QuestInfo}, mirroring the 1.16.5
 * {@code QuestData extends QuestDataBase} split, because the split <b>is</b> the wire contract:</p>
 *
 * <ul>
 *   <li>{@code /quests/user/{uuid}} serializes a {@code QuestProgress}, so the definition fields
 *       don't exist on the object and Gson can't emit them;</li>
 *   <li>{@code /quests/all} serializes a {@link QuestInfo}, whose progress fields are left null and
 *       are therefore omitted by Gson (except {@code dialogId}, an {@code int}, which 1.16.5 also
 *       emitted as {@code 0} there).</li>
 * </ul>
 *
 * <p>Flattening these into one class with nullable fields would break that: {@code repeatable} is a
 * primitive {@code boolean} and would start appearing as {@code false} on the user route, which
 * 1.16.5 never sent. The SmartRotom backend merges the two halves
 * ({@code {...systemQuest, ...userQuest}}), so a stray field on the wrong half silently overwrites
 * the good one.</p>
 */
public class QuestProgress {
    protected int id;
    protected QuestStatus status;
    protected List<QuestObjective> objectives;
    protected List<QuestReward> rewards;
    protected int dialogId;
    protected String npcName;

    protected QuestProgress() {}

    public static QuestProgress of(int id, QuestStatus status) {
        QuestProgress progress = new QuestProgress();
        progress.id = id;
        progress.status = status;
        return progress;
    }

    public int getId() { return id; }

    public QuestStatus getStatus() { return status; }
    public void setStatus(QuestStatus status) { this.status = status; }

    public List<QuestObjective> getObjectives() { return objectives; }
    public void setObjectives(List<QuestObjective> objectives) { this.objectives = objectives; }

    public List<QuestReward> getRewards() { return rewards; }
    public void setRewards(List<QuestReward> rewards) { this.rewards = rewards; }

    public int getDialogId() { return dialogId; }
    public void setDialogId(int dialogId) { this.dialogId = dialogId; }

    public String getNpcName() { return npcName; }
    public void setNpcName(String npcName) { this.npcName = npcName; }
}
