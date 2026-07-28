package es.boffmedia.teras.dungeon.piso;

import java.util.ArrayList;
import java.util.List;

/**
 * A whole dungeon: a <b>window</b> onto the canonical floor sequence. It declares where it opens
 * ({@code primerPiso}) and, through its tramos, how many floors it spans.
 *
 * <p>Length is <b>emergent</b> — the sum of its tramos' {@code largo} — but length does not decide
 * how a floor generates. That is the distinction the two numbers here exist to keep: a
 * <i>stage</i> is a position in this run, a <i>floor</i> is a position in the sequence, and only
 * the second reaches the generator. A one-tramo dungeon at {@code primerPiso 10} is the tenth floor
 * of the descent played on its own, not a first floor; a two-floor dungeon at {@code primerPiso 1}
 * is floors one and two, not a twelve-floor descent compressed into two.</p>
 *
 * @param id         config key; folded into the run seed so two dungeons never generate the same floor
 * @param nombre     what players are offered at the entrance
 * @param primerPiso the canonical floor this dungeon's stage 1 lands on. 1 for a dungeon played
 *                   from the top; higher for a challenge that starts partway down
 * @param tramos     in depth order, first tramo first
 */
public record DungeonDef(String id, String nombre, int primerPiso, List<TierDef> tramos) {

    /** Where a floor sits: which tramo owns it, and how deep into that tramo it is. */
    public record Position(int tierIndex, TierDef tier, int indexInTier) {
        /** 1-based depth within the tramo — the numeral on the title card. */
        public int depth() {
            return indexInTier + 1;
        }
    }

    /** Total floors, from the tramos alone. */
    public int length() {
        int total = 0;
        for (TierDef tier : tramos) {
            total += Math.max(1, tier.largo());
        }
        return total;
    }

    public boolean isValidStage(int stage) {
        return stage >= 1 && stage <= length();
    }

    /**
     * The stage a tramo opens at, 1-based, or 0 when this dungeon has no such tramo.
     *
     * <p>The inverse of {@link #locate}, and the arithmetic the ascensor boards on: an unlock is
     * held as a tramo index because that is what a run <i>earns</i>, and a run has to be started at
     * a stage. Doing it here keeps the two ends of that conversion in one place, beside the length
     * they are both derived from.</p>
     */
    public int firstStageOf(int tramoIndex) {
        if (tramoIndex < 0 || tramoIndex >= tramos.size()) {
            return 0;
        }
        int stage = 1;
        for (int i = 0; i < tramoIndex; i++) {
            stage += Math.max(1, tramos.get(i).largo());
        }
        return stage;
    }

    /** Whether a tramo exists at this index — i.e. whether an unlock of it means anything. */
    public boolean hasTramo(int tramoIndex) {
        return tramoIndex >= 0 && tramoIndex < tramos.size();
    }

    /**
     * The canonical floor a stage of this dungeon lands on. The one place run position becomes
     * floor identity — everything generation-side takes the result, never the stage.
     */
    public int floorFor(int stage) {
        return Math.max(1, primerPiso) + stage - 1;
    }

    /** The deepest canonical floor this window reaches. */
    public int lastFloor() {
        return floorFor(length());
    }

    /**
     * The tramo covering {@code stage} (1-based), or null when the stage is past the end. Walks and
     * accumulates rather than indexing, because tramos have different spans — the whole point of
     * {@code largo}.
     */
    public Position locate(int stage) {
        if (stage < 1) {
            return null;
        }
        int floor = 1;
        for (int index = 0; index < tramos.size(); index++) {
            TierDef tier = tramos.get(index);
            int span = Math.max(1, tier.largo());
            if (stage < floor + span) {
                return new Position(index, tier, stage - floor);
            }
            floor += span;
        }
        return null;
    }

    /** Whether {@code stage} is this dungeon's last floor — replaces the old {@code finalStage}. */
    public boolean isFinalStage(int stage) {
        return stage == length();
    }

    /**
     * Why this dungeon cannot be run, or empty when it can. Every piso it names must exist in the
     * catalog and be usable itself; a dungeon referencing a broken piso is broken, because
     * selection has no fallback to reach for.
     */
    public List<String> problems(java.util.Map<String, FloorDef> catalog, int canonicalFloors) {
        List<String> problems = new ArrayList<>();
        if (id == null || id.isBlank()) {
            problems.add("dungeon has no id");
        }
        if (primerPiso < 1) {
            problems.add("dungeon '" + id + "' opens on floor " + primerPiso
                    + "; the sequence is 1-based");
        }
        if (tramos == null || tramos.isEmpty()) {
            problems.add("dungeon '" + id + "' has no tramos, so it has no floors");
            return problems;
        }
        // Caught here rather than clamped in the curve lookup: a window reaching past the sequence
        // is an authoring mistake, and silently building floor 12 four times over would look like
        // it worked.
        if (lastFloor() > canonicalFloors) {
            problems.add("dungeon '" + id + "' spans floors " + Math.max(1, primerPiso) + "-"
                    + lastFloor() + ", past the " + canonicalFloors
                    + " the curve declares; shorten it or extend 'celdas'");
        }
        for (int index = 0; index < tramos.size(); index++) {
            TierDef tier = tramos.get(index);
            problems.addAll(tier.problems(index));
            if (tier.pisos() == null) {
                continue;
            }
            for (WeightedRef ref : tier.pisos()) {
                FloorDef piso = catalog.get(ref.id());
                if (piso == null) {
                    problems.add("tramo " + index + " names piso '" + ref.id()
                            + "', which is not in the catalog");
                    continue;
                }
                for (String problem : piso.problems()) {
                    problems.add("tramo " + index + " -> " + problem);
                }
            }
        }
        return problems;
    }
}
