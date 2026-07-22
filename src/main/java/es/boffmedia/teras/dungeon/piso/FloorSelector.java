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
                piso, position.tier().dificultad(), curses,
                pool(piso.jefes(), position.tier().jefes()),
                miniPool(piso, position.tier()));
    }

    /**
     * The mini-boss pool: the piso's own if it declares one, <b>its own elites</b> if it does not,
     * and the tramo's only when the floor fields no elite at all.
     *
     * <p>The middle step is the one worth having. A tramo's pool is a statement about how hard the
     * tier is, not about what lives on the floor, so inheriting it directly put {@code
     * centinela_hueso} — a bone humanoid — in the mini-boss room of a spider nest, because
     * Infestadas never declared a pool of its own. An elite is by construction the toughest thing
     * the floor already fields, so a piso that says nothing still gets a mini-boss that belongs to
     * it, and every config already on disk is fixed without being rewritten.</p>
     *
     * <p>The tramo remains the last resort rather than being dropped: a floor whose roster is all
     * chaff has nothing to promote, and no mini-boss at all is worse than a borrowed one.</p>
     */
    private static List<String> miniPool(FloorDef piso, TierDef tier) {
        if (piso.minijefes() != null && !piso.minijefes().isEmpty()) {
            return piso.minijefes();
        }
        List<String> elites = piso.enemigos().elites();
        if (!elites.isEmpty()) {
            return elites;
        }
        return tier.minijefes() == null ? List.of() : tier.minijefes();
    }

    /**
     * The piso's pool when it declares one, the tramo's otherwise. Resolved here so a boss can be
     * declared once for a whole tramo and still be overridden by the one piso that needs its own —
     * which is the only thing keeping the spider queen out of plain Cuevas.
     */
    private static List<String> pool(List<String> override, List<String> tierPool) {
        if (override != null && !override.isEmpty()) {
            return override;
        }
        return tierPool == null ? List.of() : tierPool;
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
