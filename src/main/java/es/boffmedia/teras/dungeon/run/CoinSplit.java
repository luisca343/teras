package es.boffmedia.teras.dungeon.run;

/**
 * How a coin payout is divided across item entities. Free of Minecraft so the arithmetic can be
 * tested directly — {@link CoinDrops} only spawns what this returns.
 *
 * <p>The per-stack ceiling is load-bearing rather than cosmetic: {@code ItemStack.CODEC} validates
 * {@code count} against {@code intRange(1, 99)}, so a stack over that fails to encode and the
 * entity is dropped the moment its chunk saves. A deep-floor arcade jackpot runs to several
 * hundred coins, which a fixed split would put well past it.</p>
 */
public final class CoinSplit {
    private CoinSplit() {}

    /** Entities a payout prefers to use, so a boss reads as a burst of money rather than one item. */
    private static final int PREFERRED_STACKS = 4;

    /** Never more than this in one stack; see the class note on why. */
    static final int MAX_PER_STACK = 64;

    /**
     * The stack counts {@code amount} becomes, largest first. Always sums back to {@code amount};
     * empty for a non-positive amount.
     */
    public static int[] split(int amount) {
        if (amount <= 0) {
            return new int[0];
        }
        int stacks = Math.max(Math.min(PREFERRED_STACKS, amount),
                (amount + MAX_PER_STACK - 1) / MAX_PER_STACK);
        int each = amount / stacks;
        int remainder = amount % stacks;
        int[] counts = new int[stacks];
        for (int i = 0; i < stacks; i++) {
            counts[i] = each + (i < remainder ? 1 : 0);
        }
        return counts;
    }
}
