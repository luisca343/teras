package es.boffmedia.teras.dungeon.combat;

/**
 * Poise, as a state machine: what a heavy attack is <i>for</i>.
 *
 * <h2>Why the loop needs it</h2>
 *
 * <p>Without poise a rebuilt melee register is vanilla with different numbers — light and heavy
 * differ only in how much they subtract, so the correct play is always whichever divides better into
 * the target's health, and the wind-up on a heavy is a pure cost. Poise makes the heavy buy
 * <b>something light attacks cannot</b>: an opening.</p>
 *
 * <h2>The two rules that keep it from becoming a stun-lock</h2>
 *
 * <p><b>Breaking refills it.</b> A staggered target comes back at full poise, so a party cannot
 * chain-stagger anything to death — the second break has to be earned exactly as the first was. A
 * boss held permanently open is not a fight, and four players make that trivially reachable in a way
 * a single-player game never has to consider.</p>
 *
 * <p><b>Poise recovers after quiet, not continuously.</b> Chip damage alone would otherwise
 * accumulate into a guaranteed break over a long enough fight, which makes the heavy attack
 * redundant again by a slower route. The break has to be earned inside a <i>window</i>, and
 * {@link #REGEN_DELAY_TICKS} is that window's length.</p>
 *
 * <p>Pure — no Minecraft. Time arrives as tick counts.</p>
 */
public final class Aplomo {

    /** How long a broken guard stays open. Two seconds: long enough to commit to, short enough to miss. */
    public static final int STAGGER_TICKS = 40;

    /**
     * What a plain enemy swing takes out of a player's guard.
     *
     * <p>Flat, for the reason {@link SwingState#poiseChip} gives at length: chipping by damage makes
     * poise scale with the stat that already wins fights. Deliberately smaller than the player's own
     * opener, because a player has one guard and a room has several enemies — at 2 a chain of ordinary
     * swings will break a 6-poise player, and it takes a while, which is what makes the esquiva the
     * answer rather than a nicety.</p>
     *
     * <p>Stage 2 gives each enemy its own chip and this becomes the fallback for one that names
     * none.</p>
     */
    public static final double ENEMY_CHIP = 2;

    /** Quiet time before poise begins to come back. */
    public static final int REGEN_DELAY_TICKS = 60;

    /** Once it starts, a full bar returns over this long. */
    public static final int REGEN_TICKS = 40;

    private final double max;
    private double current;
    private int staggerTicks;
    private int quietTicks;

    public Aplomo(double max) {
        this.max = Math.max(0, max);
        this.current = this.max;
    }

    public double max() {
        return max;
    }

    public double current() {
        return current;
    }

    public boolean staggered() {
        return staggerTicks > 0;
    }

    /** How much longer the opening lasts, for whoever draws the tell. */
    public int staggerRemaining() {
        return staggerTicks;
    }

    /**
     * Takes a bite out of the guard.
     *
     * @return true if this is the blow that broke it — the caller owes a tell and an opening
     */
    public boolean chip(double amount) {
        if (max <= 0 || amount <= 0 || staggered()) {
            // A target already open cannot be broken again: the chip would shorten nothing and the
            // second tell would read as a bug.
            return false;
        }
        quietTicks = 0;
        current -= amount;
        if (current > 0) {
            return false;
        }
        current = max;
        staggerTicks = STAGGER_TICKS;
        return true;
    }

    /** One tick of the world: the opening closes, or the guard comes back. */
    public void tick() {
        if (staggerTicks > 0) {
            staggerTicks--;
            return;
        }
        if (current >= max) {
            return;
        }
        if (quietTicks < REGEN_DELAY_TICKS) {
            quietTicks++;
            return;
        }
        current = Math.min(max, current + max / REGEN_TICKS);
    }

    /** Back to full, guard closed — what a fresh room or a revived combatant gets. */
    public void reset() {
        current = max;
        staggerTicks = 0;
        quietTicks = 0;
    }
}
