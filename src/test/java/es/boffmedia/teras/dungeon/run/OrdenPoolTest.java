package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.dungeon.gen.SatelliteOdds;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What la Orden puts on her font. The tier decides the shape of the offer, and two invariants
 * decide what may be on it at all.
 */
class OrdenPoolTest {

    private static Set<String> ids(List<OrdenPool.Gift> gifts) {
        return gifts.stream().map(OrdenPool.Gift::id).collect(Collectors.toSet());
    }

    /**
     * The offer must be exactly as long as the shipped {@code Tier.options()} says. §63e's table
     * reads "Plena: 2 picks, 4 options — pool adds the phoenix charm", which is a fifth entry unless
     * something steps aside; if a later edit adds one without removing one, this is what says so.
     */
    @Test
    void eachTierOffersExactlyItsOptionCount() {
        for (SatelliteOdds.Tier tier : SatelliteOdds.Tier.values()) {
            assertEquals(tier.options(), OrdenPool.of(tier, 60).size(),
                    tier + " offered the wrong number of gifts");
        }
    }

    @Test
    void everyGiftOnAPoolIsDistinct() {
        for (SatelliteOdds.Tier tier : SatelliteOdds.Tier.values()) {
            assertEquals(OrdenPool.of(tier, 60).size(), ids(OrdenPool.of(tier, 60)).size(),
                    tier + " offered the same gift twice");
        }
    }

    /** Restoration arrives at Mayor, the charm at Plena — the tiers are what purity buys. */
    @Test
    void theTiersUnlockWhatTheDesignSays() {
        assertFalse(ids(OrdenPool.of(SatelliteOdds.Tier.MENOR, 60))
                .contains(OrdenPool.RESTITUCION));
        assertTrue(ids(OrdenPool.of(SatelliteOdds.Tier.MAYOR, 60))
                .contains(OrdenPool.RESTITUCION));
        assertFalse(ids(OrdenPool.of(SatelliteOdds.Tier.MAYOR, 60))
                .contains(OrdenPool.FENIX));
        assertTrue(ids(OrdenPool.of(SatelliteOdds.Tier.PLENA, 60))
                .contains(OrdenPool.FENIX));
    }

    /** A flawless floor gets two picks; everything else gets one. */
    @Test
    void onlyPlenaGivesTwoPicks() {
        assertEquals(1, SatelliteOdds.Tier.MENOR.picks());
        assertEquals(1, SatelliteOdds.Tier.MAYOR.picks());
        assertEquals(2, SatelliteOdds.Tier.PLENA.picks());
    }

    /**
     * PRODUCCION §4.1: monedas, petardos and llaves are never substitutable, and it is an invariant
     * enforced in review. Neither satellite may trade in keys or petardos — coins buying access is
     * what collapses the trio — so the allowed vocabulary is pinned here rather than left to it.
     */
    @Test
    void nothingSheGivesTouchesTheScarcityTrio() {
        Set<String> allowed = Set.of(OrdenPool.VIGOR, OrdenPool.OBOLO,
                OrdenPool.RELIQUIA, OrdenPool.RESTITUCION, OrdenPool.FENIX);
        for (SatelliteOdds.Tier tier : SatelliteOdds.Tier.values()) {
            assertTrue(allowed.containsAll(ids(OrdenPool.of(tier, 60))),
                    tier + " offered something outside the sanctioned pool");
        }
    }

    /**
     * Her first slot reads the party's actual state. Absolución is also the answer to the debt
     * loophole — an unpaid deuda pierces {@code ordenCommitted}, so grace has to be able to settle
     * an account deliberately or the creditor never stops coming.
     */
    @Test
    void mercyIsAbsolutionOnlyWhileADebtStands() {
        assertEquals(OrdenPool.ABSOLUCION, OrdenPool.mercy(120).id());
        assertEquals(OrdenPool.PURIFICACION, OrdenPool.mercy(0).id());
    }

}
