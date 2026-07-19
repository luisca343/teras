package es.boffmedia.teras.region;

import es.boffmedia.teras.plot.model.PlotOwnership;
import es.boffmedia.teras.region.RegionResolver.Decision;
import es.boffmedia.teras.region.RegionResolver.Outcome;
import es.boffmedia.teras.region.model.RegionFlag;
import es.boffmedia.teras.region.model.RegionPoint;
import es.boffmedia.teras.region.model.TerasRegion;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The deny-unless-owner rule, which inverts the default inside a plot. These are the cases where
 * WorldGuard parity actually lives, so they are worth more than the flag tests.
 */
class PlotResolutionTest {

    private static final String DIM = "minecraft:overworld";
    private static final long NOW = 10_000L;

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID FRIEND = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();

    private final Map<String, PlotOwnership> plots = new HashMap<>();

    private static TerasRegion square(String name, int x, int z, int size, int priority) {
        TerasRegion region = TerasRegion.polygon(name, DIM, List.of(
                new RegionPoint(x, z), new RegionPoint(x + size, z),
                new RegionPoint(x + size, z + size), new RegionPoint(x, z + size)), null, null);
        region.setPriority(priority);
        return region;
    }

    private void plot(String name, UUID owner, Long expiresAt, UUID... members) {
        plots.put(name, new PlotOwnership(name, DIM, owner, 0L, expiresAt, Set.of(members)));
    }

    private Decision decide(List<TerasRegion> regions, double x, double z, RegionFlag flag, UUID player) {
        return RegionResolver.decide(RegionResolver.ordered(regions), x, 64.0, z, flag, player,
                plots::get, NOW);
    }

    // --- The passthrough guarantee: today's regions must not change behaviour at all ---

    /** Every town and road that ships has no ownership row, so the rule cannot fire. */
    @Test
    void regionsWithoutAnOwnershipRowAreNeverPlots() {
        List<TerasRegion> regions = List.of(square("pueblo_mizu", 0, 0, 100, 0));
        for (RegionFlag flag : RegionFlag.values()) {
            assertFalse(decide(regions, 50, 50, flag, STRANGER).denied(),
                    flag + " must stay allowed in a town nobody can buy");
        }
    }

    @Test
    void townFlagsStillApplyWithPlotsInPlay() {
        TerasRegion town = square("pueblo_mizu", 0, 0, 100, 0);
        town.setFlag(RegionFlag.BUILD, false);
        Decision decision = decide(List.of(town), 50, 50, RegionFlag.BUILD, STRANGER);
        assertEquals(Outcome.DENIED_BY_FLAG, decision.outcome());
        assertEquals("pueblo_mizu", decision.regionName());
    }

    // --- The implicit denial ---

    @Test
    void strangerCannotBuildInAnOwnedPlot() {
        TerasRegion plotRegion = square("parcela_mizu_01", 10, 10, 5, 10);
        plot("parcela_mizu_01", OWNER, null);

        Decision decision = decide(List.of(plotRegion), 12, 12, RegionFlag.BUILD, STRANGER);
        assertEquals(Outcome.DENIED_BY_PLOT, decision.outcome());
        assertEquals("parcela_mizu_01", decision.regionName());
        assertEquals(OWNER, decision.owner(), "the message names who to go ask");
    }

    @Test
    void ownerAndMembersCanBuild() {
        TerasRegion plotRegion = square("parcela_mizu_01", 10, 10, 5, 10);
        plot("parcela_mizu_01", OWNER, null, FRIEND);

        assertFalse(decide(List.of(plotRegion), 12, 12, RegionFlag.BUILD, OWNER).denied());
        assertFalse(decide(List.of(plotRegion), 12, 12, RegionFlag.BUILD, FRIEND).denied());
        assertTrue(decide(List.of(plotRegion), 12, 12, RegionFlag.BUILD, STRANGER).denied());
    }

    /** An unowned plot is for sale, not a free-for-all. */
    @Test
    void nobodyCanBuildInAnUnownedPlot() {
        TerasRegion plotRegion = square("parcela_mizu_01", 10, 10, 5, 10);
        plot("parcela_mizu_01", null, null);

        Decision decision = decide(List.of(plotRegion), 12, 12, RegionFlag.BUILD, STRANGER);
        assertEquals(Outcome.DENIED_BY_PLOT, decision.outcome());
        assertNull(decision.owner(), "no owner to name, so the feedback says 'for sale' instead");
    }

    @Test
    void denialCoversTheWholeBuildingFamilyOnly() {
        TerasRegion plotRegion = square("parcela_mizu_01", 10, 10, 5, 10);
        plot("parcela_mizu_01", OWNER, null);

        assertTrue(decide(List.of(plotRegion), 12, 12, RegionFlag.BUILD, STRANGER).denied());
        assertTrue(decide(List.of(plotRegion), 12, 12, RegionFlag.BREAK, STRANGER).denied());
        assertTrue(decide(List.of(plotRegion), 12, 12, RegionFlag.INTERACT, STRANGER).denied());
        // Not about who owns the ground; explosions are resolved with no player at all.
        assertFalse(decide(List.of(plotRegion), 12, 12, RegionFlag.PVP, STRANGER).denied());
        assertFalse(decide(List.of(plotRegion), 12, 12, RegionFlag.EXPLOSIONS, null).denied());
    }

    @Test
    void denialStopsAtThePlotBorder() {
        TerasRegion town = square("pueblo_mizu", 0, 0, 100, 0);
        TerasRegion plotRegion = square("parcela_mizu_01", 10, 10, 5, 10);
        plot("parcela_mizu_01", OWNER, null);
        List<TerasRegion> regions = List.of(town, plotRegion);

        assertTrue(decide(regions, 12, 12, RegionFlag.BUILD, STRANGER).denied());
        assertFalse(decide(regions, 50, 50, RegionFlag.BUILD, STRANGER).denied(),
                "the rest of the town stays open");
    }

    // --- Interaction with priority ---

    /** The core case: a plot inside a town, where the plot must win. */
    @Test
    void plotInsideTownShadowsTheTown() {
        TerasRegion town = square("pueblo_mizu", 0, 0, 100, 0);
        town.setFlag(RegionFlag.BUILD, false);
        TerasRegion plotRegion = square("parcela_mizu_01", 10, 10, 5, 10);
        plot("parcela_mizu_01", OWNER, null);
        List<TerasRegion> regions = List.of(town, plotRegion);

        assertFalse(decide(regions, 12, 12, RegionFlag.BUILD, OWNER).denied(),
                "the owner builds on their plot even though the town denies building");
        assertEquals(Outcome.DENIED_BY_PLOT,
                decide(regions, 12, 12, RegionFlag.BUILD, STRANGER).outcome());
        assertEquals(Outcome.DENIED_BY_FLAG,
                decide(regions, 50, 50, RegionFlag.BUILD, OWNER).outcome());
    }

    /**
     * A plot left at the default priority is shadowed flat by its town — the exact footgun
     * {@code /teras parcela vender} raises priority to avoid.
     */
    @Test
    void plotAtEqualPriorityDoesNotOutrankItsTown() {
        TerasRegion town = square("pueblo_mizu", 0, 0, 100, 0);
        town.setFlag(RegionFlag.BUILD, false);
        TerasRegion plotRegion = square("parcela_mizu_01", 10, 10, 5, 0);
        plot("parcela_mizu_01", OWNER, null);

        assertTrue(decide(List.of(town, plotRegion), 12, 12, RegionFlag.BUILD, OWNER).denied());
    }

    /** A lower-priority plot is shadowed away entirely — its ownership never gets consulted. */
    @Test
    void shadowedPlotDoesNotDeny() {
        TerasRegion arena = square("arena_libre", 0, 0, 100, 20);
        TerasRegion plotRegion = square("parcela_mizu_01", 10, 10, 5, 10);
        plot("parcela_mizu_01", OWNER, null);

        assertFalse(decide(List.of(arena, plotRegion), 12, 12, RegionFlag.BUILD, STRANGER).denied());
    }

    /** Holding one plot in the tier is enough; overlapping plots need not each admit you. */
    @Test
    void holdingOneOfTwoOverlappingPlotsIsEnough() {
        TerasRegion mine = square("parcela_a", 0, 0, 20, 10);
        TerasRegion theirs = square("parcela_b", 10, 10, 20, 10);
        plot("parcela_a", OWNER, null);
        plot("parcela_b", STRANGER, null);
        List<TerasRegion> regions = List.of(mine, theirs);

        assertFalse(decide(regions, 15, 15, RegionFlag.BUILD, OWNER).denied());
        assertTrue(decide(regions, 15, 15, RegionFlag.BUILD, FRIEND).denied());
    }

    /** Membership bypasses the implicit denial, not an explicit one. */
    @Test
    void explicitDenialStillBindsTheOwner() {
        TerasRegion plotRegion = square("parcela_mizu_01", 10, 10, 5, 10);
        plotRegion.setFlag(RegionFlag.BREAK, false);
        plot("parcela_mizu_01", OWNER, null);

        Decision decision = decide(List.of(plotRegion), 12, 12, RegionFlag.BREAK, OWNER);
        assertEquals(Outcome.DENIED_BY_FLAG, decision.outcome());
        assertFalse(decide(List.of(plotRegion), 12, 12, RegionFlag.BUILD, OWNER).denied());
    }

    /** A plot in the tier reports as a plot denial even when a flag also denies. */
    @Test
    void plotDenialIsReportedAheadOfFlagDenial() {
        TerasRegion plotRegion = square("parcela_mizu_01", 10, 10, 5, 10);
        plotRegion.setFlag(RegionFlag.BUILD, false);
        plot("parcela_mizu_01", OWNER, null);

        assertEquals(Outcome.DENIED_BY_PLOT,
                decide(List.of(plotRegion), 12, 12, RegionFlag.BUILD, STRANGER).outcome());
    }

    // --- Rentals (expiresAt is already in the schema) ---

    @Test
    void expiredLeaseRevertsThePlotToUnowned() {
        TerasRegion plotRegion = square("parcela_mizu_01", 10, 10, 5, 10);
        plot("parcela_mizu_01", OWNER, NOW - 1, FRIEND);

        Decision decision = decide(List.of(plotRegion), 12, 12, RegionFlag.BUILD, OWNER);
        assertEquals(Outcome.DENIED_BY_PLOT, decision.outcome());
        assertNull(decision.owner());
        assertTrue(decide(List.of(plotRegion), 12, 12, RegionFlag.BUILD, FRIEND).denied());
    }

    @Test
    void leaseStillRunningBehavesLikeOwnership() {
        TerasRegion plotRegion = square("parcela_mizu_01", 10, 10, 5, 10);
        plot("parcela_mizu_01", OWNER, NOW + 1);

        assertFalse(decide(List.of(plotRegion), 12, 12, RegionFlag.BUILD, OWNER).denied());
    }

    // --- Odds and ends ---

    @Test
    void nullPlayerIsNeverAnOwner() {
        TerasRegion plotRegion = square("parcela_mizu_01", 10, 10, 5, 10);
        plot("parcela_mizu_01", OWNER, null);
        assertTrue(decide(List.of(plotRegion), 12, 12, RegionFlag.BUILD, null).denied());
    }

    @Test
    void noPlotsLookupBehavesLikePlainFlagResolution() {
        TerasRegion town = square("pueblo_mizu", 0, 0, 100, 0);
        town.setFlag(RegionFlag.BUILD, false);
        List<TerasRegion> ordered = RegionResolver.ordered(List.of(town));

        assertEquals(Outcome.DENIED_BY_FLAG, RegionResolver.decide(
                ordered, 50, 64, 50, RegionFlag.BUILD, STRANGER,
                RegionResolver.PlotLookup.NONE, NOW).outcome());
        assertEquals(RegionResolver.denies(ordered, 50, 64, 50, RegionFlag.BUILD),
                RegionResolver.decide(ordered, 50, 64, 50, RegionFlag.BUILD, STRANGER,
                        RegionResolver.PlotLookup.NONE, NOW).denied());
    }
}
