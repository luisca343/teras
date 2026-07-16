package es.boffmedia.teras.quests.model;

/**
 * One quest's <b>definition</b> half (on top of the per-player {@link QuestProgress} it extends):
 * the player-independent data the SmartRotom backend caches for 4 hours. Port of the 1.16.5
 * {@code QuestData}.
 *
 * <p>Built two ways:</p>
 * <ul>
 *   <li><b>Catalog</b> ({@code GET /quests/all}) — {@link #definition} only: {@code status} is the
 *       {@code NOT_STARTED} stub and the progress fields stay null, exactly as 1.16.5 sent them. The
 *       backend fills those from {@code /quests/user/{uuid}}.</li>
 *   <li><b>Merged</b> (the mcef {@code getMisiones}) — definition <i>plus</i> the progress fields set,
 *       because single-player has no backend to do the merge.</li>
 * </ul>
 *
 * <p>Note there is no {@code skin}/{@code x}/{@code y}/{@code z} here: the giver's location lives on
 * {@link DialogInfo#getNpcLocations()}, which is where {@code QuestList} put it.</p>
 */
public class QuestInfo extends QuestProgress {
    private String name;
    private String logText;
    private String completeText;
    private boolean repeatable;
    private int type;
    private int nextQuest;
    private String category;
    private QuestRequirement requirements;

    private QuestInfo() {}

    /**
     * The catalog form: definition fields set, {@code status = NOT_STARTED}, progress fields null.
     * Matches {@code new QuestData(quest, dialog)} in 1.16.5.
     */
    public static QuestInfo definition(int id, String name, String logText, String completeText,
                                       boolean repeatable, int type, int nextQuest, String category,
                                       QuestRequirement requirements) {
        QuestInfo info = new QuestInfo();
        info.id = id;
        info.status = QuestStatus.NOT_STARTED;
        info.name = name;
        info.logText = logText;
        info.completeText = completeText;
        info.repeatable = repeatable;
        info.type = type;
        info.nextQuest = nextQuest;
        info.category = category;
        info.requirements = requirements;
        return info;
    }

    /** Copies this definition and overlays a player's progress — the merge the backend would do. */
    public QuestInfo mergedWith(QuestProgress progress) {
        QuestInfo merged = definition(id, name, logText, completeText, repeatable, type, nextQuest,
                category, requirements);
        if (progress != null) {
            merged.status = progress.getStatus();
            merged.objectives = progress.getObjectives();
            merged.rewards = progress.getRewards();
            merged.dialogId = progress.getDialogId();
            merged.npcName = progress.getNpcName();
        }
        return merged;
    }

    public String getName() { return name; }
    public String getLogText() { return logText; }
    public String getCompleteText() { return completeText; }
    public boolean isRepeatable() { return repeatable; }
    public int getType() { return type; }
    public int getNextQuest() { return nextQuest; }
    public String getCategory() { return category; }
    public QuestRequirement getRequirements() { return requirements; }
}
