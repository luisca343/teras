package es.boffmedia.teras.quests.model;

/** One item reward of a quest ({@code item} is a registry name). Ported 1:1 from 1.16.5. */
public class QuestReward {
    private final String item;
    private final int count;

    public QuestReward(String item, int count) {
        this.item = item;
        this.count = count;
    }

    public String getItem() { return item; }
    public int getCount() { return count; }
}
