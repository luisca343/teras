package es.boffmedia.teras.dungeon.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pipeline, and the property it exists for: an armour <i>budget</i> has to feel the same at
 * floor 1 and floor 12 while both sides' raw numbers grow.
 */
class DamageMathTest {

    private static StatBlock attacker(double damage) {
        return new StatBlock().setBase(Stat.DANO, damage);
    }

    private static StatBlock defender(double armour) {
        return new StatBlock().setBase(Stat.ARMADURA, armour);
    }

    @Test
    void noArmourMeansTheWholeHitLands() {
        DamageMath.Hit hit = DamageMath.resolve(attacker(50), defender(0), 1, 1.0);
        assertEquals(50, hit.damage(), 1e-9);
        assertFalse(hit.crit());
    }

    /**
     * The reason K scales. An enemy authored to feel "40% armoured" has to keep feeling that way at
     * depth — if this drifts, floor 12 is either a wall or a formality.
     */
    @Test
    void anArmourBudgetFeelsTheSameAtEveryDepth() {
        for (int depth : new int[] {1, 4, 8, 12}) {
            double armour = DamageMath.armourFor(0.40, depth);
            assertEquals(0.40, DamageMath.mitigation(armour, 0, depth), 1e-9,
                    "40% at floor " + depth + " stopped being 40%");
        }
    }

    @Test
    void theSameArmourIsWorthLessTheDeeperYouGo() {
        double armour = 60;
        assertTrue(DamageMath.mitigation(armour, 0, 1) > DamageMath.mitigation(armour, 0, 12),
                "a fixed number must decay against a growing constant, or numbers stop inflating");
    }

    @Test
    void nothingIsEverImmune() {
        double mitigation = DamageMath.mitigation(Double.MAX_VALUE, 0, 1);
        assertEquals(DamageMath.MAX_MITIGATION, mitigation, 1e-9);
        assertTrue(DamageMath.resolve(attacker(10), defender(Double.MAX_VALUE), 1, 1.0).damage() > 0,
                "an unkillable enemy on a sealed floor is a room with no exit");
    }

    @Test
    void penetrationEatsArmourBeforeTheCurveSeesIt() {
        double full = DamageMath.mitigation(100, 0, 1);
        double halved = DamageMath.mitigation(100, 0.5, 1);
        assertEquals(DamageMath.mitigation(50, 0, 1), halved, 1e-9,
                "half penetration against 100 armour must equal no penetration against 50");
        assertTrue(halved < full);
        assertEquals(0, DamageMath.mitigation(100, 1.0, 1), 1e-9, "total penetration leaves nothing");
    }

    @Test
    void aCritMultipliesByContundenciaAndSaysSo() {
        StatBlock crits = attacker(20).setBase(Stat.CRITICO, 1.0).setBase(Stat.CONTUNDENCIA, 2.0);
        DamageMath.Hit hit = DamageMath.resolve(crits, defender(0), 1, 0.0);
        assertEquals(40, hit.damage(), 1e-9);
        assertTrue(hit.crit(), "the caller cannot play a tell for a crit it was not told about");
    }

    @Test
    void theCritRollIsExclusiveAtTheTop() {
        StatBlock never = attacker(20).setBase(Stat.CRITICO, 0.0);
        assertFalse(DamageMath.resolve(never, defender(0), 1, 0.0).crit(),
                "a zero chance must not crit on a roll of exactly zero");
        StatBlock always = attacker(20).setBase(Stat.CRITICO, 1.0);
        assertTrue(DamageMath.resolve(always, defender(0), 1, 0.999).crit());
    }

    @Test
    void depthBelowOneIsTreatedAsTheFirstFloor() {
        assertEquals(DamageMath.armourConstant(1), DamageMath.armourConstant(0), 1e-9);
        assertEquals(DamageMath.armourConstant(1), DamageMath.armourConstant(-5), 1e-9);
    }

    @Test
    void armourForIsTheInverseOfMitigation() {
        for (double fraction : new double[] {0.1, 0.25, 0.5, 0.7, 0.85}) {
            double armour = DamageMath.armourFor(fraction, 7);
            assertEquals(fraction, DamageMath.mitigation(armour, 0, 7), 1e-9);
        }
        assertEquals(0, DamageMath.armourFor(0, 7), 1e-9);
    }
}
