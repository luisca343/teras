package es.boffmedia.teras.dungeon.combat;

/**
 * What one connecting hit is worth. The whole of it — this is the formula vanilla's is replaced by
 * inside a run (ROGUELIKE §4.2), and it runs in both directions so one tuning pass moves players and
 * enemies together.
 *
 * <h2>Why mitigation is a curve and not a subtraction</h2>
 *
 * <p>Flat subtraction has one failure mode and it arrives on schedule: at floor 1 a few points of
 * armour cancel a whole hit, and by floor 12 the same points are a rounding error, so armour is
 * either everything or nothing and never a stat. The ratio {@code A / (A + K)} has neither end —
 * it approaches total immunity without reaching it, so armour always does <i>something</i> and
 * penetration always has something to bite.</p>
 *
 * <h2>Why K grows with depth</h2>
 *
 * <p>Because the numbers inflate and the <i>feel</i> must not. If K were fixed, a floor-12 enemy
 * carrying floor-12 armour would sit at 95% mitigation and the run would end in a wall. Scaling K
 * with the floor keeps a given armour <i>budget</i> reading as the same fraction at every depth, so
 * "this one is armoured" means the same thing on floor 1 and floor 12 while both sides' raw numbers
 * grow. {@link #armourFor} is the inverse, and is how the bestiary gets authored: you choose the
 * fraction you want an enemy to feel like and it tells you the number to write.</p>
 *
 * <p>Pure — no Minecraft, no randomness of its own. The crit roll arrives as a parameter so a test
 * can pin it.</p>
 */
public final class DamageMath {
    private DamageMath() {}

    /** Armour constant at floor 1. */
    static final double K_BASE = 20;

    /** How much the constant grows per floor descended. */
    static final double K_PER_FLOOR = 6;

    /**
     * The ceiling on mitigation.
     *
     * <p>Nothing in the dungeon is immune to anything. A combatant that cannot be hurt by a build
     * that has no penetration is a wall the player is told to walk away from, and there is nowhere
     * to walk to on a sealed floor.</p>
     */
    static final double MAX_MITIGATION = 0.85;

    /** One resolved swing: what it did, and whether it was a crit — which the caller has to show. */
    public record Hit(double damage, boolean crit) {}

    /** The armour constant at a given floor. Depth is 1-based; anything lower is treated as floor 1. */
    public static double armourConstant(int depth) {
        return K_BASE + K_PER_FLOOR * (Math.max(1, depth) - 1);
    }

    /** The fraction of a hit the target's armour removes, after penetration eats into it. */
    public static double mitigation(double armour, double penetration, int depth) {
        double effective = Math.max(0, armour) * (1 - clamp01(penetration));
        if (effective <= 0) {
            return 0;
        }
        return Math.min(MAX_MITIGATION, effective / (effective + armourConstant(depth)));
    }

    /**
     * Resolves a swing.
     *
     * @param critRoll a roll in [0,1) from the caller's randomness — passed in rather than drawn
     *                 here so the arithmetic stays pure and a test can pin the outcome
     */
    public static Hit resolve(StatBlock attacker, StatBlock defender, int depth, double critRoll) {
        boolean crit = critRoll < attacker.get(Stat.CRITICO);
        double swing = attacker.get(Stat.DANO);
        if (crit) {
            swing *= attacker.get(Stat.CONTUNDENCIA);
        }
        double mitigated = swing * (1 - mitigation(
                defender.get(Stat.ARMADURA), attacker.get(Stat.PENETRACION), depth));
        return new Hit(Math.max(0, mitigated), crit);
    }

    /**
     * The armour value that produces {@code fraction} mitigation at {@code depth} against an
     * attacker with no penetration — the authoring direction, so a bestiary is written in feel and
     * stored in numbers.
     */
    public static double armourFor(double fraction, int depth) {
        double target = Math.min(MAX_MITIGATION, clamp01(fraction));
        if (target <= 0) {
            return 0;
        }
        return armourConstant(depth) * target / (1 - target);
    }

    private static double clamp01(double value) {
        return Math.min(1, Math.max(0, value));
    }
}
