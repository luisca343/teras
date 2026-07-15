package es.boffmedia.teras.util.objects.quests;

public class QuestObjective {
    private String name;
    private int progress;
    private int total;

    public QuestObjective(String name, int progress, int total) {
        this.name = name;
        this.progress = progress;
        this.total = total;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }


    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }
}
