package es.boffmedia.teras.util.game.dungeons;

public class DungeonBuilder {
    private int stageId;
    private boolean curseOfTheLabyrinth;
    private boolean curseOfTheLost;
    private String seed;
    private SeededRandom rng;

    public DungeonBuilder() {
        this.stageId = 1;
        this.curseOfTheLabyrinth = false;
        this.curseOfTheLost = false;
    }

    public DungeonBuilder stageId(int stageId) {
        if (!DungeonConfig.isValidStageId(stageId)) {
            throw new IllegalArgumentException("Invalid stage ID: " + stageId);
        }
        this.stageId = stageId;
        return this;
    }

    public DungeonBuilder withCurseOfTheLabyrinth() {
        this.curseOfTheLabyrinth = true;
        return this;
    }

    public DungeonBuilder withCurseOfTheLost() {
        this.curseOfTheLost = true;
        return this;
    }

    public DungeonBuilder seed(String seed) {
        this.seed = seed;
        return this;
    }

    public DungeonGenerator.DungeonResult build() {
        String generatedSeed = (seed != null) ? seed : String.valueOf(System.currentTimeMillis());
        String combinedSeed = stageId + "-" + generatedSeed;
        this.rng = new SeededRandom(combinedSeed);

        int requiredRooms = DungeonGenerator.calculateNumberOfRooms(
                stageId, curseOfTheLabyrinth, curseOfTheLost, rng);
        int requiredDeadEnds = DungeonGenerator.calculateMinDeadEnds(
                stageId, curseOfTheLabyrinth);

        Room[][] dungeon = NewRoomCarver.generateDungeonLayout(requiredRooms, requiredDeadEnds, rng);
        DungeonGenerator.placeSpecialRooms(dungeon, stageId, rng);

        return new DungeonGenerator.DungeonResult(dungeon, combinedSeed);
    }
}