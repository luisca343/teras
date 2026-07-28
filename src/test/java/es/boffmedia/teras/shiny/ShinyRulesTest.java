package es.boffmedia.teras.shiny;

import es.boffmedia.teras.shiny.api.ShinyCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sparkle rule, with no Minecraft and neither Pokémon engine on the classpath. */
class ShinyRulesTest {

    private static ShinyCandidate wildShiny() {
        return new ShinyCandidate("shiny", true, false, true);
    }

    @Test
    @DisplayName("both Pixelmon shiny palettes count, whatever their case")
    void bothShinyPalettes() {
        assertTrue(ShinyRules.isShinyPalette("shiny"));
        assertTrue(ShinyRules.isShinyPalette("shiny2"));
        assertTrue(ShinyRules.isShinyPalette("Shiny2"));
    }

    @Test
    @DisplayName("an ordinary or event palette is not shiny")
    void otherPalettes() {
        assertFalse(ShinyRules.isShinyPalette("none"));
        assertFalse(ShinyRules.isShinyPalette("teras"));
        assertFalse(ShinyRules.isShinyPalette(null));
        assertFalse(ShinyRules.isShinyPalette(""));
    }

    @Test
    @DisplayName("a wild, catchable, non-boss shiny sparkles")
    void sparkles() {
        assertTrue(ShinyRules.sparkles(wildShiny()));
    }

    @Test
    @DisplayName("a Pokemon that belongs to someone does not sparkle")
    void ownedDoesNotSparkle() {
        assertFalse(ShinyRules.sparkles(new ShinyCandidate("shiny", false, false, true)));
    }

    @Test
    @DisplayName("a boss does not sparkle — it has its own cue")
    void bossDoesNotSparkle() {
        assertFalse(ShinyRules.sparkles(new ShinyCandidate("shiny", true, true, true)));
    }

    @Test
    @DisplayName("an uncatchable shiny does not sparkle — the cue would promise nothing")
    void uncatchableDoesNotSparkle() {
        assertFalse(ShinyRules.sparkles(new ShinyCandidate("shiny", true, false, false)));
    }

    @Test
    @DisplayName("a non-shiny that passes every other test still does not sparkle")
    void ordinaryDoesNotSparkle() {
        assertFalse(ShinyRules.sparkles(new ShinyCandidate("none", true, false, true)));
    }

    @Test
    @DisplayName("nothing at all does not sparkle")
    void nullDoesNotSparkle() {
        assertFalse(ShinyRules.sparkles(null));
    }

    @Test
    @DisplayName("an absent palette normalizes to none rather than null")
    void paletteNormalizes() {
        assertFalse(ShinyRules.isShinyPalette(new ShinyCandidate(null, true, false, true).palette()));
        assertFalse(ShinyRules.sparkles(new ShinyCandidate("", true, false, true)));
    }

    // Looking due north (-Z), the way a player faces at yaw 180.
    private static boolean inCone(double dx, double dy, double dz, double cone) {
        return ShinyRules.inViewCone(0, 0, -1, dx, dy, dz, cone);
    }

    @Test
    @DisplayName("straight ahead is in view, straight behind is not")
    void aheadAndBehind() {
        assertTrue(inCone(0, 0, -10, 120));
        assertFalse(inCone(0, 0, 10, 120));
    }

    @Test
    @DisplayName("the cone is measured full width, so 120 degrees reaches 60 to each side")
    void coneIsFullWidth() {
        assertTrue(inCone(-9, 0, -10, 120));  // ~42 degrees off centre
        assertFalse(inCone(-10, 0, -1, 120)); // ~84 degrees off centre
    }

    @Test
    @DisplayName("a shiny directly overhead is out of a 120 degree cone")
    void overhead() {
        assertFalse(inCone(0, 10, 0, 120));
    }

    @Test
    @DisplayName("a cone of zero disables the test — back to a sphere")
    void zeroConeDisables() {
        assertTrue(inCone(0, 0, 10, 0));
        assertTrue(inCone(0, 0, 10, -1));
    }

    @Test
    @DisplayName("a cone of 360 or more sees everything")
    void fullCircle() {
        assertTrue(inCone(0, 0, 10, 360));
        assertTrue(inCone(0, 0, 10, 400));
    }

    @Test
    @DisplayName("something at the player's own position is in view rather than a divide by zero")
    void zeroOffset() {
        assertTrue(inCone(0, 0, 0, 120));
        assertTrue(ShinyRules.inViewCone(0, 0, 0, 0, 0, -10, 120));
    }

    @Test
    @DisplayName("range is a sphere, and the boundary is inside it")
    void range() {
        assertTrue(ShinyRules.inRange(0, 20));
        assertTrue(ShinyRules.inRange(400, 20));   // exactly 20 blocks
        assertFalse(ShinyRules.inRange(401, 20));
        assertFalse(ShinyRules.inRange(0, 0));     // a range of zero notices nothing
    }
}
