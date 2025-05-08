package es.boffmedia.teras.util.objects.quests;

public class QuestReward {
    private String item;
    private int count;

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public QuestReward(String item, int count) {
        this.item = item;
        this.count = count;
    }
}
