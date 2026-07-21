package es.boffmedia.teras.dungeon.piso;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The compounding trap, held as a test. §11's caution is that a flat multiply lands at ~1.8&#179;
 * &asymp; 5.8&times; threat at dificultad 1.8, and the whole point of splitting the curves is that
 * it must not. These assertions are what would fail if anyone rewired the curves back into one
 * multiply.
 */
class DificultadTest {

    @Test
    void identityAtOne() {
        assertEquals(1.0, Dificultad.count(1.0));
        assertEquals(1.0, Dificultad.health(1.0));
        assertEquals(1.0, Dificultad.damage(1.0));
        assertEquals(1.0, Dificultad.eliteWeight(1.0));
        assertEquals(1.0, Dificultad.combined(1.0));
    }

    @Test
    void combinedStaysFarBelowTheFlatCube() {
        // The number the caution names: a flat multiply would be 1.8^3 = 5.832.
        double flatCube = Math.pow(1.8, 3);
        double combined = Dificultad.combined(1.8);
        assertTrue(combined < flatCube * 0.5,
                "combined " + combined + " should be well under half the flat cube " + flatCube);
        // And still meaningfully harder than the previous floor.
        assertTrue(combined > 1.3, "combined " + combined + " should still bite");
    }

    @Test
    void healthCarriesMoreThanDamage() {
        // The design statement: a deeper floor lengthens fights before it makes mistakes fatal.
        assertTrue(Dificultad.health(2.0) > Dificultad.damage(2.0));
        assertTrue(Dificultad.health(2.0) > Dificultad.count(2.0));
        // Damage grows slowest of the three stat axes.
        assertTrue(Dificultad.damage(2.0) <= Dificultad.count(2.0));
    }

    @Test
    void everyAxisRisesWithDepth() {
        assertTrue(Dificultad.count(2.0) > Dificultad.count(1.5));
        assertTrue(Dificultad.health(2.0) > Dificultad.health(1.5));
        assertTrue(Dificultad.damage(2.0) > Dificultad.damage(1.5));
        assertTrue(Dificultad.eliteWeight(2.0) > Dificultad.eliteWeight(1.5));
    }

    @Test
    void clampedAtTheDesignCeiling() {
        assertEquals(Dificultad.combined(Dificultad.MAX), Dificultad.combined(18.0),
                "a typo'd dificultad of 18 must clamp, not build an unclearable floor");
    }

    @Test
    void nonsenseFallsBackToBaseline() {
        assertEquals(1.0, Dificultad.count(0.0));
        assertEquals(1.0, Dificultad.count(-3.0));
        assertEquals(1.0, Dificultad.health(Double.NaN));
    }
}
