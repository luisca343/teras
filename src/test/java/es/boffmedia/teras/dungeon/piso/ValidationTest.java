package es.boffmedia.teras.dungeon.piso;

import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.ShapeFamily;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validation exists because selection has <b>no fallback</b>. With themes, a missing room quietly
 * borrowed another theme's; here there is nothing to borrow, so a broken piso has to be caught at
 * load and dropped from selection — never discovered mid-build, where the materializer's job loop
 * swallows the exception and leaves a run waiting on a floor that never lands.
 *
 * <p>Every check therefore reports a reason rather than throwing: the log line has to name what is
 * wrong, or an admin cannot fix it.</p>
 */
class ValidationTest {

    private static final Set<ShapeFamily> ALL = EnumSet.allOf(ShapeFamily.class);

    private static FloorDef piso(String id, String nombre, Set<ShapeFamily> shapes, int luz) {
        return new FloorDef(id, nombre, "", shapes, luz, "", "", "",
                EnumSet.of(Curse.LOST), List.of(), List.of(), Map.of());
    }

    private static FloorDef good() {
        return piso("cuevas", "Cuevas", ALL, 7);
    }

    @Test
    void aWellFormedPisoHasNoProblems() {
        assertTrue(good().problems().isEmpty(), good().problems().toString());
    }

    @Test
    void aPisoWithoutSingleIsRejected() {
        List<String> problems = piso("x", "X", EnumSet.of(ShapeFamily.BIG), 7).problems();
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("SINGLE"), problems.toString());
    }

    @Test
    void aPisoWithNoShapesIsRejected() {
        assertFalse(piso("x", "X", EnumSet.noneOf(ShapeFamily.class), 7).problems().isEmpty());
    }

    /** A blank name would render a blank title card, which reads as the system being broken. */
    @Test
    void aPisoWithoutANameIsRejected() {
        assertFalse(piso("x", "  ", ALL, 7).problems().isEmpty());
    }

    @Test
    void lightLevelIsBounded() {
        assertFalse(piso("x", "X", ALL, 16).problems().isEmpty());
        assertFalse(piso("x", "X", ALL, -1).problems().isEmpty());
        assertTrue(piso("x", "X", ALL, 0).problems().isEmpty());
        assertTrue(piso("x", "X", ALL, 15).problems().isEmpty());
    }

    // --- dungeon level --------------------------------------------------------------------------

    private static DungeonDef dungeonNaming(String pisoId) {
        return new DungeonDef("cripta", "La Cripta", List.of(
                new TierDef(2, 1.0, List.of(new WeightedRef(pisoId, 1)),
                        List.of("jefe"), List.of("minijefe"))));
    }

    @Test
    void aDungeonNamingAMissingPisoIsRejected() {
        List<String> problems = dungeonNaming("fantasma").problems(Map.of("cuevas", good()));
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("fantasma"), problems.toString());
    }

    /** A dungeon is only as sound as the pisos it names — there is nothing else to fall back to. */
    @Test
    void aDungeonNamingABrokenPisoIsRejected() {
        FloorDef broken = piso("cuevas", "Cuevas", EnumSet.noneOf(ShapeFamily.class), 7);
        assertFalse(dungeonNaming("cuevas").problems(Map.of("cuevas", broken)).isEmpty());
    }

    @Test
    void aTramoWithoutABossPoolIsRejected() {
        DungeonDef noBoss = new DungeonDef("cripta", "La Cripta", List.of(
                new TierDef(2, 1.0, List.of(new WeightedRef("cuevas", 1)),
                        List.of(), List.of("minijefe"))));
        assertFalse(noBoss.problems(Map.of("cuevas", good())).isEmpty());
    }

    @Test
    void aTramoWithNoPisosIsRejected() {
        DungeonDef empty = new DungeonDef("cripta", "La Cripta", List.of(
                new TierDef(2, 1.0, List.of(), List.of("jefe"), List.of())));
        assertFalse(empty.problems(Map.of()).isEmpty());
    }

    @Test
    void aZeroSpanTramoIsRejected() {
        DungeonDef zero = new DungeonDef("cripta", "La Cripta", List.of(
                new TierDef(0, 1.0, List.of(new WeightedRef("cuevas", 1)),
                        List.of("jefe"), List.of())));
        assertFalse(zero.problems(Map.of("cuevas", good())).isEmpty());
    }

    @Test
    void aDungeonWithNoTramosIsRejected() {
        assertFalse(new DungeonDef("vacia", "Vacía", List.of()).problems(Map.of()).isEmpty());
    }

    @Test
    void aWellFormedDungeonHasNoProblems() {
        assertTrue(dungeonNaming("cuevas").problems(Map.of("cuevas", good())).isEmpty());
    }

    /** Weights below one would drop a piso out of a weighted draw entirely. */
    @Test
    void weightsAreClampedToAtLeastOne() {
        assertEquals(1, new WeightedRef("cuevas", 0).weight());
        assertEquals(1, new WeightedRef("cuevas", -5).weight());
        assertEquals(3, new WeightedRef("cuevas", 3).weight());
    }

    /** isFinalStage replaces GenConfig.finalStage, which could not vary per dungeon. */
    @Test
    void finalStageComesFromTheDungeon() {
        DungeonDef six = new DungeonDef("cripta", "La Cripta", List.of(
                new TierDef(2, 1.0, List.of(new WeightedRef("cuevas", 1)), List.of("j"), List.of()),
                new TierDef(4, 1.5, List.of(new WeightedRef("cuevas", 1)), List.of("j"), List.of())));
        assertEquals(6, six.length());
        assertTrue(six.isFinalStage(6));
        assertFalse(six.isFinalStage(5));
        assertTrue(six.isValidStage(1));
        assertFalse(six.isValidStage(7));
        assertFalse(six.isValidStage(0));
    }
}
