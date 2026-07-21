package es.boffmedia.teras.dungeon.piso;

/**
 * How a tramo's {@code dificultad} reaches the things it scales.
 *
 * <p>One knob is the right interface — a tramo says "harder than the last" once — but it must not
 * be one multiply. Dificultad scales enemy count, health, damage <i>and</i> the bias toward elite
 * entries; applied flat to all of them a value of 1.8 lands as roughly 1.8&#179; &asymp; 5.8&times;
 * the threat before the roster shift is counted, which is how a difficulty knob turns a late floor
 * from tense into impossible. The compounding is the whole hazard, so each axis gets its own curve
 * and the product is what the tests hold.</p>
 *
 * <p>The split between them is a design statement, not a tuning detail:</p>
 *
 * <ul>
 *   <li><b>Health carries the most.</b> A tougher enemy lengthens a fight, which is what "deeper"
 *       should feel like. It is also the axis a party can answer with better gear.</li>
 *   <li><b>Count grows slowly.</b> Rooms have a fixed number of spawn markers and a fixed floor
 *       area; doubling a wave crowds it rather than making it harder, and past the marker count the
 *       spawner starts stacking enemies on one block.</li>
 *   <li><b>Damage grows slowest.</b> It is the only axis that can remove a player's decisions
 *       rather than test them — at some multiple every mistake is fatal and the floor stops being
 *       readable.</li>
 *   <li><b>The roster shift does the rest.</b> Meeting different enemies is what makes depth
 *       interesting; meeting the same ones with bigger numbers is what makes it a grind.</li>
 * </ul>
 *
 * <p>Rewards are deliberately not here: {@code coinStageScalingPct} already scales payout by
 * absolute floor, and running both off one number would make a shared piso's rewards depend on
 * which dungeon referenced it.</p>
 */
public final class Dificultad {
    private Dificultad() {}

    /** Each axis's share of the excess over 1.0. Health takes it whole; the others are fractions. */
    private static final double COUNT_SHARE = 0.35;
    private static final double HEALTH_SHARE = 1.0;
    private static final double DAMAGE_SHARE = 0.25;

    /** Elite weighting is exponential because a linear bias barely moves a weighted draw. */
    private static final double ELITE_EXPONENT = 1.6;

    /**
     * The ceiling the curves are designed against. A tramo asking for more is almost certainly a
     * typo — {@code dificultad: 18} for {@code 1.8} — and clamping is kinder than building a floor
     * nothing can clear.
     */
    public static final double MAX = 3.0;

    public static double clamp(double dificultad) {
        if (!Double.isFinite(dificultad) || dificultad <= 0) {
            return 1.0;
        }
        return Math.min(MAX, dificultad);
    }

    /** Wave size multiplier. */
    public static double count(double dificultad) {
        return share(dificultad, COUNT_SHARE);
    }

    /** Max-health multiplier. */
    public static double health(double dificultad) {
        return share(dificultad, HEALTH_SHARE);
    }

    /** Attack-damage multiplier. */
    public static double damage(double dificultad) {
        return share(dificultad, DAMAGE_SHARE);
    }

    /**
     * What an entry marked {@code elite} has its weight multiplied by. Ordinary entries keep theirs,
     * so this shifts the mix without needing the table to be rewritten per depth.
     */
    public static double eliteWeight(double dificultad) {
        return Math.pow(clamp(dificultad), ELITE_EXPONENT);
    }

    /**
     * The combined stat multiplier, which is the number the compounding caution is about. Exposed
     * so it can be asserted directly rather than inferred from the three curves separately.
     */
    public static double combined(double dificultad) {
        return count(dificultad) * health(dificultad) * damage(dificultad);
    }

    private static double share(double dificultad, double weight) {
        return 1.0 + (clamp(dificultad) - 1.0) * weight;
    }
}
