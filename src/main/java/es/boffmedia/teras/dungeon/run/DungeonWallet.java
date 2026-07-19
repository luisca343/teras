package es.boffmedia.teras.dungeon.run;

/**
 * The party's purse for one run: coins picked up off dead enemies, plus the wall-breaker charges
 * bought with them. Shared rather than per-player, so a clear is not a scramble to hoover the drops
 * and a broke member can still be handed a potion — the trade is that one member's gambling spends
 * the team's money, which is the intended social friction (DUNGEONS.md §7).
 *
 * <p>Coins are <b>not</b> money. They exist only inside a run and never touch
 * {@link es.boffmedia.teras.economy.EconomyStore}: the single conversion to ₽ happens when a run is
 * completed, in {@code DungeonRunManager.completeRun}. Nothing here knows about Minecraft, so the
 * arithmetic is unit-testable.</p>
 */
public final class DungeonWallet {

    private int coins;
    private int wallCharges;
    private int totalEarned;
    private int totalSpent;

    public int coins() {
        return coins;
    }

    public int wallCharges() {
        return wallCharges;
    }

    /** Coins picked up over the whole run, for the results report — not reduced by spending. */
    public int totalEarned() {
        return totalEarned;
    }

    public int totalSpent() {
        return totalSpent;
    }

    /** Negative and zero amounts are ignored rather than silently draining the purse. */
    public void add(int amount) {
        if (amount <= 0) {
            return;
        }
        coins += amount;
        totalEarned += amount;
    }

    /** Charges into the shared stock: bought at the shop, occasionally found in a reward roll. */
    public void addCharges(int amount) {
        if (amount > 0) {
            wallCharges += amount;
        }
    }

    /** False leaves the wallet untouched — callers price first and act only on success. */
    public boolean trySpend(int price) {
        if (price < 0 || coins < price) {
            return false;
        }
        coins -= price;
        totalSpent += price;
        return true;
    }

    /** One charge for one secret wall; false when the party has none and the wall stays shut. */
    public boolean tryUseCharge() {
        if (wallCharges <= 0) {
            return false;
        }
        wallCharges--;
        return true;
    }

    /**
     * Empties the purse and returns what was in it — the run cashing out. Emptying matters even
     * though the run ends immediately after: leaving the coins in a wallet that has already been
     * paid for is state that reads as money the party still holds.
     */
    public int cashOut() {
        int held = coins;
        coins = 0;
        return held;
    }

    /**
     * Death costs the party a slice of what it is carrying. Returns what was lost, for the message
     * — spent coins are gone, so this does not touch {@link #totalSpent}.
     */
    public int applyDeathPenalty(int percent) {
        if (percent <= 0 || coins <= 0) {
            return 0;
        }
        int lost = Math.min(coins, (int) Math.ceil(coins * (double) percent / 100.0));
        coins -= lost;
        return lost;
    }
}
