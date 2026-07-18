package es.boffmedia.teras.karts.reward;

import es.boffmedia.teras.karts.engine.RaceResult;
import es.boffmedia.teras.karts.mode.ClassicMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Race winnings. This is the only part of karts that moves real player balances, so the rules are
 * pinned here rather than discovered on a live server.
 */
class PayoutCalculatorTest {

    private static final UUID ANA = UUID.nameUUIDFromBytes("ana".getBytes());
    private static final UUID BEA = UUID.nameUUIDFromBytes("bea".getBytes());
    private static final UUID CARLOS = UUID.nameUUIDFromBytes("carlos".getBytes());

    private static final Map<String, BigDecimal> TABLE = Map.of(
            "1", BigDecimal.valueOf(1000),
            "2", BigDecimal.valueOf(500),
            "3", BigDecimal.valueOf(250),
            PayoutCalculator.PARTICIPATION_KEY, BigDecimal.valueOf(50),
            PayoutCalculator.CHAMPION_KEY, BigDecimal.valueOf(2000));

    private static final Function<String, BigDecimal> PAYOUTS =
            key -> TABLE.getOrDefault(key, BigDecimal.ZERO);

    private static RaceResult result(RaceResult.Placement... placements) {
        return new RaceResult("playa", ClassicMode.ID, 3, List.of(placements));
    }

    private static RaceResult.Placement finished(UUID player, String name, int position) {
        return new RaceResult.Placement(player, name, position, 60_000L, 19_000L, 3, false);
    }

    private static RaceResult.Placement retired(UUID player, String name, int position) {
        return new RaceResult.Placement(player, name, position, -1, -1, 1, true);
    }

    @Test
    @DisplayName("placings pay out, each with the participation payment on top")
    void paysPlacingsPlusParticipation() {
        Map<UUID, BigDecimal> earnings = PayoutCalculator.calculate(result(
                finished(ANA, "Ana", 1),
                finished(BEA, "Bea", 2),
                finished(CARLOS, "Carlos", 3)), PAYOUTS);

        assertEquals(BigDecimal.valueOf(1050), earnings.get(ANA));
        assertEquals(BigDecimal.valueOf(550), earnings.get(BEA));
        assertEquals(BigDecimal.valueOf(300), earnings.get(CARLOS));
    }

    @Test
    @DisplayName("a retirement earns nothing, not even the participation payment")
    void retirementsEarnNothing() {
        Map<UUID, BigDecimal> earnings = PayoutCalculator.calculate(result(
                finished(ANA, "Ana", 1),
                retired(BEA, "Bea", 2)), PAYOUTS);

        // Paying a quitter the same as someone who limped home last would make quitting the
        // rational move on a bad lap.
        assertFalse(earnings.containsKey(BEA));
        assertEquals(BigDecimal.valueOf(1050), earnings.get(ANA));
    }

    @Test
    @DisplayName("finishing outside the paying positions still earns the participation payment")
    void unplacedFinishersStillEarnParticipation() {
        Map<UUID, BigDecimal> earnings = PayoutCalculator.calculate(result(
                finished(ANA, "Ana", 1),
                finished(BEA, "Bea", 7)), PAYOUTS);

        assertEquals(BigDecimal.valueOf(50), earnings.get(BEA));
    }

    @Test
    @DisplayName("nobody is credited zero — an empty payout table pays no one")
    void zeroEarningsAreOmitted() {
        Map<UUID, BigDecimal> earnings = PayoutCalculator.calculate(
                result(finished(ANA, "Ana", 1)), key -> BigDecimal.ZERO);

        assertTrue(earnings.isEmpty());
    }

    @Test
    @DisplayName("an unknown payout key is treated as zero rather than failing")
    void unknownKeysAreZero() {
        Map<UUID, BigDecimal> earnings = PayoutCalculator.calculate(
                result(finished(ANA, "Ana", 99)), PAYOUTS);

        assertEquals(BigDecimal.valueOf(50), earnings.get(ANA));
    }

    @Test
    @DisplayName("a null payout lookup is treated as zero, not a crash mid-payout")
    void nullPayoutsAreZero() {
        Map<UUID, BigDecimal> earnings = PayoutCalculator.calculate(
                result(finished(ANA, "Ana", 1)), key -> null);

        assertTrue(earnings.isEmpty());
    }

    @Test
    @DisplayName("a null result pays nobody")
    void nullResultPaysNobody() {
        assertTrue(PayoutCalculator.calculate(null, PAYOUTS).isEmpty());
    }

    @Test
    @DisplayName("the champion bonus is read from its own key")
    void championBonus() {
        assertEquals(BigDecimal.valueOf(2000), PayoutCalculator.championBonus(PAYOUTS));
        assertEquals(BigDecimal.ZERO, PayoutCalculator.championBonus(key -> null));
    }
}
