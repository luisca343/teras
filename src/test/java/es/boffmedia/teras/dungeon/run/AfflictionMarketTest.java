package es.boffmedia.teras.dungeon.run;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The curse room as a market rather than a tax.
 *
 * <p>The properties worth pinning are the ones that make it a decision: an offer you cannot accept
 * must never be shown, the reward has to fall with depth (you live with a late affliction for less
 * time), and buying your way out has to get harder the deeper you dug.</p>
 */
class AfflictionMarketTest {

    private static final int BASE_REWARD = 40;
    private static final int BASE_PURGE = 30;

    @Test
    void offersNeverRepeatWhatThePartyAlreadyCarries() {
        AfflictionSet party = new AfflictionSet();
        party.add(Afliccion.NIEBLA);
        party.add(Afliccion.AVARICIA);
        List<AfflictionMarket.Offer> offers =
                AfflictionMarket.draw(party, List.of(), 6, BASE_REWARD, 1, 1234);
        for (AfflictionMarket.Offer offer : offers) {
            assertFalse(offer.afliccion() == Afliccion.NIEBLA);
            assertFalse(offer.afliccion() == Afliccion.AVARICIA);
        }
    }

    @Test
    void aPersonalAfflictionIsStillOfferedWhileAnyoneCouldTakeIt() {
        AfflictionSet mine = new AfflictionSet();
        mine.add(Afliccion.PLOMO);
        AfflictionSet theirs = new AfflictionSet();
        // One player carries Plomo, the other does not — the offer must survive, or a second player
        // could never take a personal drawback once the first had.
        List<AfflictionMarket.Offer> offers = AfflictionMarket.draw(
                new AfflictionSet(), List.of(mine, theirs), 6, BASE_REWARD, 1, 7);
        assertTrue(offers.stream().anyMatch(o -> o.afliccion() == Afliccion.PLOMO));
    }

    @Test
    void aPersonalAfflictionEveryoneCarriesIsDropped() {
        AfflictionSet a = new AfflictionSet();
        AfflictionSet b = new AfflictionSet();
        a.add(Afliccion.PLOMO);
        b.add(Afliccion.PLOMO);
        List<AfflictionMarket.Offer> offers = AfflictionMarket.draw(
                new AfflictionSet(), List.of(a, b), 6, BASE_REWARD, 1, 7);
        assertTrue(offers.stream().noneMatch(o -> o.afliccion() == Afliccion.PLOMO));
    }

    @Test
    void offersAreDistinct() {
        List<AfflictionMarket.Offer> offers =
                AfflictionMarket.draw(new AfflictionSet(), List.of(), 3, BASE_REWARD, 1, 99);
        assertEquals(3, offers.size());
        assertEquals(3, offers.stream().map(AfflictionMarket.Offer::afliccion).distinct().count());
    }

    @Test
    void aMarketWithNothingLeftToSellShowsNothing() {
        AfflictionSet party = new AfflictionSet();
        AfflictionSet personal = new AfflictionSet();
        Afliccion.all().forEach(a -> {
            party.add(a);
            personal.add(a);
        });
        assertTrue(AfflictionMarket.draw(party, List.of(personal), 3, BASE_REWARD, 1, 1).isEmpty());
    }

    @Test
    void neverDrawsMoreThanTheRoomHasPedestals() {
        assertEquals(2, AfflictionMarket.draw(
                new AfflictionSet(), List.of(), 2, BASE_REWARD, 1, 5).size());
    }

    @Test
    void theRewardFallsWithDepthAndHasAFloor() {
        int first = AfflictionMarket.reward(BASE_REWARD, 1);
        int third = AfflictionMarket.reward(BASE_REWARD, 3);
        int tenth = AfflictionMarket.reward(BASE_REWARD, 10);
        assertEquals(BASE_REWARD, first);
        assertTrue(third < first, "a later affliction is carried for less time, so it pays less");
        assertTrue(tenth > 0 && tenth < third);
        // The floor keeps a deep floor's market from paying nothing at all, which would read as
        // broken rather than as stingy.
        assertEquals(AfflictionMarket.reward(BASE_REWARD, 20),
                AfflictionMarket.reward(BASE_REWARD, 40));
    }

    @Test
    void purgingGetsMoreExpensiveTheDeeperYouDug() {
        assertEquals(0, AfflictionMarket.purgePrice(BASE_PURGE, 0));
        assertEquals(BASE_PURGE, AfflictionMarket.purgePrice(BASE_PURGE, 1));
        assertTrue(AfflictionMarket.purgePrice(BASE_PURGE, 3)
                > AfflictionMarket.purgePrice(BASE_PURGE, 2));
    }

    @Test
    void theSameSeedDrawsTheSameMarket() {
        List<AfflictionMarket.Offer> a =
                AfflictionMarket.draw(new AfflictionSet(), List.of(), 3, BASE_REWARD, 2, 4242);
        List<AfflictionMarket.Offer> b =
                AfflictionMarket.draw(new AfflictionSet(), List.of(), 3, BASE_REWARD, 2, 4242);
        assertEquals(a, b);
    }
}
