package es.boffmedia.teras.dungeon.run;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Who may take a reward off a pedestal, and whether anyone still can.
 *
 * <p>Rewards used to be loose {@link net.minecraft.world.entity.item.ItemEntity}s dropped on the
 * marker the moment the first player crossed the threshold. Four things followed, all bad: a player
 * still fighting two rooms back arrived to a bare pedestal, one player could walk across the pile
 * and take all of it, the items despawned after five minutes, and stage advance swept whatever was
 * left. A pedestal you claim fixes all four at once, and turns "who gets this" into a per-room
 * setting instead of two different mechanisms.</p>
 *
 * <h2>The rule</h2>
 *
 * <p><b>Common gear is per-player. Rare and epic are one-of-N.</b> Legible enough to hold in your
 * head — if it is shiny, there is one of it. Nobody races for the ordinary things, and the argument
 * is reserved for the good ones, where it is dramatic instead of petty.</p>
 *
 * <p>Pure, so the bookkeeping is testable without a server; the pedestal that owns one of these
 * knows about displays and item stacks.</p>
 */
public final class ClaimPolicy {

    public enum Kind {
        /**
         * Every party member may claim once, each getting their own roll. Nothing to race for,
         * because nobody can see or take another player's share.
         */
        PER_PLAYER,
        /**
         * One reward, first claim takes it. Rolled when the pedestal arms rather than when it is
         * claimed, so the party can see what they are deciding about.
         */
        ONE_OF_N
    }

    private final Kind kind;
    private final Set<UUID> claimed = new LinkedHashSet<>();

    public ClaimPolicy(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    /** Whether {@code member} can still take something from this pedestal. */
    public boolean mayClaim(UUID member) {
        if (member == null) {
            return false;
        }
        return kind == Kind.PER_PLAYER ? !claimed.contains(member) : claimed.isEmpty();
    }

    /**
     * Records a claim. False when it was not allowed, so a caller can use the return as the test
     * rather than asking twice and racing itself.
     */
    public boolean claim(UUID member) {
        return mayClaim(member) && claimed.add(member);
    }

    /** Whether anything is left for anyone. A spent pedestal goes quiet rather than disappearing. */
    public boolean spent(int partySize) {
        return kind == Kind.ONE_OF_N ? !claimed.isEmpty() : claimed.size() >= Math.max(1, partySize);
    }

    public int claims() {
        return claimed.size();
    }

    public Set<UUID> claimants() {
        return Set.copyOf(claimed);
    }
}
