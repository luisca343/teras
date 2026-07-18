package es.boffmedia.teras.client.region.journeymap;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for parsing the {@code addWaypoint} request. Pure JSON→record, so no Minecraft, no CEF and —
 * importantly — no JourneyMap, which is {@code compileOnly} and therefore absent at test runtime.
 *
 * <p>The page is the caller and may send a partial or malformed object, so every field has to hold to
 * something that still places a waypoint: a bad colour must not throw on the CEF callback.</p>
 */
class AddWaypointQueryTest {

    private static final String OVERWORLD = "minecraft:overworld";

    private static AddWaypointQuery parse(String json) {
        return AddWaypointQuery.from(JsonParser.parseString(json).getAsJsonObject(), OVERWORLD);
    }

    /** 1.16.5's defaults, which the page relies on when it omits fields. */
    @Test
    void appliesLegacyDefaultsWhenFieldsAreAbsent() {
        AddWaypointQuery query = parse("{\"query\":\"addWaypoint\"}");
        assertEquals("waypoint", query.name());
        assertEquals(0, query.x());
        assertEquals(64, query.y());
        assertEquals(0, query.z());
        assertEquals(AddWaypointQuery.WHITE, query.color());
    }

    @Test
    void readsExplicitFields() {
        AddWaypointQuery query = parse(
                "{\"name\":\"Pueblo Lavanda\",\"x\":10,\"y\":70,\"z\":-30,\"color\":\"#FF8800\"}");
        assertEquals("Pueblo Lavanda", query.name());
        assertEquals(10, query.x());
        assertEquals(70, query.y());
        assertEquals(-30, query.z());
        assertEquals(0xFF8800, query.color());
    }

    /** The dimension is the player's unless the page names one — it has no way to know it otherwise. */
    @Test
    void defaultsDimensionToTheFallbackButPrefersAnExplicitOne() {
        assertEquals(OVERWORLD, parse("{\"name\":\"a\"}").dimension());
        assertEquals("minecraft:the_nether",
                parse("{\"dimension\":\"minecraft:the_nether\"}").dimension());
    }

    /** Colours are accepted with or without the leading '#', as Color.decode did in 1.16.5. */
    @Test
    void acceptsColourWithAndWithoutHash() {
        assertEquals(0x00FF00, parse("{\"color\":\"#00FF00\"}").color());
        assertEquals(0x00FF00, parse("{\"color\":\"00FF00\"}").color());
    }

    /** A malformed colour must fall back, not throw: this runs on a CEF callback thread. */
    @Test
    void fallsBackToWhiteOnUnparseableColour() {
        assertEquals(AddWaypointQuery.WHITE, parse("{\"color\":\"nope\"}").color());
        assertEquals(AddWaypointQuery.WHITE, parse("{\"color\":\"\"}").color());
        assertEquals(AddWaypointQuery.WHITE, parse("{\"color\":123}").color());
    }

    /** An alpha-carrying colour is masked to RGB rather than becoming a negative int. */
    @Test
    void masksAlphaOutOfAnEightDigitColour() {
        assertEquals(0x336699, parse("{\"color\":\"FF336699\"}").color());
    }

    /** Wrong-typed fields fall back instead of throwing. */
    @Test
    void ignoresWrongTypedFields() {
        AddWaypointQuery query = parse("{\"name\":42,\"x\":\"east\"}");
        assertEquals("waypoint", query.name());
        assertEquals(0, query.x());
    }
}
