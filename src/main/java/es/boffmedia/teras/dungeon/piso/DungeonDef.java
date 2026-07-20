package es.boffmedia.teras.dungeon.piso;

import java.util.ArrayList;
import java.util.List;

/**
 * A whole dungeon: an ordered list of tramos, and nothing else that decides how long it is.
 *
 * <p>Length is <b>emergent</b> — the sum of its tramos' {@code largo}. That is why
 * {@code GenConfig.finalStage} is deleted rather than made configurable: "the last floor" stopped
 * being a constant the moment two dungeons could differ, and a number declared in two places drifts.
 * </p>
 *
 * @param id     config key; folded into the run seed so two dungeons never generate the same floor
 * @param nombre what players are offered at the entrance
 * @param tramos in depth order, first tramo first
 */
public record DungeonDef(String id, String nombre, List<TierDef> tramos) {

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
    public List<String> problems(java.util.Map<String, FloorDef> catalog) {
        List<String> problems = new ArrayList<>();
        if (id == null || id.isBlank()) {
            problems.add("dungeon has no id");
        }
        if (tramos == null || tramos.isEmpty()) {
            problems.add("dungeon '" + id + "' has no tramos, so it has no floors");
            return problems;
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
