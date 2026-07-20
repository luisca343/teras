package es.boffmedia.teras.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link TerasConfig#normalizeApiUrl} — the migration that keeps already-deployed configs working.
 *
 * <p>Builds up to and including the 1.21.1 port shipped a default {@code apiURL} ending in
 * {@code /smartrotom} while every route in {@code SmartRotomService} appends that prefix itself, so
 * those servers POSTed to {@code /smartrotom/smartrotom/...} and got a 404 on every outbound call.
 * Config files are never rewritten in place, so correcting the default alone would have left them
 * broken; these cases pin the strip that actually fixes them.</p>
 */
class TerasConfigApiUrlTest {

    @Test
    void stripsTheDoubledSmartrotomPrefix() {
        assertEquals("http://api.boffmedia.es",
                TerasConfig.normalizeApiUrl("http://api.boffmedia.es/smartrotom"));
        assertEquals("https://api.ficuslab.es",
                TerasConfig.normalizeApiUrl("https://api.ficuslab.es/smartrotom"));
    }

    /** The suffix and the trailing slash have to be handled together, in that order. */
    @Test
    void stripsTrailingSlashesBeforeTheSuffix() {
        assertEquals("https://api.ficuslab.es",
                TerasConfig.normalizeApiUrl("https://api.ficuslab.es/smartrotom/"));
        assertEquals("https://api.ficuslab.es",
                TerasConfig.normalizeApiUrl("https://api.ficuslab.es///"));
    }

    @Test
    void leavesACorrectBaseAlone() {
        assertEquals("https://api.ficuslab.es",
                TerasConfig.normalizeApiUrl("https://api.ficuslab.es"));
        assertEquals("http://127.0.0.1:3000",
                TerasConfig.normalizeApiUrl("http://127.0.0.1:3000"));
    }

    /**
     * Only a whole trailing segment counts. A host that merely ends in those letters, or a base with
     * the segment somewhere in the middle, is a deliberate value and must survive untouched.
     */
    @Test
    void doesNotStripASegmentThatOnlyLooksLikeTheSuffix() {
        assertEquals("https://smartrotom.ficuslab.es",
                TerasConfig.normalizeApiUrl("https://smartrotom.ficuslab.es"));
        assertEquals("https://api.ficuslab.es/smartrotom/v2",
                TerasConfig.normalizeApiUrl("https://api.ficuslab.es/smartrotom/v2"));
    }

    @Test
    void fallsBackToTheDefaultWhenUnset() {
        assertEquals("https://api.ficuslab.es", TerasConfig.normalizeApiUrl(null));
        assertEquals("https://api.ficuslab.es", TerasConfig.normalizeApiUrl("   "));
    }
}
