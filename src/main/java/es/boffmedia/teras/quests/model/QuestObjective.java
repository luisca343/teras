package es.boffmedia.teras.quests.model;

/** One objective line of a quest with the player's progress. Ported 1:1 from 1.16.5. */
public class QuestObjective {
    private final String name;
    private final int progress;
    private final int total;

    public QuestObjective(String name, int progress, int total) {
        this.name = name;
        this.progress = progress;
        this.total = total;
    }

    public String getName() { return name; }
    public int getProgress() { return progress; }
    public int getTotal() { return total; }
}
