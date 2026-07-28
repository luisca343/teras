package es.boffmedia.teras.dungeon.combat;

/**
 * One player's esquiva: when they may roll again, and the window in which nothing can touch them.
 *
 * <h2>Why this is ours and not ParCool's</h2>
 *
 * <p>ROGUELIKE ruling 4 originally borrowed ParCool's roll. It could not: ParCool is deliberately not
 * a Teras dependency, the whole integration is one-way console commands, and the mod is client-driven
 * — the server never learns that a roll happened. The deeper objection is that a rebuild whose
 * premise is <i>we own every window and number</i> cannot rent out the one verb every dodge boon
 * attaches to. In Hades the dash is the most-modified verb in the game, and ESQUIVA will be the
 * same.</p>
 *
 * <h2>Deadlines, not counters</h2>
 *
 * <p>Both fields are absolute tick deadlines rather than counting-down timers, so nothing has to be
 * ticked for the state to stay correct. A player who logs out mid-roll and returns has an expired
 * window rather than a frozen one, and no bookkeeping pass can forget an entity.</p>
 *
 * <p>Pure — no Minecraft. The current tick arrives as a parameter.</p>
 */
public final class DodgeState {

    /** How long the roll makes you untouchable. Generous enough to read, short enough to time. */
    public static final int IFRAME_TICKS = 7;

    /** The base wait between rolls, before {@link Stat#ENFRIAMIENTO} scales it. */
    public static final int BASE_COOLDOWN_TICKS = 40;

    private long readyAtTick;
    private long invulnerableUntilTick;

    /**
     * The cooldown a sheet earns.
     *
     * <p>{@code enfriamiento} is a <i>rate</i> — 2 means cooldowns tick twice as fast — so it divides
     * the wait rather than subtracting from it. That keeps every point of it worth the same
     * proportion, and keeps the result above zero for any legal value of the stat.</p>
     */
    public static int cooldownTicks(double enfriamiento) {
        double rate = Math.max(0.1, enfriamiento);
        return Math.max(1, (int) Math.round(BASE_COOLDOWN_TICKS / rate));
    }

    public boolean ready(long now) {
        return now >= readyAtTick;
    }

    public long readyIn(long now) {
        return Math.max(0, readyAtTick - now);
    }

    /**
     * Rolls, if the cooldown allows it.
     *
     * @return false when it was not ready — the caller should not move the player or play anything
     */
    public boolean begin(long now, double enfriamiento) {
        if (!ready(now)) {
            return false;
        }
        invulnerableUntilTick = now + IFRAME_TICKS;
        readyAtTick = now + cooldownTicks(enfriamiento);
        return true;
    }

    /** Whether an incoming hit lands at all. */
    public boolean invulnerable(long now) {
        return now < invulnerableUntilTick;
    }

    /** Clears both windows — what leaving a run or dying owes. */
    public void reset() {
        readyAtTick = 0;
        invulnerableUntilTick = 0;
    }
}
