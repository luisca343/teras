package es.boffmedia.teras.region;

import es.boffmedia.teras.region.model.RegionFlag;
import es.boffmedia.teras.region.model.RegionPoint;
import es.boffmedia.teras.region.model.TerasRegion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionResolverTest {

    private static final String DIM = "minecraft:overworld";

    /** A square from (x,z) to (x+size, z+size), full height. */
    private static TerasRegion square(String name, int x, int z, int size, int priority) {
        TerasRegion region = TerasRegion.polygon(name, DIM, List.of(
                new RegionPoint(x, z), new RegionPoint(x + size, z),
                new RegionPoint(x + size, z + size), new RegionPoint(x, z + size)), null, null);
        region.setPriority(priority);
        return region;
    }

    @Test
    void orderedSortsHighestPriorityFirst() {
        List<TerasRegion> ordered = RegionResolver.ordered(List.of(
                square("pueblo_a", 0, 0, 100, 0),
                square("parcela_b", 10, 10, 5, 10),
                square("carretera_c", 0, 0, 100, 5)));
        assertEquals(List.of("parcela_b", "carretera_c", "pueblo_a"),
                ordered.stream().map(TerasRegion::getName).toList());
    }

    @Test
    void noRegionsAllows() {
        assertFalse(RegionResolver.denies(List.of(), 5.0, 64.0, 5.0, RegionFlag.BUILD));
    }

    @Test
    void regionWithNoOpinionAllows() {
        List<TerasRegion> ordered = RegionResolver.ordered(List.of(square("pueblo_a", 0, 0, 10, 0)));
        assertFalse(RegionResolver.denies(ordered, 5.0, 64.0, 5.0, RegionFlag.BUILD));
    }

    @Test
    void explicitDenialBlocksInsideOnly() {
        TerasRegion town = square("pueblo_a", 0, 0, 10, 0);
        town.setFlag(RegionFlag.BUILD, false);
        List<TerasRegion> ordered = RegionResolver.ordered(List.of(town));
        assertTrue(RegionResolver.denies(ordered, 5.0, 64.0, 5.0, RegionFlag.BUILD));
        assertFalse(RegionResolver.denies(ordered, 50.0, 64.0, 50.0, RegionFlag.BUILD));
        assertFalse(RegionResolver.denies(ordered, 5.0, 64.0, 5.0, RegionFlag.PVP));
    }

    @Test
    void equalPriorityIsMostRestrictiveWins() {
        TerasRegion open = square("pueblo_a", 0, 0, 100, 0);
        open.setFlag(RegionFlag.BUILD, true);
        TerasRegion closed = square("pueblo_b", 0, 0, 100, 0);
        closed.setFlag(RegionFlag.BUILD, false);
        List<TerasRegion> ordered = RegionResolver.ordered(List.of(open, closed));
        assertTrue(RegionResolver.denies(ordered, 5.0, 64.0, 5.0, RegionFlag.BUILD));
    }

    /** The case priority exists for: a permissive plot inside a locked town. */
    @Test
    void higherPriorityPlotReopensAFlagItsTownCloses() {
        TerasRegion town = square("pueblo_mizu", 0, 0, 100, 0);
        town.setFlag(RegionFlag.BUILD, false);
        TerasRegion plot = square("parcela_mizu_01", 10, 10, 5, 10);
        plot.setFlag(RegionFlag.BUILD, true);
        List<TerasRegion> ordered = RegionResolver.ordered(List.of(town, plot));

        assertFalse(RegionResolver.denies(ordered, 12.0, 64.0, 12.0, RegionFlag.BUILD));
        // Still inside the town, outside the plot: the town's denial stands.
        assertTrue(RegionResolver.denies(ordered, 50.0, 64.0, 50.0, RegionFlag.BUILD));
    }

    /** The mirror case: a restrictive plot inside an open town. */
    @Test
    void higherPriorityPlotClosesAFlagItsTownLeavesOpen() {
        TerasRegion town = square("pueblo_mizu", 0, 0, 100, 0);
        TerasRegion plot = square("parcela_mizu_01", 10, 10, 5, 10);
        plot.setFlag(RegionFlag.BUILD, false);
        List<TerasRegion> ordered = RegionResolver.ordered(List.of(town, plot));

        assertTrue(RegionResolver.denies(ordered, 12.0, 64.0, 12.0, RegionFlag.BUILD));
        assertFalse(RegionResolver.denies(ordered, 50.0, 64.0, 50.0, RegionFlag.BUILD));
    }

    /** A shadowing region with no opinion still shadows — the town below never gets asked. */
    @Test
    void higherPrioritySilenceShadowsLowerDenial() {
        TerasRegion town = square("pueblo_mizu", 0, 0, 100, 0);
        town.setFlag(RegionFlag.BUILD, false);
        TerasRegion plot = square("parcela_mizu_01", 10, 10, 5, 10);
        List<TerasRegion> ordered = RegionResolver.ordered(List.of(town, plot));

        assertFalse(RegionResolver.denies(ordered, 12.0, 64.0, 12.0, RegionFlag.BUILD));
    }

    /** Shadowing is decided by the regions actually containing the point, not by the dimension. */
    @Test
    void higherPriorityRegionElsewhereDoesNotShadow() {
        TerasRegion town = square("pueblo_mizu", 0, 0, 100, 0);
        town.setFlag(RegionFlag.BUILD, false);
        TerasRegion faraway = square("parcela_lejos", 500, 500, 5, 10);
        List<TerasRegion> ordered = RegionResolver.ordered(List.of(town, faraway));

        assertTrue(RegionResolver.denies(ordered, 50.0, 64.0, 50.0, RegionFlag.BUILD));
    }

    @Test
    void deepestOfThreeNestedTiersWins() {
        TerasRegion town = square("pueblo_mizu", 0, 0, 100, 0);
        town.setFlag(RegionFlag.BUILD, false);
        TerasRegion district = square("barrio_mizu", 0, 0, 50, 5);
        district.setFlag(RegionFlag.BUILD, true);
        TerasRegion plot = square("parcela_mizu_01", 10, 10, 5, 10);
        plot.setFlag(RegionFlag.BUILD, false);
        List<TerasRegion> ordered = RegionResolver.ordered(List.of(town, district, plot));

        assertTrue(RegionResolver.denies(ordered, 12.0, 64.0, 12.0, RegionFlag.BUILD));
        assertFalse(RegionResolver.denies(ordered, 30.0, 64.0, 30.0, RegionFlag.BUILD));
        assertTrue(RegionResolver.denies(ordered, 80.0, 64.0, 80.0, RegionFlag.BUILD));
    }

    /** Two plots at the same priority overlapping: within a tier, deny still wins. */
    @Test
    void tiedTopPriorityFallsBackToMostRestrictive() {
        TerasRegion a = square("parcela_a", 0, 0, 20, 10);
        a.setFlag(RegionFlag.BUILD, true);
        TerasRegion b = square("parcela_b", 10, 10, 20, 10);
        b.setFlag(RegionFlag.BUILD, false);
        List<TerasRegion> ordered = RegionResolver.ordered(List.of(a, b));

        assertTrue(RegionResolver.denies(ordered, 15.0, 64.0, 15.0, RegionFlag.BUILD));
        assertFalse(RegionResolver.denies(ordered, 5.0, 64.0, 5.0, RegionFlag.BUILD));
    }

    /** Negative priorities are legal and rank below the default 0. */
    @Test
    void negativePriorityRanksBelowDefault() {
        TerasRegion backdrop = square("mundo_base", 0, 0, 100, -10);
        backdrop.setFlag(RegionFlag.BUILD, false);
        TerasRegion town = square("pueblo_mizu", 0, 0, 50, 0);
        List<TerasRegion> ordered = RegionResolver.ordered(List.of(backdrop, town));

        assertFalse(RegionResolver.denies(ordered, 25.0, 64.0, 25.0, RegionFlag.BUILD));
        assertTrue(RegionResolver.denies(ordered, 75.0, 64.0, 75.0, RegionFlag.BUILD));
    }
}
