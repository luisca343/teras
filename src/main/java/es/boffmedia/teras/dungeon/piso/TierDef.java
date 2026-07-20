package es.boffmedia.teras.dungeon.piso;

import java.util.ArrayList;
import java.util.List;

/**
 * A <b>tramo</b>: the tier grouping the pisos that may fill a stretch of floors, and the one thing
 * they share. Never shown to a player — it exists so a boss can appear in either Cuevas or
 * Catacumbas without being declared twice, and so difficulty can be a property of depth rather than
 * of place.
 *
 * @param largo      how many floors this tramo spans. Tramos stack in order, so a dungeon's length
 *                   is the sum of its tramos' {@code largo} and no floor count is declared anywhere
 * @param dificultad multiplier the pisos' relative tables are scaled by. It must <b>not</b> be
 *                   applied as one flat multiply to count, health and damage together: at 1.8 that
 *                   compounds to roughly 5.8× threat before the roster shift toward elites is even
 *                   counted. Each axis needs its own curve
 * @param pisos      the weighted alternatives, rolled per floor
 * @param jefes      boss pool, unless a piso overrides it
 * @param minijefes  mini-boss pool, unless a piso overrides it
 */
public record TierDef(int largo,
                      double dificultad,
                      List<WeightedRef> pisos,
                      List<String> jefes,
                      List<String> minijefes) {

    public List<String> problems(int index) {
        List<String> problems = new ArrayList<>();
        String where = "tramo " + index;
        if (largo < 1) {
            problems.add(where + " has largo " + largo + "; a tramo must span at least one floor");
        }
        if (dificultad <= 0) {
            problems.add(where + " has dificultad " + dificultad + ", which must be positive");
        }
        if (pisos == null || pisos.isEmpty()) {
            problems.add(where + " offers no pisos, so its floors have nothing to be");
        }
        if (jefes == null || jefes.isEmpty()) {
            problems.add(where + " has no boss pool and no piso can inherit one");
        }
        return problems;
    }
}
