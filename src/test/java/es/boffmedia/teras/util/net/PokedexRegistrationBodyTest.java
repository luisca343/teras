package es.boffmedia.teras.util.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import es.boffmedia.teras.dex.DexStatus;
import es.boffmedia.teras.dex.api.DexScan;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the {@code /smartrotom/pokemon/register} wire contract. Pure object→JSON, so no Minecraft, no
 * config, no network.
 *
 * <p>Nothing else catches a break here: the fields are matched by name at the far end, so renaming one
 * — or dropping {@code server}, which {@code MinecraftMiddleware} 403s without — compiles and ships.</p>
 */
class PokedexRegistrationBodyTest {

    private static final UUID PLAYER = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");

    private static JsonObject body(int dex, DexStatus status, String form, String palette) {
        return JsonParser.parseString(SmartRotomService.registrationBody(
                        "teras-1", PLAYER, DexScan.of(dex, form, palette), status))
                .getAsJsonObject();
    }

    @Test
    void carriesEveryFieldTheBackendReads() {
        JsonObject json = body(479, DexStatus.SEEN, "frost", "shiny");
        assertEquals("teras-1", json.get("server").getAsString());
        assertEquals(PLAYER.toString(), json.get("uuid").getAsString());
        assertEquals(479, json.get("pokemonId").getAsInt());
        assertEquals("frost", json.get("form").getAsString());
        assertEquals("shiny", json.get("palette").getAsString());
        assertEquals(0, json.get("status").getAsInt());
        assertEquals(6, json.entrySet().size(), "unexpected extra field(s): " + json);
    }

    /** 0 = seen, 1 = caught. */
    @Test
    void statusUsesTheLegacyWireValues() {
        assertEquals(0, body(1, DexStatus.SEEN, "none", "none").get("status").getAsInt());
        assertEquals(1, body(1, DexStatus.CAUGHT, "none", "none").get("status").getAsInt());
    }

    /**
     * Pixelmon reports a missing form/palette as empty, Cobblemon as null; the contract wants "none".
     * A null would be omitted from the body by Gson, leaving the field absent rather than "none".
     */
    @Test
    void normalisesAbsentFormAndPaletteToNone() {
        JsonObject fromNull = body(25, DexStatus.CAUGHT, null, null);
        assertEquals("none", fromNull.get("form").getAsString());
        assertEquals("none", fromNull.get("palette").getAsString());

        JsonObject fromEmpty = body(25, DexStatus.CAUGHT, "", "");
        assertEquals("none", fromEmpty.get("form").getAsString());
        assertEquals("none", fromEmpty.get("palette").getAsString());
    }
}
