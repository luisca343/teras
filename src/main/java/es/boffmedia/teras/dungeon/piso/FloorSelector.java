package es.boffmedia.teras.dungeon.piso;

import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.DungeonSeeds;
import es.boffmedia.teras.dungeon.model.SeededRng;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides what a floor is: which piso fills it, and under which curses. Pure and seeded — the same
 * dungeon, seed and stage always produce the same floor, which is the whole point of printing a
 * seed.
 *
 * <h2>The ordering that matters</h2>
 *
 * Curses and piso selection are mutually dependent, and the seed makes it circular:
 * {@link DungeonSeeds#baseSeed} already folds curses in, so a piso drawn <i>from</i> the base seed
 * cannot also be what decides which curses are legal. The order is therefore fixed:
 *
 * <ol>
 *   <li>roll the piso from a <b>curse-independent</b> seed — dungeon id, stage and seed string;</li>
 *   <li>roll curses from that piso's accepted set;</li>
 *   <li>only then derive the generation seed, now that both are known.</li>
 * </ol>
 *
 * <p>Rolling curses first and filtering pisos to those accepting them is the obvious alternative and
 * it is wrong twice over: it re-creates the circularity, and it can leave a floor with no eligible
 * piso at all.</p>
 */
public final class FloorSelector {
    private FloorSelector() {}

    /** Salt keeping the piso draw clear of every other draw made from the same run seed. */
    private static final long PISO_SALT = 0x7069736FL;
    private static final long CURSE_SALT = 0x6d616c64L;

    /**
     * The floor at {@code stage}, or null when the dungeon has no such floor.
     *
     * @param catalog      the shared piso catalog
     * @param curseChances how likely each curse is before the piso's preferences are applied; a
     *                     curse the piso refuses is skipped whatever its chance
     */
    public static FloorPlan select(DungeonDef dungeon, Map<String, FloorDef> catalog, int stage,
                                   String seedString, Map<Curse, Double> curseChances) {
        DungeonDef.Position position = dungeon.locate(stage);
        if (position == null) {
            return null;
        }
        List<FloorDef> options = usable(position.tier(), catalog);
        if (options.isEmpty()) {
            return null;
        }
        FloorDef piso = pick(options, position.tier(), catalog, dungeon.id(), stage, seedString);
        Set<Curse> curses = rollCurses(piso, dungeon.id(), stage, seedString, curseChances);
        return new FloorPlan(stage, dungeon.id(), position.tierIndex(), position.indexInTier(),
                piso, position.tier().dificultad(), curses);
    }

    /**
     * The generation seed for a plan. Separate from {@link DungeonSeeds#baseSeed} by the dungeon id:
     * without it, floor 3 of one dungeon and floor 3 of another generate an identical layout from
     * the same run seed — silent, and the kind of thing that survives to production.
     */
    public static long baseSeed(FloorPlan plan, String seedString) {
        return DungeonSeeds.baseSeed(plan.stage(), plan.curses(),
                plan.dungeonId() + '|' + seedString);
    }

    /**
     * The pisos of a tramo that are actually usable. A broken one is dropped here rather than
     * throwing, so one bad entry costs its own variety and not the whole run — but if a tramo has
     * nothing left, the caller gets null and can say so plainly.
     */
    private static List<FloorDef> usable(TierDef tier, Map<String, FloorDef> catalog) {
        List<FloorDef> options = new ArrayList<>();
        if (tier.pisos() == null) {
            return options;
        }
        for (WeightedRef ref : tier.pisos()) {
            FloorDef piso = catalog.get(ref.id());
            if (piso != null && piso.problems().isEmpty()) {
                options.add(piso);
            }
        }
        return options;
    }

    /**
     * Weighted draw over the usable pisos, from a seed that deliberately excludes curses. Weights
     * come from the tramo's refs; a piso dropped as broken takes its weight with it.
     */
    private static FloorDef pick(List<FloorDef> options, TierDef tier,
                                 Map<String, FloorDef> catalog, String dungeonId, int stage,
                                 String seedString) {
        if (options.size() == 1) {
            return options.get(0);
        }
        int total = 0;
        int[] weights = new int[options.size()];
        for (int i = 0; i < options.size(); i++) {
            weights[i] = weightOf(tier, options.get(i).id());
            total += weights[i];
        }
        SeededRng rng = new SeededRng(DungeonSeeds.derive(
                DungeonSeeds.fnv1a64(dungeonId + '|' + seedString), PISO_SALT + stage));
        int roll = rng.between(1, Math.max(1, total));
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll <= 0) {
                return options.get(i);
            }
        }
        return options.get(options.size() - 1);
    }

    private static int weightOf(TierDef tier, String pisoId) {
        for (WeightedRef ref : tier.pisos()) {
            if (ref.id().equals(pisoId)) {
                return ref.weight();
            }
        }
        return 1;
    }

    /**
     * Curses for this floor, drawn only from what the piso accepts. A curse every eligible piso
     * refuses simply cannot occur at that depth — which is the intended consequence, not a gap:
     * LABYRINTH on a piso with two shapes would sprawl to the room cap in one repeated footprint.
     *
     * <p>Each curse rolls independently, so a floor can carry several or none.</p>
     */
    private static Set<Curse> rollCurses(FloorDef piso, String dungeonId, int stage,
                                         String seedString, Map<Curse, Double> chances) {
        Set<Curse> curses = EnumSet.noneOf(Curse.class);
        if (chances == null || chances.isEmpty()) {
            return curses;
        }
        long seed = DungeonSeeds.derive(
                DungeonSeeds.fnv1a64(dungeonId + '|' + seedString), CURSE_SALT + stage);
        // One rng walked in the enum's fixed order, so adding a curse to the enum never reshuffles
        // the draws of the ones before it.
        SeededRng rng = new SeededRng(seed);
        for (Curse curse : Curse.values()) {
            double chance = chances.getOrDefault(curse, 0.0);
            boolean rolled = rng.chance(chance);
            if (rolled && piso.accepts(curse)) {
                curses.add(curse);
            }
        }
        return curses;
    }
}
