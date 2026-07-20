package es.boffmedia.teras.dungeon.piso;

/** A catalog id with the weight it is drawn at. */
public record WeightedRef(String id, int weight) {

    public WeightedRef {
        weight = Math.max(1, weight);
    }
}
