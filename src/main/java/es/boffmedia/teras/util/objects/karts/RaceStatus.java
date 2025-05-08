package es.boffmedia.teras.util.objects.karts;

public enum RaceStatus {
    WAITING_PLAYERS("WAITING_PLAYERS", 1),
    STARTING("STARTING",2),
    IN_PROGRESS("IN_PROGRESS",3),
    FINISHED("FINISHED",4);


    private int value;
    private String name;

    RaceStatus(String name, int value) {
        this.name = name;
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public String getName() {
        return name;
    }
}
