package es.boffmedia.teras.audio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Track names reach the filesystem, so this is the traversal boundary for the whole audio system.
 */
class TrackNameTest {

    @Test
    @DisplayName("names are stored trimmed, lowercased, with spaces as underscores")
    void normalizes() {
        assertEquals("cancion_del_bar", TrackName.normalize("  Cancion Del Bar  "));
        assertEquals("tema-01", TrackName.normalize("TEMA-01"));
    }

    @Test
    @DisplayName("a name that is already canonical survives normalisation unchanged")
    void idempotent() {
        assertEquals("cancion_del_bar", TrackName.normalize(TrackName.normalize("Cancion Del Bar")));
    }

    @Test
    @DisplayName("path traversal is refused, not sanitised")
    void refusesTraversal() {
        // Sanitising would silently turn ../../server into something that still resolves somewhere;
        // refusing means the admin is told their name is wrong.
        assertNull(TrackName.normalize("../secreto"));
        assertNull(TrackName.normalize("..\\secreto"));
        assertNull(TrackName.normalize("/etc/passwd"));
        assertNull(TrackName.normalize("carpeta/tema"));
        assertNull(TrackName.normalize("C:tema"));
    }

    @Test
    @DisplayName("a NUL or control character inside the name cannot get through")
    void refusesControlCharacters() {
        assertNull(TrackName.normalize("te\nma"));
        assertNull(TrackName.normalize("te\u0000ma"));
        assertNull(TrackName.normalize("te\tma"));
        // An extension is not part of a name - the library owns that.
        assertNull(TrackName.normalize("tema.mp3"));
    }

    @Test
    @DisplayName("surrounding whitespace is trimmed rather than refused")
    void trimsRatherThanRefuses() {
        // Deliberate: a trailing newline is a paste artefact, not an attack, and refusing it would
        // reject names an admin typed correctly.
        assertEquals("tema", TrackName.normalize("tema\n"));
        assertEquals("tema", TrackName.normalize("\t tema  "));
    }

    @Test
    @DisplayName("accents and other letters outside the safe alphabet are refused")
    void refusesAccents() {
        assertNull(TrackName.normalize("cancion\u00f3n"));
        assertNull(TrackName.normalize("tema\u266a"));
    }

    @Test
    @DisplayName("empty, blank and punctuation-only names are refused")
    void refusesEmpty() {
        assertNull(TrackName.normalize(null));
        assertNull(TrackName.normalize(""));
        assertNull(TrackName.normalize("   "));
        assertNull(TrackName.normalize("___"));
        assertNull(TrackName.normalize("--"));
    }

    @Test
    @DisplayName("a leading dash is refused so a name can never read as a flag")
    void refusesLeadingDash() {
        assertNull(TrackName.normalize("-rf"));
        assertTrue(TrackName.isValid("tema-rf"));
    }

    @Test
    @DisplayName("names longer than the limit are refused")
    void refusesOverlongNames() {
        assertTrue(TrackName.isValid("a".repeat(TrackName.MAX_LENGTH)));
        assertFalse(TrackName.isValid("a".repeat(TrackName.MAX_LENGTH + 1)));
        assertNull(TrackName.normalize("a".repeat(TrackName.MAX_LENGTH + 1)));
    }

    @Test
    @DisplayName("isValid demands the canonical form, not merely a safe one")
    void isValidIsCanonical() {
        // normalize() lowercases; isValid() is the post-condition, so uppercase must fail it.
        assertFalse(TrackName.isValid("Tema"));
        assertFalse(TrackName.isValid("tema del bar"));
        assertTrue(TrackName.isValid("tema_del_bar"));
    }
}
