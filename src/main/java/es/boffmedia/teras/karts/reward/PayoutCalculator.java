package es.boffmedia.teras.karts.reward;

import es.boffmedia.teras.karts.engine.RaceResult;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Turns a finished race into who gets paid what.
 *
 * <p>Pure and separate from the paying so the money rules can be unit-tested — an economy bug that
 * only shows up on a live server is expensive to find, and this is the one part of karts that
 * touches real player balances.</p>
 */
public final class PayoutCalculator {
    private PayoutCalculator() {}

    /** Config key for the payment everyone who finishes gets, on top of any placing money. */
    public static final String PARTICIPATION_KEY = "participacion";
    /** Config key for the Grand Prix champion bonus. */
    public static final String CHAMPION_KEY = "campeonGp";

    /**
     * What each racer earns.
     *
     * <p>Retirements earn nothing at all — not even the participation payment. Paying someone who
     * quit the same as someone who struggled home last would make quitting the rational move on a
     * bad lap, which is exactly the behaviour a participation payment is meant to discourage.</p>
     *
     * @param payouts looks up a config key: {@code "1"}, {@code "2"}, … for placings, plus
     *                {@link #PARTICIPATION_KEY}. Unknown keys must return zero, not null.
     */
    public static Map<UUID, BigDecimal> calculate(RaceResult result,
                                                  Function<String, BigDecimal> payouts) {
        Map<UUID, BigDecimal> earnings = new LinkedHashMap<>();
        if (result == null) {
            return earnings;
        }
        BigDecimal participation = orZero(payouts.apply(PARTICIPATION_KEY));

        for (RaceResult.Placement placement : result.placements()) {
            if (placement.dnf()) {
                continue;
            }
            BigDecimal placeMoney = orZero(payouts.apply(String.valueOf(placement.position())));
            BigDecimal total = placeMoney.add(participation);
            if (total.signum() > 0) {
                earnings.merge(placement.playerId(), total, BigDecimal::add);
            }
        }
        return earnings;
    }

    /** The championship bonus, paid on top of the final round's own payout. */
    public static BigDecimal championBonus(Function<String, BigDecimal> payouts) {
        return orZero(payouts.apply(CHAMPION_KEY));
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
