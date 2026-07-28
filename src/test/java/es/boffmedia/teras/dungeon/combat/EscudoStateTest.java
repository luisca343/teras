package es.boffmedia.teras.dungeon.combat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The grant rule for shield hearts. The arithmetic is tiny and the failure it prevents is not: without
 * the high-water mark, any escudo item becomes an infinite one by unequipping and re-equipping it.
 */
class EscudoStateTest {

    @Test
    @DisplayName("a fresh sheet pays out in full")
    void firstGrantPaysEverything() {
        assertEquals(3, EscudoState.owed(3, 0), 1e-9);
    }

    @Test
    @DisplayName("what has already been paid is never paid twice")
    void alreadyGrantedIsNotRepeated() {
        assertEquals(0, EscudoState.owed(3, 3), 1e-9);
        // Taking the piece off and putting it back is the case that matters: the sheet reads 3 again,
        // and it must not hand out three more hearts.
        assertEquals(0, EscudoState.owed(3, 3), 1e-9);
    }

    @Test
    @DisplayName("only the increase is paid when a second piece is added")
    void onlyTheDifferenceIsPaid() {
        assertEquals(2, EscudoState.owed(5, 3), 1e-9);
    }

    @Test
    @DisplayName("losing the source never claws back hearts already spent or held")
    void losingTheSourceOwesNothingRatherThanNegative() {
        assertEquals(0, EscudoState.owed(0, 4), 1e-9,
                "a negative owed would subtract absorption the player had already earned");
    }

    @Test
    @DisplayName("a nonsense already-granted is treated as nothing granted")
    void negativeMarkIsFloored() {
        assertEquals(2, EscudoState.owed(2, -5), 1e-9);
    }

    @Test
    @DisplayName("escudo counts in the same half-hearts per point as contenedores")
    void pointsAreTwoHalfHeartsEach() {
        // contenedores is maxHealth / 2, so a shield point has to be worth the same as a heart
        // container or the two numbers on the panel mean different things.
        assertEquals(2f, EscudoState.HEALTH_PER_POINT, 1e-9);
    }
}
