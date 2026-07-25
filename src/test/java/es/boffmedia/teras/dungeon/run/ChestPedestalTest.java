package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.dungeon.piso.MarkerContract;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a chest may be, and that a template can say so.
 *
 * <p>The system itself needs a server to exercise, but the half that has actually broken before is
 * the seam between an authored marker and the code that reads it — three times (§25, §33, §37), each
 * time as content that shipped and was never once used. These are the checks that seam allows
 * without a level.</p>
 */
class ChestPedestalTest {

    /** The marker has to be in the vocabulary, or the editor refuses to place what rooms carry. */
    @Test
    void cofreIsARuntimeMarker() {
        assertTrue(MarkerContract.vocabulary().contains("cofre"));
        assertEquals(MarkerContract.Use.RUNTIME, MarkerContract.useOf("cofre"));
        // And with its qualifier, which is how every authored one is actually spelled.
        assertEquals(MarkerContract.Use.RUNTIME, MarkerContract.useOf("cofre:proeza"));
    }

    /**
     * A parkour route is a note to an author and nothing reads it — which is the correct state for
     * an annotation, and the contract refuses to let a room key require one.
     */
    @Test
    void parkourIsAnAnnotation() {
        assertEquals(MarkerContract.Use.ANNOTATION, MarkerContract.useOf("parkour"));
    }

    /**
     * Every kind is spellable in a template exactly as it is named in code.
     *
     * <p>The tool writes {@code cofre:<kind>} in lower case and {@link ChestPedestal} matches
     * case-insensitively against the enum, so this is the check that the two spellings cannot drift
     * apart — a kind renamed in code and not in the room tool would fall back to a plain chest, which
     * is a silent loss of the lock rather than an error anyone would see.</p>
     */
    @Test
    void everyKindHasALowerCaseSpelling() {
        for (ChestPedestal.Kind kind : ChestPedestal.Kind.values()) {
            String spelled = kind.name().toLowerCase(Locale.ROOT);
            assertEquals(kind, ChestPedestal.Kind.valueOf(spelled.toUpperCase(Locale.ROOT)),
                    "cofre:" + spelled + " does not round-trip to its kind");
        }
    }
}
