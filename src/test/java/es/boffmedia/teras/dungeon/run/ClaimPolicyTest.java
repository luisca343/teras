package es.boffmedia.teras.dungeon.run;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may take a reward, and whether any is left.
 *
 * <p>The four failures this replaces were all in how rewards were <i>delivered</i>: loose items
 * dropped once, on the first player through the door. A latecomer got nothing, one player could take
 * everything, the items despawned, and stage advance swept the rest.</p>
 */
class ClaimPolicyTest {

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BEA = UUID.randomUUID();
    private static final UUID CAM = UUID.randomUUID();

    @Test
    void perPlayerLetsEveryoneClaimExactlyOnce() {
        ClaimPolicy policy = new ClaimPolicy(ClaimPolicy.Kind.PER_PLAYER);

        assertTrue(policy.claim(ANA));
        assertFalse(policy.claim(ANA), "nobody claims twice");
        assertTrue(policy.claim(BEA), "another member is unaffected by the first");
        assertEquals(2, policy.claims());
    }

    /** The failure that started this: arriving late must not mean arriving to nothing. */
    @Test
    void aLatecomerCanStillClaim() {
        ClaimPolicy policy = new ClaimPolicy(ClaimPolicy.Kind.PER_PLAYER);
        policy.claim(ANA);
        policy.claim(BEA);
        assertTrue(policy.mayClaim(CAM), "still fighting two rooms back is not a forfeit");
        assertTrue(policy.claim(CAM));
    }

    @Test
    void oneOfNGoesToWhoeverClaimsFirst() {
        ClaimPolicy policy = new ClaimPolicy(ClaimPolicy.Kind.ONE_OF_N);

        assertTrue(policy.claim(ANA));
        assertFalse(policy.mayClaim(BEA), "there was one of it");
        assertFalse(policy.claim(BEA));
        assertFalse(policy.claim(ANA), "not even the claimant gets a second");
    }

    @Test
    void spentDependsOnThePartySize() {
        ClaimPolicy shared = new ClaimPolicy(ClaimPolicy.Kind.PER_PLAYER);
        shared.claim(ANA);
        assertFalse(shared.spent(3), "two members have not claimed yet");
        shared.claim(BEA);
        shared.claim(CAM);
        assertTrue(shared.spent(3));

        ClaimPolicy single = new ClaimPolicy(ClaimPolicy.Kind.ONE_OF_N);
        assertFalse(single.spent(4));
        single.claim(ANA);
        assertTrue(single.spent(4), "one claim empties it however big the party");
    }

    /** A solo run must not leave a per-player pedestal looking unfinished forever. */
    @Test
    void aSoloPlayerEmptiesAPerPlayerPedestal() {
        ClaimPolicy policy = new ClaimPolicy(ClaimPolicy.Kind.PER_PLAYER);
        policy.claim(ANA);
        assertTrue(policy.spent(1));
    }

    @Test
    void nobodyIsNotAClaimant() {
        ClaimPolicy policy = new ClaimPolicy(ClaimPolicy.Kind.PER_PLAYER);
        assertFalse(policy.mayClaim(null));
        assertFalse(policy.claim(null));
        assertEquals(0, policy.claims());
    }
}
