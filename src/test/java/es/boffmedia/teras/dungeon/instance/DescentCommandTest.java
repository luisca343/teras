package es.boffmedia.teras.dungeon.instance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reading the stage out of an authored dialogue option. Every case that cannot be read must come
 * back 0 — "not ours, leave it alone" — because the alternative is an operator's option silently
 * disappearing from their own dialogue.
 */
class DescentCommandTest {

    @Test
    void readsTheStageAfterThePlayerArgument() {
        assertEquals(3, DescentCommand.stageOf("/teras dungeon entrada iniciar @dp 3"));
    }

    @Test
    void ignoresTheCurseFlagsThatFollow() {
        assertEquals(5, DescentCommand.stageOf("/teras dungeon entrada iniciar @dp 5 true false"));
    }

    @Test
    void toleratesOddSpacingAndANamedPlayer() {
        assertEquals(3, DescentCommand.stageOf("  teras dungeon entrada  iniciar   Luisca  3 "));
    }

    /** The front door. Kept as a real case because it is the one every new player uses. */
    @Test
    void stageOneReadsAsOne() {
        assertEquals(1, DescentCommand.stageOf("/teras dungeon entrada iniciar @dp 1 true true"));
    }

    @Test
    void anythingThatIsNotADescentIsZero() {
        assertEquals(0, DescentCommand.stageOf("/teras trato @dp monedas"));
        assertEquals(0, DescentCommand.stageOf("/say hola"));
        assertEquals(0, DescentCommand.stageOf(""));
        assertEquals(0, DescentCommand.stageOf(null));
    }

    /**
     * A malformed or truncated command is kept, not dropped. Showing one option too many is
     * recoverable — the command itself refuses and names who is short; removing an authored option
     * on a typo is not.
     */
    @Test
    void anUnreadableStageIsZeroRatherThanAGuess() {
        assertEquals(0, DescentCommand.stageOf("/teras dungeon entrada iniciar @dp"));
        assertEquals(0, DescentCommand.stageOf("/teras dungeon entrada iniciar @dp tres"));
        assertEquals(0, DescentCommand.stageOf("/teras dungeon entrada iniciar"));
    }

    /** Negative depths are nonsense; they read as 0 and the option survives. */
    @Test
    void negativeStagesAreClampedToZero() {
        assertEquals(0, DescentCommand.stageOf("/teras dungeon entrada iniciar @dp -4"));
    }
}
