package es.boffmedia.teras.dungeon.piso;

/**
 * How the size of the party reaches the wave it fights.
 *
 * <p>Until this existed nothing scaled with the party at all: a lone runner and four players met
 * identical waves, so every enemy was four times easier with a full group. That is a defensible
 * choice — <i>The Binding of Isaac</i> is single-player and its co-op mode does exactly nothing —
 * but it is only defensible if it is chosen, and a four-floor dungeon built for parties had never
 * chosen it.</p>
 *
 * <p>The axes follow {@link Dificultad}'s discipline for the same reason: two knobs that both
 * multiply the same four numbers compound, and depth is already spending that budget. So the split
 * is deliberately lopsided:</p>
 *
 * <ul>
 *   <li><b>Count carries it.</b> More players should mean more to fight — bodies to spread between
 *       them, and enough targets that a room does not evaporate before the back of the party
 *       arrives. It is also the axis rooms are authored against: marker counts already bound it.</li>
 *   <li><b>Health moves a little.</b> Four players focus one enemy far faster than one does, so a
 *       small bump keeps an elite alive long enough to be an elite. Small, because it is the axis
 *       {@code dificultad} already leans hardest on.</li>
 *   <li><b>Damage does not move at all.</b> Joining a friend must never make the game hit harder;
 *       that punishes the group for grouping, and it is the one axis that removes decisions rather
 *       than testing them.</li>
 * </ul>
 *
 * <p>The wave is still bounded by the room: {@code EnemySpawner} cycles spawn markers, so a wave
 * larger than a room's markers stacks enemies on one block. {@link #MAX_PARTY_FACTOR} keeps the
 * count inside what an authored room can hold — the audit's {@code waveMax} is derived from the
 * same ceiling, so a room that passes {@code piso auditar} can hold a full party's wave.</p>
 */
public final class PartyScaling {
    private PartyScaling() {}

    /** The party size the tables are written for. One player is the baseline, not four. */
    public static final int BASELINE = 1;

    /** Extra wave per additional player. Four players fetch roughly twice the enemies. */
    private static final double COUNT_PER_PLAYER = 0.35;
    /** Extra health per additional player, so focus fire does not delete an elite on arrival. */
    private static final double HEALTH_PER_PLAYER = 0.10;

    /**
     * The most the count may be multiplied by, whatever the party. Rooms are authored to a fixed
     * marker count and a fixed floor area; past this a wave crowds a chamber rather than
     * challenging it, and the spawner starts stacking bodies on used blocks.
     */
    public static final double MAX_PARTY_FACTOR = 2.05;

    /** Party size clamped to something sane — a missing or corrupt party must not scale anything. */
    public static int clamp(int players) {
        return Math.max(BASELINE, Math.min(8, players));
    }

    /** Wave size multiplier for a party of {@code players}. */
    public static double count(int players) {
        double factor = 1.0 + (clamp(players) - BASELINE) * COUNT_PER_PLAYER;
        return Math.min(MAX_PARTY_FACTOR, factor);
    }

    /** Max-health multiplier for a party of {@code players}. */
    public static double health(int players) {
        return 1.0 + (clamp(players) - BASELINE) * HEALTH_PER_PLAYER;
    }

    /**
     * Damage never scales with the party. A method rather than an omission so the decision is
     * visible at the call site and cannot be quietly reintroduced by someone wiring "the other two".
     */
    public static double damage(int players) {
        return 1.0;
    }
}
