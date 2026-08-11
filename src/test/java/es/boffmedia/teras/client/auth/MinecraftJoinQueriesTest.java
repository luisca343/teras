package es.boffmedia.teras.client.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The {@code serverId} guard on the Mojang handshake query. Pure JSON parsing, so no Minecraft and no
 * CEF — the check deliberately runs before anything touches the client.
 *
 * <p>Why it is guarded at all: the query hands its argument straight to authlib's
 * {@code joinServer}. Refusing anything that is not the API's own 16-random-bytes-in-hex shape keeps
 * a page that somehow passed the origin check from using it as a "join any server" primitive.</p>
 */
class MinecraftJoinQueriesTest {

    private static String read(String json) {
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        return MinecraftJoinQueries.readServerId(object);
    }

    @Test
    void acceptsTheServerIdTheApiMints() {
        // 16 random bytes, hex — what MinecraftHandshakeService.createChallenge returns.
        String serverId = "9f2c4a1b8e7d6c5b4a39281706f5e4d3";
        assertEquals(serverId, read("{\"query\":\"mcJoinServer\",\"serverId\":\"" + serverId + "\"}"));
    }

    @Test
    void rejectsAbsentBlankAndNonStringValues() {
        assertNull(read("{\"query\":\"mcJoinServer\"}"));
        assertNull(read("{\"serverId\":\"\"}"));
        assertNull(read("{\"serverId\":12345678}"));
        assertNull(read("{\"serverId\":null}"));
        assertNull(read("{\"serverId\":[\"abcdefgh\"]}"));
    }

    @Test
    void rejectsShapesThatAreNotAChallenge() {
        assertNull(read("{\"serverId\":\"short\"}"));                    // under 8 chars
        assertNull(read("{\"serverId\":\"mc.hypixel.net\"}"));           // dots — a real host name
        assertNull(read("{\"serverId\":\"abcdefgh; rm -rf\"}"));         // spaces/punctuation
        assertNull(read("{\"serverId\":\"" + "a".repeat(65) + "\"}"));   // over 64 chars
    }
}
