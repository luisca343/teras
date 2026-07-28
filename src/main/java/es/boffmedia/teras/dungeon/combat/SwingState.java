package es.boffmedia.teras.dungeon.combat;

/**
 * One player's melee register: where they are in the light chain.
 *
 * <h2>What the chain is for</h2>
 *
 * <p>A combo that only adds damage is a damage bonus with extra steps. This one adds
 * <b>{@link Aplomo poise}</b>: the first two lights barely scratch a guard and the third bites, so
 * the chain is how a light weapon opens something up, and dropping out of it to reposition costs that
 * progress. That is the decision the window exists to create — press on, or step back.</p>
 *
 * <h2>Why there is no heavy attack</h2>
 *
 * <p>PESADO was built and then <b>removed</b>, and the reason is worth keeping: it lived on
 * right-click, and right-click was already taken. It raises a shield — {@code GUARDIA} is a verb in
 * its own right — and it opens every fixture in the dungeon, talks to both NPCs, and fires a gadget.
 * A second melee verb sharing that button meant a party could not block while armed, and no
 * allocation of the input read as anything but a special case. So the chain is the whole melee
 * register: the finisher is the stagger tool, which is what this class always said the chain was
 * for.</p>
 *
 * <p>Poise is therefore broken by <i>sustained</i> pressure rather than by one committed blow. A
 * chain is worth 5 against a guard and stepping away resets it, so an opening is something you keep
 * up rather than something you spend — a slower answer than a heavy, and one that still makes
 * {@link Stat#APLOMO} the axis that decides how hard a thing is to break.</p>
 *
 * <p>Pure — no Minecraft. Time arrives as tick counts.</p>
 */
public final class SwingState {

    /** How long the chain survives between hits. Long enough to reposition, short enough to lose. */
    public static final int COMBO_WINDOW_TICKS = 20;

    /** Hits in a chain before it starts over. */
    public static final int COMBO_LENGTH = 3;

    /** What each step of the light chain takes out of a guard. */
    public static final double OPENER_CHIP = 1;
    public static final double FINISHER_CHIP = 3;

    /** What hitting something already open is worth — the payoff a broken guard exists to give. */
    public static final double STAGGER_DAMAGE = 1.5;

    private int step;
    /** Far enough back that the first swing of a session always starts a fresh chain. */
    private long lastLightTick = -COMBO_WINDOW_TICKS - 1;

    /**
     * Registers a light hit and returns which step of the chain it was, from 1.
     *
     * <p>A hit outside the window starts over rather than continuing, which is the whole reason the
     * window is a decision and not a formality.</p>
     */
    public int light(long now) {
        if (now - lastLightTick > COMBO_WINDOW_TICKS) {
            step = 0;
        }
        lastLightTick = now;
        step = step % COMBO_LENGTH + 1;
        return step;
    }

    /** Where the chain currently stands, or 0 between chains. */
    public int step() {
        return step;
    }

    /** The finisher lands harder; the openers do not. */
    public static double damageMultiplier(int step) {
        return step >= COMBO_LENGTH ? 1.4 : 1.0;
    }

    /**
     * What this step takes out of a guard — <b>a flat amount decided by the verb, never by how much
     * the hit hurt</b>.
     *
     * <p>Chipping by damage was the first attempt and it was wrong twice. It made poise scale with the
     * one stat that already wins fights, so the best damage build was automatically the best stagger
     * build and the light/heavy distinction stopped mattering; and because a shipped enemy's poise was
     * derived from its health, three ordinary swings broke anything, forever, since breaking refills. A
     * flat chip makes poise its own axis: an enemy is hard to stagger because it was <i>authored</i>
     * hard to stagger, not because it happens to be tanky.</p>
     */
    public static double poiseChip(int step) {
        return step >= COMBO_LENGTH ? FINISHER_CHIP : OPENER_CHIP;
    }

    /** Drops the chain. */
    public void reset() {
        step = 0;
        lastLightTick = -COMBO_WINDOW_TICKS - 1;
    }
}
