package es.boffmedia.teras.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SmartRotom origin check. This is the trust boundary for the MCEF JS bridge: a URL that passes
 * here can call {@code window.mcefQuery} and, through it, capture the player's screen, spend backend
 * rewards, move them between voice calls, and (as an OP) broadcast server-wide messages.
 */
class UrlOriginTest {

    private static final String HOME = "http://teras.es/smartrotom";

    @Test
    void theConfiguredHomeMatchesItself() {
        assertTrue(UrlOrigin.sameSite(HOME, HOME));
    }

    @Test
    void anyPathOnTheHomeHostMatches() {
        assertTrue(UrlOrigin.sameSite(HOME, "http://teras.es/smartrotom/chatapp"));
        assertTrue(UrlOrigin.sameSite(HOME, "http://teras.es/"));
        assertTrue(UrlOrigin.sameSite(HOME, "https://teras.es/smartrotom?a=1#b"));
    }

    @Test
    void subdomainsOfTheHomeHostMatch() {
        assertTrue(UrlOrigin.sameSite(HOME, "http://cdn.teras.es/app"));
        assertTrue(UrlOrigin.sameSite(HOME, "http://a.b.teras.es/app"));
    }

    @Test
    void aForeignHostWithTheHomePathInItDoesNotMatch() {
        // The bug this class exists to close: 1.16.5 tested url.contains("smartrotom"), so every one
        // of these passed and inherited the bridge.
        assertFalse(UrlOrigin.sameSite(HOME, "https://evil.example/smartrotom"));
        assertFalse(UrlOrigin.sameSite(HOME, "https://evil.example/smartrotom/teras.es"));
        assertFalse(UrlOrigin.sameSite(HOME, "https://smartrotom.evil.example/"));
    }

    @Test
    void aHostMerelyEndingInTheHomeHostDoesNotMatch() {
        // The subdomain rule must anchor on a dot, or notteras.es passes as a subdomain of teras.es.
        assertFalse(UrlOrigin.sameSite(HOME, "http://notteras.es/smartrotom"));
        assertFalse(UrlOrigin.sameSite(HOME, "http://evilteras.es/"));
    }

    @Test
    void theHomeIsNotASubdomainOfItsOwnSubdomain() {
        assertFalse(UrlOrigin.sameSite("http://cdn.teras.es", "http://teras.es/"));
    }

    @Test
    void hostComparisonIgnoresCase() {
        assertTrue(UrlOrigin.sameSite("http://TERAS.es/x", "http://teras.ES/y"));
    }

    @Test
    void unparseableOrRelativeUrlsNeverMatch() {
        // "Both sides are broken" is not a match: an unknown origin must fail closed.
        assertFalse(UrlOrigin.sameSite(HOME, null));
        assertFalse(UrlOrigin.sameSite(HOME, ""));
        assertFalse(UrlOrigin.sameSite(HOME, "/smartrotom"));
        assertFalse(UrlOrigin.sameSite(HOME, "about:blank"));
        assertFalse(UrlOrigin.sameSite(HOME, "not a url"));
        assertFalse(UrlOrigin.sameSite(null, HOME));
        assertFalse(UrlOrigin.sameSite("", ""));
        assertFalse(UrlOrigin.sameSite(null, null));
    }

    @Test
    void aBlankOrHostlessHomeTrustsNothing() {
        // What an unconfigured server gets: no home host means no page is ever trusted.
        assertFalse(UrlOrigin.sameSite("not-a-url", "http://teras.es/"));
        assertFalse(UrlOrigin.sameSite("   ", "http://teras.es/"));
    }

    @Test
    void hostOfExtractsTheHostOrNull() {
        assertEquals("teras.es", UrlOrigin.hostOf("http://teras.es/smartrotom"));
        assertEquals("teras.es", UrlOrigin.hostOf("  https://TERAS.ES  "));
        assertNull(UrlOrigin.hostOf("/relative"));
        assertNull(UrlOrigin.hostOf(""));
        assertNull(UrlOrigin.hostOf(null));
    }
}
