package es.boffmedia.teras.http;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the HTTP API's routing and auth primitives. These are the security-critical parts — an
 * over-permissive path parse or a sloppy bearer check would expose player data — and they need no
 * Minecraft runtime, so they run in plain JUnit.
 */
class TerasHttpServerTest {

    private static final String UUID_STR = "069a79f4-44e9-4726-a5be-fca90e38aaf5";

    // ---- parseUuidFromPath ----

    @Test
    void parsesAValidQuestsUserPath() {
        assertEquals(UUID.fromString(UUID_STR),
                TerasHttpServer.parseUuidFromPath("/quests/user/" + UUID_STR));
    }

    @Test
    void rejectsMalformedUuid() {
        assertNull(TerasHttpServer.parseUuidFromPath("/quests/user/not-a-uuid"));
        assertNull(TerasHttpServer.parseUuidFromPath("/quests/user/123"));
    }

    @Test
    void rejectsMissingUuid() {
        assertNull(TerasHttpServer.parseUuidFromPath("/quests/user/"));
        assertNull(TerasHttpServer.parseUuidFromPath("/quests/user"));
    }

    @Test
    void rejectsExtraPathSegments() {
        // /quests/user/{uuid}/admin must not be read as a bare uuid.
        assertNull(TerasHttpServer.parseUuidFromPath("/quests/user/" + UUID_STR + "/extra"));
        assertNull(TerasHttpServer.parseUuidFromPath("/quests/user/" + UUID_STR + "/"));
    }

    @Test
    void rejectsForeignPaths() {
        assertNull(TerasHttpServer.parseUuidFromPath("/other/" + UUID_STR));
        assertNull(TerasHttpServer.parseUuidFromPath("/"));
        assertNull(TerasHttpServer.parseUuidFromPath(null));
    }

    @Test
    void doesNotTraverseOutOfItsPrefix() {
        // A traversal attempt is not a uuid, so it can never reach the handler body.
        assertNull(TerasHttpServer.parseUuidFromPath("/quests/user/../../etc/passwd"));
    }

    // ---- isTokenValid ----

    @Test
    void acceptsTheExactBearerToken() {
        assertTrue(TerasHttpServer.isTokenValid("Bearer s3cret", "s3cret"));
    }

    @Test
    void rejectsWrongOrTruncatedTokens() {
        assertFalse(TerasHttpServer.isTokenValid("Bearer wrong", "s3cret"));
        assertFalse(TerasHttpServer.isTokenValid("Bearer s3cre", "s3cret"));
        assertFalse(TerasHttpServer.isTokenValid("Bearer s3crets", "s3cret"));
    }

    @Test
    void rejectsMissingOrMalformedHeader() {
        assertFalse(TerasHttpServer.isTokenValid(null, "s3cret"));
        assertFalse(TerasHttpServer.isTokenValid("", "s3cret"));
        assertFalse(TerasHttpServer.isTokenValid("s3cret", "s3cret"), "must require the Bearer scheme");
        assertFalse(TerasHttpServer.isTokenValid("Basic s3cret", "s3cret"));
    }

    @Test
    void isCaseSensitiveOnTheToken() {
        assertFalse(TerasHttpServer.isTokenValid("Bearer S3CRET", "s3cret"));
    }

    @Test
    void blankConfiguredTokenMeansNoAuth() {
        // Deliberate, and the one place this API is fail-OPEN. Wungill's WINGULL_API was
        // unauthenticated and the SmartRotom backend still sends no Authorization header, so requiring
        // a token by default would 401 the entire live pipeline. Blank token => everything passes;
        // TerasHttpServer logs a warning at startup, louder if the bind is public.
        assertTrue(TerasHttpServer.isTokenValid(null, ""));
        assertTrue(TerasHttpServer.isTokenValid("Bearer anything", ""));
        assertTrue(TerasHttpServer.isTokenValid(null, "   "));
        assertTrue(TerasHttpServer.isTokenValid(null, null));
    }

    @Test
    void aConfiguredTokenIsEnforced() {
        // ...and the moment a token IS set, the door shuts — including for callers sending nothing.
        assertFalse(TerasHttpServer.isTokenValid(null, "s3cret"));
        assertTrue(TerasHttpServer.isTokenValid("Bearer s3cret", "s3cret"));
    }
}
