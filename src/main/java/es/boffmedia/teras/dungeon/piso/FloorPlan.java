package es.boffmedia.teras.dungeon.piso;

import es.boffmedia.teras.dungeon.model.Curse;

import java.util.List;
import java.util.Set;

/**
 * What one floor of a run turned out to be: which place, how deep, how hard, and under which
 * curses. Everything downstream — generation, the title card, spawn tables, the boss pool — reads
 * this rather than re-deriving any of it, so a floor's identity is decided exactly once.
 *
 * @param stage       absolute floor number, 1-based. Still the wire and database field; the piso is
 *                    the <i>place</i>, this is the <i>position</i>
 * @param dungeonId   which dungeon, folded into the seed so two dungeons differ at the same stage
 * @param tierIndex   which tramo, 0-based
 * @param indexInTier how deep into that tramo, 0-based
 * @param piso        the place
 * @param dificultad  the tramo's multiplier, carried so nothing downstream needs the tramo
 * @param curses      rolled from the piso's accepted set, so a piso is never handed one it refuses
 * @param jefes       the boss pool this floor draws from, already resolved: the piso's override
 *                    when it has one, otherwise the tramo's. Resolving here rather than at the
 *                    spawn site is what keeps "a spider queen must not appear in plain Cuevas" a
 *                    property of the plan instead of a rule two callers have to remember
 * @param minijefes   the mini-boss pool, resolved the same way
 */
public record FloorPlan(int stage,
                        String dungeonId,
                        int tierIndex,
                        int indexInTier,
                        FloorDef piso,
                        double dificultad,
                        Set<Curse> curses,
                        List<String> jefes,
                        List<String> minijefes) {

    /**
     * A plan with no pools of its own, which falls back to the global {@code enemies.json} tables.
     * Kept for the callers that rebuild a plan to add a forced curse and for tests that are about
     * selection rather than about bosses.
     */
    public FloorPlan(int stage, String dungeonId, int tierIndex, int indexInTier, FloorDef piso,
                     double dificultad, Set<Curse> curses) {
        this(stage, dungeonId, tierIndex, indexInTier, piso, dificultad, curses,
                List.of(), List.of());
    }

    public FloorPlan {
        jefes = jefes == null ? List.of() : List.copyOf(jefes);
        minijefes = minijefes == null ? List.of() : List.copyOf(minijefes);
    }

    /** 1-based depth within the tramo — the numeral the title card shows. */
    public int depth() {
        return indexInTier + 1;
    }

    /** "Cuevas II" — the piso's name and its depth in the tramo, never the absolute floor. */
    public String title() {
        return piso.nombre() + " " + roman(depth());
    }

    public String subtitle() {
        return piso.subtitulo();
    }

    /**
     * Roman numerals only far enough to cover a tramo. A tramo spanning more than a handful of
     * floors is a design mistake rather than a case to support, so this degrades to the plain
     * number instead of growing a general algorithm.
     */
    private static String roman(int depth) {
        return switch (depth) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(depth);
        };
    }
}
