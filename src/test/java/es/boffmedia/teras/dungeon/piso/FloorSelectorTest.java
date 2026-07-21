package es.boffmedia.teras.dungeon.piso;

import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.ShapeFamily;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Floor selection: the tramo walk, the weighted piso draw, and the curse ordering that the whole
 * design turns on. A seeded selector's tests are mostly about determinism and about the properties
 * that would otherwise only fail in a live run.
 */
class FloorSelectorTest {

    private static final Set<ShapeFamily> ALL = EnumSet.allOf(ShapeFamily.class);
    private static final Set<ShapeFamily> TIGHT =
            EnumSet.of(ShapeFamily.SINGLE, ShapeFamily.LARGE);

    private static FloorDef piso(String id, Set<ShapeFamily> shapes, Set<Curse> curses) {
        return new FloorDef(id, id, "", shapes, 7, "", "", "", curses, List.of(), List.of());
    }

    private static Map<String, FloorDef> catalog(FloorDef... pisos) {
        Map<String, FloorDef> map = new LinkedHashMap<>();
        for (FloorDef piso : pisos) {
            map.put(piso.id(), piso);
        }
        return map;
    }

    private static TierDef tier(int largo, double dificultad, WeightedRef... refs) {
        return new TierDef(largo, dificultad, List.of(refs), List.of("jefe"), List.of("minijefe"));
    }

    private static DungeonDef cripta() {
        return new DungeonDef("cripta", "La Cripta", List.of(
                tier(2, 1.0, new WeightedRef("cuevas", 3), new WeightedRef("infestadas", 1)),
                tier(2, 1.4, new WeightedRef("cuevas", 1)),
                tier(2, 1.8, new WeightedRef("infestadas", 1))));
    }

    private static Map<String, FloorDef> bothPisos() {
        return catalog(
                piso("cuevas", ALL, EnumSet.of(Curse.LABYRINTH, Curse.LOST)),
                piso("infestadas", TIGHT, EnumSet.of(Curse.LOST)));
    }

    // --- the tramo walk -------------------------------------------------------------------------

    @Test
    void lengthIsTheSumOfTramos() {
        assertEquals(6, cripta().length());
    }

    @Test
    void locateWalksSpansRatherThanIndexing() {
        DungeonDef cripta = cripta();
        assertEquals(0, cripta.locate(1).tierIndex());
        assertEquals(0, cripta.locate(2).tierIndex());
        assertEquals(1, cripta.locate(3).tierIndex());
        assertEquals(2, cripta.locate(6).tierIndex());
        assertNull(cripta.locate(7), "past the last floor");
        assertNull(cripta.locate(0), "floors are 1-based");
    }

    /** The numeral on the title card is depth within the tramo, never the absolute floor. */
    @Test
    void depthIsWithinTheTramo() {
        DungeonDef cripta = cripta();
        assertEquals(1, cripta.locate(3).depth());
        assertEquals(2, cripta.locate(4).depth());
    }

    @Test
    void tramosOfUnequalSpanStillWalk() {
        DungeonDef odd = new DungeonDef("odd", "Odd", List.of(
                tier(1, 1.0, new WeightedRef("cuevas", 1)),
                tier(3, 1.5, new WeightedRef("cuevas", 1))));
        assertEquals(4, odd.length());
        assertEquals(0, odd.locate(1).tierIndex());
        assertEquals(1, odd.locate(2).tierIndex());
        assertEquals(2, odd.locate(4).indexInTier());
    }

    // --- selection ------------------------------------------------------------------------------

    @Test
    void isDeterministic() {
        DungeonDef cripta = cripta();
        Map<String, FloorDef> catalog = bothPisos();
        for (int stage = 1; stage <= 6; stage++) {
            FloorPlan first = FloorSelector.select(cripta, catalog, stage, "semilla", Map.of());
            FloorPlan second = FloorSelector.select(cripta, catalog, stage, "semilla", Map.of());
            assertEquals(first, second, "stage " + stage);
        }
    }

    /** Per-floor rolls: a tramo of two floors must be able to produce two different pisos. */
    @Test
    void floorsRollIndependently() {
        DungeonDef cripta = cripta();
        Map<String, FloorDef> catalog = bothPisos();
        boolean sawDifferent = false;
        for (int seed = 0; seed < 200 && !sawDifferent; seed++) {
            FloorPlan one = FloorSelector.select(cripta, catalog, 1, "s" + seed, Map.of());
            FloorPlan two = FloorSelector.select(cripta, catalog, 2, "s" + seed, Map.of());
            sawDifferent = !one.piso().id().equals(two.piso().id());
        }
        assertTrue(sawDifferent, "the two floors of a tramo never differed across 200 seeds");
    }

    @Test
    void carriesTheTramoDifficulty() {
        FloorPlan deep = FloorSelector.select(cripta(), bothPisos(), 5, "semilla", Map.of());
        assertEquals(1.8, deep.dificultad(), 1e-9);
    }

    @Test
    void pastTheLastFloorIsNull() {
        assertNull(FloorSelector.select(cripta(), bothPisos(), 7, "semilla", Map.of()));
    }

    /** A broken piso is dropped from selection rather than thrown mid-build. */
    @Test
    void brokenPisoIsSkipped() {
        FloorDef broken = piso("infestadas", EnumSet.noneOf(ShapeFamily.class), EnumSet.of(Curse.LOST));
        Map<String, FloorDef> catalog = catalog(piso("cuevas", ALL, EnumSet.of(Curse.LOST)), broken);
        for (int seed = 0; seed < 50; seed++) {
            FloorPlan plan = FloorSelector.select(cripta(), catalog, 1, "s" + seed, Map.of());
            assertEquals("cuevas", plan.piso().id());
        }
    }

    /** A tramo whose every piso is broken yields null, not a half-built floor. */
    @Test
    void tramoWithNoUsablePisoIsNull() {
        Map<String, FloorDef> empty = catalog();
        assertNull(FloorSelector.select(cripta(), empty, 1, "semilla", Map.of()));
    }

    @Test
    void weightsAreRespected() {
        DungeonDef cripta = cripta();
        Map<String, FloorDef> catalog = bothPisos();
        Map<String, Integer> counts = new HashMap<>();
        for (int seed = 0; seed < 400; seed++) {
            FloorPlan plan = FloorSelector.select(cripta, catalog, 1, "s" + seed, Map.of());
            counts.merge(plan.piso().id(), 1, Integer::sum);
        }
        assertTrue(counts.getOrDefault("cuevas", 0) > counts.getOrDefault("infestadas", 0),
                "the 3:1 piso should dominate, got " + counts);
        assertTrue(counts.getOrDefault("infestadas", 0) > 0, "the rare piso never appeared");
    }

    // --- curses ---------------------------------------------------------------------------------

    /** The property the whole ordering exists for: a piso is never handed a curse it refuses. */
    @Test
    void neverAssignsARefusedCurse() {
        DungeonDef cripta = cripta();
        Map<String, FloorDef> catalog = bothPisos();
        Map<Curse, Double> always = new LinkedHashMap<>();
        for (Curse curse : Curse.values()) {
            always.put(curse, 1.0);
        }
        for (int seed = 0; seed < 200; seed++) {
            for (int stage = 1; stage <= 6; stage++) {
                FloorPlan plan = FloorSelector.select(cripta, catalog, stage, "s" + seed, always);
                for (Curse curse : plan.curses()) {
                    assertTrue(plan.piso().accepts(curse),
                            plan.piso().id() + " was handed " + curse + ", which it refuses");
                }
            }
        }
    }

    /** Infestadas refuses LABYRINTH, so it must never carry it however often the curse rolls. */
    @Test
    void aRefusedCurseSimplyDoesNotOccurThere() {
        Map<Curse, Double> always = Map.of(Curse.LABYRINTH, 1.0, Curse.LOST, 1.0);
        Set<String> sawLabyrinth = new HashSet<>();
        for (int seed = 0; seed < 300; seed++) {
            FloorPlan plan = FloorSelector.select(cripta(), bothPisos(), 1, "s" + seed, always);
            if (plan.curses().contains(Curse.LABYRINTH)) {
                sawLabyrinth.add(plan.piso().id());
            }
        }
        assertTrue(sawLabyrinth.contains("cuevas"), "cuevas accepts LABYRINTH and never got it");
        assertTrue(!sawLabyrinth.contains("infestadas"),
                "infestadas refuses LABYRINTH but was given it");
    }

    /**
     * The piso must be chosen from a seed that excludes curses, or the two are circular. Changing
     * the curse chances must therefore never change which piso is drawn.
     */
    @Test
    void pisoChoiceIsIndependentOfCurses() {
        DungeonDef cripta = cripta();
        Map<String, FloorDef> catalog = bothPisos();
        Map<Curse, Double> none = Map.of();
        Map<Curse, Double> always = Map.of(Curse.LABYRINTH, 1.0, Curse.LOST, 1.0);
        for (int seed = 0; seed < 200; seed++) {
            FloorPlan without = FloorSelector.select(cripta, catalog, 1, "s" + seed, none);
            FloorPlan with = FloorSelector.select(cripta, catalog, 1, "s" + seed, always);
            assertEquals(without.piso().id(), with.piso().id(),
                    "curse chances changed which piso was drawn — the seeds are entangled");
        }
    }

    @Test
    void zeroChanceMeansNoCurses() {
        FloorPlan plan = FloorSelector.select(cripta(), bothPisos(), 1, "semilla",
                Map.of(Curse.LABYRINTH, 0.0, Curse.LOST, 0.0));
        assertTrue(plan.curses().isEmpty());
    }

    // --- seeds ----------------------------------------------------------------------------------

    /** Two dungeons at the same stage and seed must not generate the same floor. */
    @Test
    void dungeonIdSeparatesTheSeeds() {
        Map<String, FloorDef> catalog = bothPisos();
        DungeonDef cripta = cripta();
        DungeonDef minas = new DungeonDef("minas", "Las Minas", cripta.tramos());
        FloorPlan a = FloorSelector.select(cripta, catalog, 3, "semilla", Map.of());
        FloorPlan b = FloorSelector.select(minas, catalog, 3, "semilla", Map.of());
        assertNotNull(a);
        assertNotNull(b);
        assertTrue(FloorSelector.baseSeed(a, "semilla") != FloorSelector.baseSeed(b, "semilla"),
                "floor 3 of two dungeons shares a base seed — they would build identically");
    }

    /** Stage 4 is the second floor of tramo 2, so the card reads II — not IV. */
    @Test
    void titleIsPlaceAndDepthNotAbsoluteFloor() {
        FloorDef named = new FloorDef("cuevas", "Cuevas", "algo se mueve en la oscuridad",
                ALL, 7, "", "", "", Set.of(), List.of(), List.of());
        FloorPlan second =
                FloorSelector.select(cripta(), catalog(named), 4, "semilla", Map.of());
        assertEquals("Cuevas II", second.title());
        assertEquals("algo se mueve en la oscuridad", second.subtitle());
    }

    /**
     * The plan resolves the boss pool: a piso without its own inherits the tramo's, a piso with one
     * overrides it. This is the property that was config nothing consumed — every floor drew from
     * the global stage pool — so the spider queen could appear in plain Cuevas or not at all.
     */
    @Test
    void bossPoolInheritsFromTheTramo() {
        FloorDef plain = piso("cuevas", ALL, Set.of());
        FloorPlan plan = FloorSelector.select(
                new DungeonDef("d", "D", List.of(tier(2, 1.0, new WeightedRef("cuevas", 1)))),
                catalog(plain), 1, "s", Map.of());
        assertEquals(List.of("jefe"), plan.jefes());
        assertEquals(List.of("minijefe"), plan.minijefes());
    }

    @Test
    void pisoOverridesTheTramoBossPool() {
        FloorDef queenPiso = new FloorDef("infestadas", "Cuevas Infestadas", "", TIGHT, 4,
                "", "", "infestacion", EnumSet.of(Curse.LOST),
                List.of("reina_cria"), List.of());
        FloorPlan plan = FloorSelector.select(
                new DungeonDef("d", "D", List.of(tier(2, 1.0, new WeightedRef("infestadas", 1)))),
                catalog(queenPiso), 1, "s", Map.of());
        assertEquals(List.of("reina_cria"), plan.jefes(),
                "the queen's piso must override, or she never appears");
        assertEquals(List.of("minijefe"), plan.minijefes(),
                "an unset mini-boss pool still inherits the tramo's");
    }
}
