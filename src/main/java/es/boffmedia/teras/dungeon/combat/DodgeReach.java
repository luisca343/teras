package es.boffmedia.teras.dungeon.combat;

/**
 * How far an esquiva may carry, given what stands in front of it.
 *
 * <p>The roll is a raw impulse held for {@link DodgeState#IFRAME_TICKS} ticks, which at the shipped
 * numbers is nearly six blocks of travel in seven ticks. Minecraft's collision resolver runs per
 * tick against the whole step, so a wall thinner than one tick's movement can be passed through
 * entirely. Dungeon walls are solid structures today, which is why this has never been seen — but a
 * decorative pillar or a one-block partition in a future room template is all it would take.</p>
 *
 * <p>Pure — no Minecraft. The distance to whatever the ray hit arrives as a parameter.</p>
 */
public final class DodgeReach {
    private DodgeReach() {}

    /**
     * How far short of the wall the roll stops. Without it the player finishes flush against the
     * face and the next tick's push resolves them into it.
     */
    public static final double SKIN = 0.35;

    /** The blocks a dodge covers at {@code impulse} if nothing stops it. */
    public static double travel(double impulse) {
        return impulse * DodgeState.IFRAME_TICKS;
    }

    /**
     * The impulse to apply given {@code free} blocks of clear space ahead.
     *
     * <p>Negative when there is nothing in the way — the caller passes the raycast's distance, and a
     * miss means the full impulse. A wall closer than {@link #SKIN} yields zero: the player stays
     * put, and keeps their i-frames, which is the half of the dodge that was defending them.</p>
     */
    public static double clamp(double impulse, double free) {
        if (free < 0 || free >= travel(impulse)) {
            return impulse;
        }
        double reachable = Math.max(0, free - SKIN);
        return impulse * (reachable / travel(impulse));
    }
}
