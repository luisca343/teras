package es.boffmedia.teras.dungeon.gen;

/**
 * Which floor of the canonical sequence this is. Not which floor of the current <i>run</i> — the
 * generator is never told that, and the distinction is the whole point of this type.
 *
 * <p>A dungeon is a <b>window</b> onto one canonical sequence of {@code canonicalFloors} floors: it
 * declares where it opens ({@code primerPiso}) and how many floors it spans, and its stage
 * {@code n} is canonical floor {@code primerPiso + n - 1}. Everything generation decides —
 * the cell budget, whether the shop is withheld, the seed — reads that absolute number, so floor 10
 * builds the same whether it was reached at stage 10 of the full descent or opened directly by a
 * one-floor challenge.</p>
 *
 * <p>The predecessor of this record carried the run-relative stage plus the dungeon's own length and
 * mapped one onto the other proportionally, so a two-floor dungeon's opening floor generated as
 * floor six and its second as the twelfth. Two floors are not a twelve-floor descent compressed;
 * they are floors one and two. Run position and floor identity are separate facts, and only the
 * second belongs here — which is why there is no {@code stage} field to reach for.</p>
 *
 * @param floor          1-based position in the canonical sequence
 * @param canonicalFloors how deep the canonical sequence goes, so {@link #isFinal()} has a meaning
 *                        that does not change with the window looking at it
 */
public record FloorDepth(int floor, int canonicalFloors) {

    public FloorDepth {
        if (floor < 1) {
            throw new IllegalArgumentException("floor is 1-based: " + floor);
        }
        if (canonicalFloors < 1) {
            throw new IllegalArgumentException("canonicalFloors must be positive: " + canonicalFloors);
        }
    }

    /** The canonical floor {@code floor}, against the sequence {@code config} describes. */
    public static FloorDepth of(GenConfig config, int floor) {
        return new FloorDepth(floor, config.canonicalFloors());
    }

    /**
     * The canonical floor a dungeon's stage lands on: a window opening at {@code primerPiso} maps
     * its stage 1 to that floor, its stage 2 to the next, and so on.
     */
    public static FloorDepth ofStage(GenConfig config, int primerPiso, int stage) {
        return new FloorDepth(primerPiso + stage - 1, config.canonicalFloors());
    }

    /**
     * The sequence's opening floor, where the party has no coins and no gear yet. What
     * {@code SpecialRoomPlacer} means when it withholds the challenge room, the arcade and the
     * devil deal, and boosts the mini-boss instead — the shop and the treasure are placed on every
     * floor, this one included. A property of floor one itself, so a challenge that opens deeper
     * does not inherit it.
     */
    public boolean isFirst() {
        return floor == 1;
    }

    /** The sequence's last floor. A run can end anywhere; only this floor is the finale. */
    public boolean isFinal() {
        return floor == canonicalFloors;
    }

    public boolean isValid() {
        return floor >= 1 && floor <= canonicalFloors;
    }
}
