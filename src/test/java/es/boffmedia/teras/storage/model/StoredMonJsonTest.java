package es.boffmedia.teras.storage.model;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the wire shape the SmartRotom PC reads. The web client binds these key names directly, and a
 * rename would surface only as blank cards on the board.
 */
class StoredMonJsonTest {

    private static final Gson GSON = new Gson();

    private static StoredMon togedemaru() {
        return new StoredMon(777, "Serious", "Togedemaru", "", "none", "Togedemaru", 100,
                "item.minecraft.air", "Lightning Rod",
                List.of("Fake Out", "Nuzzle", "Thunderbolt", "Spiky Shield"),
                List.of(17, 10, 19, 30, 9, 23), List.of(252, 0, 3, 252, 3, 0),
                List.of(320, 211, 150, 178, 160, 220), 320, "female", "none");
    }

    @Test
    void serialisesEveryFieldTheWebPcBinds() {
        JsonObject json = JsonParser.parseString(GSON.toJson(togedemaru())).getAsJsonObject();

        assertEquals(777, json.get("dex").getAsInt());
        assertEquals("Serious", json.get("nature").getAsString());
        assertEquals("Togedemaru", json.get("species").getAsString());
        assertEquals("Togedemaru", json.get("name").getAsString());
        assertEquals("none", json.get("palette").getAsString());
        assertEquals(100, json.get("level").getAsInt());
        assertEquals("Lightning Rod", json.get("ability").getAsString());
        assertEquals(4, json.getAsJsonArray("moves").size());
        assertEquals("female", json.get("gender").getAsString());
        assertEquals(320, json.get("hp").getAsInt());
        assertEquals("none", json.get("status").getAsString());

        for (String key : Arrays.asList("ivs", "evs", "stats")) {
            assertEquals(6, json.getAsJsonArray(key).size(), key + " is [HP, ATK, DEF, SPA, SPD, SPE]");
        }
    }

    /** The web PC tests emptiness against the literal {@code item.minecraft.air}. */
    @Test
    void sendsTheHeldItemAsADescriptionId() {
        JsonObject json = JsonParser.parseString(GSON.toJson(togedemaru())).getAsJsonObject();
        assertEquals("item.minecraft.air", json.get("item").getAsString());
    }

    @Test
    void wrapsAPcEntryInItsBoxAndIndex() {
        JsonObject json = JsonParser.parseString(
                GSON.toJson(new PcEntry(3, 17, togedemaru()))).getAsJsonObject();
        assertEquals(3, json.get("box").getAsInt());
        assertEquals(17, json.get("index").getAsInt());
        assertEquals("Togedemaru", json.getAsJsonObject("pokemon").get("species").getAsString());
    }

    /** Six entries always; an empty slot is a literal null, not a gap. */
    @Test
    void keepsEmptyPartySlotsAsNulls() {
        List<StoredMon> party = Arrays.asList(togedemaru(), null, null, null, null, null);
        JsonArray json = JsonParser.parseString(GSON.toJson(party)).getAsJsonArray();

        assertEquals(6, json.size());
        assertTrue(json.get(1).isJsonNull());
    }
}
