package es.boffmedia.teras.dungeon.combat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The truth table that decides how much of a hit the engine owns.
 *
 * <p>Worth testing at this length because the arithmetic was always tested and it was this
 * classification that was wrong: treating every hit with a living attacker as a swing threw away the
 * authored amount of seven shipped damage sites, and ran the verb block on two of them.</p>
 */
class HitClassTest {

    @Test
    @DisplayName("a body landing directly under an attack type is a swing")
    void directAttackIsMelee() {
        assertEquals(HitClass.MELEE, HitClass.of(true, true, true));
    }

    @Test
    @DisplayName("no attacker means the engine does not touch it — this is what keeps a price a price")
    void noAttackerIsUntouched() {
        assertEquals(HitClass.UNATTRIBUTED, HitClass.of(false, false, false));
        // A source with no entity still reports its type; neither flag may promote it.
        assertEquals(HitClass.UNATTRIBUTED, HitClass.of(false, true, true));
    }

    @Test
    @DisplayName("a projectile is attributed, not a swing: the shooter is not the thing that hit you")
    void projectileIsAttributed() {
        assertEquals(HitClass.ATTRIBUTED, HitClass.of(true, false, false));
    }

    @Test
    @DisplayName("thorns and explosions name the attacker as the direct entity and are still not swings")
    void directButNotAnAttackTypeIsAttributed() {
        // The case the direct-entity check alone would get wrong, and the reason the type is the
        // load-bearing test: damageSources().thorns(e) and explosion(e, e) both set entity == direct.
        assertEquals(HitClass.ATTRIBUTED, HitClass.of(true, true, false));
    }

    @Test
    @DisplayName("only a swing replaces the amount or carries a verb")
    void onlyMeleeOwnsTheNumberAndTheVerb() {
        assertTrue(HitClass.MELEE.replacesAmount());
        assertTrue(HitClass.MELEE.carriesVerb());

        // An authored number survives, and nothing spends the heavy the player wound up.
        assertFalse(HitClass.ATTRIBUTED.replacesAmount());
        assertFalse(HitClass.ATTRIBUTED.carriesVerb());

        assertFalse(HitClass.UNATTRIBUTED.replacesAmount());
        assertFalse(HitClass.UNATTRIBUTED.carriesVerb());
    }

    @Test
    @DisplayName("armour applies to anything an attacker caused, and to nothing else")
    void mitigationFollowsAttribution() {
        assertTrue(HitClass.MELEE.mitigated());
        assertTrue(HitClass.ATTRIBUTED.mitigated(), "a bolt still has to get through armour");
        assertFalse(HitClass.UNATTRIBUTED.mitigated(),
                "a well-equipped party must not pay less for the same trade");
    }

    @Test
    @DisplayName("every combination classifies, and only the two flags together reach MELEE")
    void tableIsTotal() {
        int melee = 0;
        for (int i = 0; i < 8; i++) {
            HitClass kind = HitClass.of((i & 1) != 0, (i & 2) != 0, (i & 4) != 0);
            if (kind == HitClass.MELEE) {
                melee++;
            }
        }
        assertEquals(1, melee, "exactly one of the eight combinations is a swing");
    }
}
