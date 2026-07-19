package es.boffmedia.teras.dungeon.run;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a coin payout is divided into item entities. The per-stack ceiling is what keeps a deep-floor
 * jackpot from producing a stack the {@code ItemStack} codec refuses to encode — which would lose
 * the coins silently, on chunk save, long after the payout looked fine.
 */
class CoinSplitTest {

    @Test
    void smallPayoutsUseAtMostAHandfulOfStacks() {
        assertEquals(1, CoinSplit.split(1).length);
        assertEquals(2, CoinSplit.split(2).length);
        assertEquals(4, CoinSplit.split(40).length);
    }

    @Test
    void nothingIsSpawnedForANonPositiveAmount() {
        assertEquals(0, CoinSplit.split(0).length);
        assertEquals(0, CoinSplit.split(-5).length);
    }

    /** A stage-12 arcade jackpot: four stacks would be 115 each, past the codec's limit of 99. */
    @Test
    void largePayoutsAddStacksRatherThanOversizedOnes() {
        int[] counts = CoinSplit.split(460);

        assertTrue(counts.length > 4, "still split four ways: " + counts.length);
        for (int count : counts) {
            assertTrue(count <= CoinSplit.MAX_PER_STACK, "stack of " + count + " is too big");
        }
    }

    @Test
    void everyPayoutUpToADeepFloorJackpotSplitsSoundly() {
        for (int amount = 1; amount <= 2000; amount++) {
            int total = 0;
            for (int count : CoinSplit.split(amount)) {
                assertTrue(count >= 1, amount + " coins: produced an empty stack");
                assertTrue(count <= CoinSplit.MAX_PER_STACK,
                        amount + " coins: stack of " + count + " exceeds the cap");
                total += count;
            }
            assertEquals(amount, total, amount + " coins: the split does not add up");
        }
    }
}
