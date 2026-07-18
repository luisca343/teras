package es.boffmedia.teras.dex.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The backend reads {@code wingullDex.SEEN} / {@code wingullDex.CAUGHT} straight off this response to
 * bulk-write its own table, so the upper-case field names are the wire contract, not a style choice.
 */
class DexSnapshotJsonTest {

    @Test
    void serialisesTheKeysTheBackendReads() {
        String json = new Gson().toJson(new DexSnapshot(List.of(1, 4), List.of(25, 133)));
        JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

        assertEquals(2, parsed.getAsJsonArray("SEEN").size());
        assertEquals(25, parsed.getAsJsonArray("CAUGHT").get(0).getAsInt());
    }
}
