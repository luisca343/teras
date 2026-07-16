package es.boffmedia.teras.quests.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One CustomNPCs dialog: its **text** plus the gates on it and the NPCs that offer it. Port of the
 * 1.16.5 {@code DialogData}.
 *
 * <p>This is the class whose absence emptied "La Bitácora" — it carries the only dialog text the
 * SmartRotom board ever had.</p>
 *
 * <p>{@link #npcLocations} is the catalog's answer to "who says this, and where": name, skin and
 * coordinates per giver, filled from {@code NpcCatalog} keyed by this dialog's id (what
 * {@code QuestList} does on {@code origin/dev}). The wire contract has no top-level {@code npcs}
 * array — givers hang off their dialog.</p>
 */
public class DialogInfo {
    private final int id;
    private final String name;
    private final String text;
    private final int questId;
    private final QuestRequirement requirements;
    private List<NpcData> npcLocations = new ArrayList<>();

    public DialogInfo(int id, String name, String text, int questId, QuestRequirement requirements) {
        this.id = id;
        this.name = name;
        this.text = text;
        this.questId = questId;
        this.requirements = requirements;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getText() { return text; }
    public int getQuestId() { return questId; }
    public QuestRequirement getRequirements() { return requirements; }

    public List<NpcData> getNpcLocations() { return npcLocations; }
    public void setNpcLocations(List<NpcData> npcLocations) { this.npcLocations = npcLocations; }
}
