package es.boffmedia.teras.client.camera;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for parsing the {@code takeScreenshot} options. Pure JSON→record, so no Minecraft and no CEF.
 *
 * <p>The page is the caller and is free to send a partial or malformed object, so the parse has to hold
 * every field to something {@code ImageIO} will accept — an unknown format or an out-of-range quality
 * reaching the encoder is an exception on a CEF callback rather than a photo.</p>
 */
class ScreenshotQueryTest {

    private static ScreenshotQuery parse(String json) {
        return ScreenshotQuery.from(JsonParser.parseString(json).getAsJsonObject());
    }

    /** 1.16.5's defaults, which the page relies on when it sends a bare {@code {query:...}}. */
    @Test
    void appliesLegacyDefaultsWhenFieldsAreAbsent() {
        ScreenshotQuery query = parse("{\"query\":\"takeScreenshot\"}");
        assertTrue(query.includeUI());
        assertEquals(ScreenshotQuery.PNG, query.format());
        assertEquals(90, query.quality());
        assertFalse(query.isJpeg());
    }

    @Test
    void readsExplicitOptions() {
        ScreenshotQuery query = parse("{\"includeUI\":false,\"format\":\"jpeg\",\"quality\":60}");
        assertFalse(query.includeUI());
        assertTrue(query.isJpeg());
        assertEquals(60, query.quality());
    }

    /** ImageIO writes "jpg"; the page and the data-URL MIME type both say "jpeg". */
    @Test
    void treatsJpgAsJpegAndIsCaseInsensitive() {
        assertTrue(parse("{\"format\":\"jpg\"}").isJpeg());
        assertTrue(parse("{\"format\":\"JPEG\"}").isJpeg());
        assertEquals(ScreenshotQuery.JPEG, parse("{\"format\":\"JPG\"}").mimeSubtype());
    }

    /** An unknown format must not reach ImageIO.write, which returns false and writes nothing. */
    @Test
    void fallsBackToPngForUnknownFormats() {
        assertEquals(ScreenshotQuery.PNG, parse("{\"format\":\"webp\"}").format());
        assertEquals(ScreenshotQuery.PNG, parse("{\"format\":\"\"}").format());
    }

    /** setCompressionQuality throws outside 0..1, so the clamp is what keeps it in range. */
    @Test
    void clampsQualityToWhatTheJpegWriterAccepts() {
        assertEquals(100, parse("{\"quality\":5000}").quality());
        assertEquals(1, parse("{\"quality\":0}").quality());
        assertEquals(1, parse("{\"quality\":-20}").quality());
    }

    @Test
    void toleratesWrongTypesRatherThanFailingTheCapture() {
        ScreenshotQuery query = parse("{\"includeUI\":\"nope\",\"format\":7,\"quality\":\"high\"}");
        assertTrue(query.includeUI());
        assertEquals(ScreenshotQuery.PNG, query.format());
        assertEquals(90, query.quality());
    }

    @Test
    void toleratesNestedObjectsInPlaceOfScalars() {
        JsonObject json = JsonParser.parseString("{\"quality\":{\"a\":1},\"includeUI\":[1,2]}").getAsJsonObject();
        ScreenshotQuery query = ScreenshotQuery.from(json);
        assertEquals(90, query.quality());
        assertTrue(query.includeUI());
    }
}
