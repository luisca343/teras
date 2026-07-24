package es.boffmedia.teras.dungeon.piso;

import es.boffmedia.teras.dungeon.model.DungeonSeeds;

/**
 * The run-scoped half of variant selection: one shuffled bag per room key that keeps dealing
 * <i>across</i> the floors of a run instead of restarting on each one.
 *
 * <h2>The gap this closes</h2>
 *
 * <p>{@link VariantBag} removed clustering <i>within</i> a floor, and that is the whole of what it
 * was asked to do. But its bag is shuffled from the floor's own {@code baseSeed}, which folds in the
 * stage — so two floors deal from two unrelated shuffles, and a key that appears exactly once per
 * floor ({@code start}, {@code boss}, {@code exit}) is back to an independent roll every time. With
 * three variants and the two-floor Cripta that is the same start room on both floors a third of the
 * time, which is precisely the impression authoring variants exists to dispel — and it would have
 * been invisible while every one of those keys had a single template.</p>
 *
 * <p>So the once-per-floor keys draw from here instead: a seed with <b>no stage in it</b>, and an
 * ordinal that counts the floors of this piso the run has already built. Floor 1 takes position 0
 * of the cycle and floor 2 position 1 of the <i>same</i> cycle, so within one cycle a repeat is not
 * unlikely, it is impossible.</p>
 *
 * <p>Keys that appear several times on one floor are left on the floor-local bag: their clustering
 * is a within-floor problem, the floor bag already solves it, and a run-long ordinal would make the
 * variant a room gets depend on how many rooms every earlier floor happened to have.</p>
 *
 * @param seed        the run's seed, stage-free, so every floor of the run shuffles identically
 * @param pisoOrdinal how many floors of <i>this piso</i> the run built before this one — the
 *                    position this floor deals from. Per piso rather than per run because an
 *                    alternating pool would otherwise skip positions: two Cuevas floors either side
 *                    of an Infestadas one are the first and second Cuevas floors, not the first and
 *                    the third
 */
public record VariantDraw(long seed, int pisoOrdinal) {

    /**
     * The draw for a floor built outside a run — {@code /teras dungeon generar}, the room editor,
     * every test. Position 0 of the run bag, which is the same thing a run's first floor gets.
     */
    public static VariantDraw single(String seedString) {
        return new VariantDraw(DungeonSeeds.fnv1a64(seedString), 0);
    }
}
