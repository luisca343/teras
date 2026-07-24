package es.boffmedia.teras.dungeon.gen;

import es.boffmedia.teras.dungeon.gen.SatelliteOdds.FloorOutcome;
import es.boffmedia.teras.dungeon.gen.SatelliteOdds.Ledger;
import es.boffmedia.teras.dungeon.gen.SatelliteOdds.Tier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SatelliteOddsTest {

    /** A floor played perfectly: nobody died, nothing touched anyone, no flesh sold. */
    private static FloorOutcome flawless() {
        return new FloorOutcome(false, true, true, false, false, false);
    }

    /** A floor played badly, and the party is broke and bleeding at the end of it. */
    private static FloorOutcome desperate() {
        return new FloorOutcome(true, false, false, true, true, true);
    }

    @Test
    void aFreshRunMeetsHimAtTheBaseChanceAndHerNotAtAll() {
        Ledger fresh = Ledger.empty();
        assertEquals(SatelliteOdds.ACREEDOR_BASE,
                SatelliteOdds.acreedor(fresh, FloorOutcome.fresh()));
        // She answers a refusal; there has not been one yet.
        assertEquals(0, SatelliteOdds.orden(fresh, flawless()));
    }

    @Test
    void heMayVisitTheFirstFloor() {
        // The old rule withheld him from floor 1; nothing in the odds does that any more, and the
        // generator no longer passes a depth that would.
        assertTrue(SatelliteOdds.acreedor(Ledger.empty(), FloorOutcome.fresh()) > 0);
    }

    @Test
    void dealingOnceMakesHimARegularVisitor() {
        Ledger client = new Ledger(1, 0, false, 0, false);
        assertEquals(SatelliteOdds.ACREEDOR_BASE + SatelliteOdds.ACREEDOR_DEALT_BEFORE,
                SatelliteOdds.acreedor(client, FloorOutcome.fresh()));
    }

    @Test
    void aDesperateFloorPullsHimToTheCapButNeverToCertainty() {
        int chance = SatelliteOdds.acreedor(new Ledger(1, 0, false, 0, true), desperate());
        assertEquals(SatelliteOdds.CAP, chance);
    }

    @Test
    void anUnpaidDeudaSummonsHimOutright() {
        assertEquals(100, SatelliteOdds.acreedor(new Ledger(0, 0, false, 60, false),
                FloorOutcome.fresh()));
    }

    @Test
    void thePactBanishesHimOnlyWhileTheAccountsAreSettled() {
        Ledger devout = new Ledger(0, 3, true, 0, false);
        assertEquals(0, SatelliteOdds.acreedor(devout, desperate()));

        // The loophole, and the whole point of it: grace absolves the soul but does not settle
        // accounts, so a debt walks straight back through the Orden's protection.
        Ledger devoutButIndebted = new Ledger(0, 3, true, 60, false);
        assertEquals(100, SatelliteOdds.acreedor(devoutButIndebted, desperate()));
    }

    @Test
    void sheNeedsARefusalAndIsGenerousAfterOne() {
        Ledger refused = new Ledger(0, 1, false, 0, false);
        // Base plus the two purity signals a quiet floor earns for free: nobody died, nothing sold.
        assertEquals(SatelliteOdds.ORDEN_BASE + SatelliteOdds.ORDEN_NO_DEATHS
                        + SatelliteOdds.ORDEN_SOLD_NO_HEARTS,
                SatelliteOdds.orden(refused, FloorOutcome.fresh()));
    }

    @Test
    void aFlawlessFloorAllButGuaranteesHer() {
        assertEquals(SatelliteOdds.CAP,
                SatelliteOdds.orden(new Ledger(0, 1, false, 0, false), flawless()));
    }

    @Test
    void takingHerBlessingEndsHerVisitsToo() {
        // Committed means the arc is closed on both sides — she has already given what she gives.
        assertEquals(0, SatelliteOdds.orden(new Ledger(0, 2, true, 0, false), flawless()));
    }

    @Test
    void theyWantOppositeThings() {
        Ledger refused = new Ledger(0, 1, false, 0, false);
        FloorOutcome clean = flawless();
        FloorOutcome bloody = desperate();

        assertTrue(SatelliteOdds.orden(refused, clean) > SatelliteOdds.orden(refused, bloody),
                "a clean floor must pull her harder than a bloody one");
        assertTrue(SatelliteOdds.acreedor(refused, bloody) > SatelliteOdds.acreedor(refused, clean),
                "a bloody floor must pull him harder than a clean one");
    }

    @Test
    void oneDeathSwingsBothWays() {
        Ledger refused = new Ledger(0, 1, false, 0, false);
        FloorOutcome clean = FloorOutcome.fresh();
        FloorOutcome died = new FloorOutcome(true, false, false, false, false, false);

        assertEquals(SatelliteOdds.ACREEDOR_DEATH,
                SatelliteOdds.acreedor(refused, died) - SatelliteOdds.acreedor(refused, clean));
        assertEquals(SatelliteOdds.ORDEN_NO_DEATHS,
                SatelliteOdds.orden(refused, clean) - SatelliteOdds.orden(refused, died));
    }

    @Test
    void purityTiersTheGift() {
        assertEquals(SatelliteOdds.PURITY_MAX, SatelliteOdds.purity(flawless()));
        assertEquals(Tier.PLENA, SatelliteOdds.tierOf(SatelliteOdds.purity(flawless())));

        // Everything went wrong: no purity at all.
        assertEquals(0, SatelliteOdds.purity(desperate()));
        assertEquals(Tier.MENOR, SatelliteOdds.tierOf(0));

        assertEquals(Tier.MAYOR, SatelliteOdds.tierOf(SatelliteOdds.PURITY_MAYOR));
        assertEquals(Tier.MENOR, SatelliteOdds.tierOf(SatelliteOdds.PURITY_MAYOR - 1));
    }

    @Test
    void onlyAFlawlessFloorBuysTheSecondPick() {
        assertEquals(2, Tier.PLENA.picks());
        assertEquals(1, Tier.MAYOR.picks());
        assertEquals(1, Tier.MENOR.picks());
        assertEquals(3, Tier.MENOR.options());
        assertEquals(4, Tier.MAYOR.options());
    }
}
