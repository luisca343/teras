package es.boffmedia.teras.http;

import es.boffmedia.teras.http.GiveRequests.BadRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parsing of the two grant bodies. These shapes are the backend's, so the tests pin the exact fields
 * its 23 raw axios sites send — and that a malformed body is a clean {@link BadRequest} (a 400),
 * never a half-built grant.
 */
class GiveRequestsTest {

    private static final String UUID_STR = "67d9b543-5ac9-41e1-a8a5-20d7689e24a4";

    // ---- /givepokemon ----

    @Test
    void parsesAPokemonGive() {
        GiveRequests.PokemonGive req = GiveRequests.parsePokemon(
                "{\"uuid\":\"" + UUID_STR + "\",\"pokespec\":\"Incineroar lvl:50\",\"sendMessage\":true}");
        assertEquals(UUID.fromString(UUID_STR), req.uuid());
        assertEquals("Incineroar lvl:50", req.pokespec());
        assertTrue(req.sendMessage());
    }

    /** sendMessage defaults to true when omitted, matching 1.16.5. */
    @Test
    void defaultsSendMessageToTrue() {
        GiveRequests.PokemonGive req = GiveRequests.parsePokemon(
                "{\"uuid\":\"" + UUID_STR + "\",\"pokespec\":\"Pikachu\"}");
        assertTrue(req.sendMessage());
    }

    @Test
    void honoursSendMessageFalse() {
        GiveRequests.PokemonGive req = GiveRequests.parsePokemon(
                "{\"uuid\":\"" + UUID_STR + "\",\"pokespec\":\"Pikachu\",\"sendMessage\":false}");
        assertFalse(req.sendMessage());
    }

    @Test
    void rejectsPokemonGiveMissingFields() {
        assertThrows(BadRequest.class, () -> GiveRequests.parsePokemon(
                "{\"pokespec\":\"Pikachu\"}"));
        assertThrows(BadRequest.class, () -> GiveRequests.parsePokemon(
                "{\"uuid\":\"" + UUID_STR + "\"}"));
        assertThrows(BadRequest.class, () -> GiveRequests.parsePokemon(
                "{\"uuid\":\"" + UUID_STR + "\",\"pokespec\":\"  \"}"));
        assertThrows(BadRequest.class, () -> GiveRequests.parsePokemon(
                "{\"uuid\":\"not-a-uuid\",\"pokespec\":\"Pikachu\"}"));
        assertThrows(BadRequest.class, () -> GiveRequests.parsePokemon("not json"));
        assertThrows(BadRequest.class, () -> GiveRequests.parsePokemon("[]"));
    }

    // ---- /giveitems ----

    @Test
    void parsesAnItemsGiveWithDisplayAndLore() {
        GiveRequests.ItemsGive req = GiveRequests.parseItems(
                "{\"uuid\":\"" + UUID_STR + "\",\"items\":[{\"id\":\"minecraft:diamond\",\"amount\":5,"
                        + "\"display_name\":\"Shiny\",\"lore\":[\"line1\",\"line2\"]}]}");
        assertEquals(1, req.items().size());
        GiveRequests.ItemGive item = req.items().get(0);
        assertEquals("minecraft:diamond", item.id());
        assertEquals(5, item.amount());
        assertEquals("Shiny", item.displayName());
        assertEquals(2, item.lore().size());
    }

    /** display_name and lore are optional — an entry without them still parses. */
    @Test
    void parsesAnItemWithoutDisplayOrLore() {
        GiveRequests.ItemsGive req = GiveRequests.parseItems(
                "{\"uuid\":\"" + UUID_STR + "\",\"items\":[{\"id\":\"minecraft:bone\",\"amount\":2}]}");
        GiveRequests.ItemGive item = req.items().get(0);
        assertEquals(null, item.displayName());
        assertTrue(item.lore().isEmpty());
    }

    @Test
    void parsesMultiplePreSplitStacks() {
        // The backend splits amount:200 into 64/64/64/8 before sending; parse them as-is, never merge.
        GiveRequests.ItemsGive req = GiveRequests.parseItems(
                "{\"uuid\":\"" + UUID_STR + "\",\"items\":["
                        + "{\"id\":\"minecraft:diamond\",\"amount\":64},"
                        + "{\"id\":\"minecraft:diamond\",\"amount\":64},"
                        + "{\"id\":\"minecraft:diamond\",\"amount\":8}]}");
        assertEquals(3, req.items().size());
        assertEquals(64, req.items().get(0).amount());
        assertEquals(8, req.items().get(2).amount());
    }

    @Test
    void rejectsItemsGiveWithBadShape() {
        assertThrows(BadRequest.class, () -> GiveRequests.parseItems(
                "{\"uuid\":\"" + UUID_STR + "\",\"items\":[]}"));
        assertThrows(BadRequest.class, () -> GiveRequests.parseItems(
                "{\"uuid\":\"" + UUID_STR + "\"}"));
        assertThrows(BadRequest.class, () -> GiveRequests.parseItems(
                "{\"uuid\":\"" + UUID_STR + "\",\"items\":[{\"amount\":5}]}"));
        assertThrows(BadRequest.class, () -> GiveRequests.parseItems(
                "{\"uuid\":\"" + UUID_STR + "\",\"items\":[{\"id\":\"minecraft:diamond\"}]}"));
    }
}
