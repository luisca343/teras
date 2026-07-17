package es.boffmedia.teras.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The bodies of {@code POST /givepokemon} and {@code POST /giveitems}, parsed defensively.
 *
 * <p>These shapes are the SmartRotom backend's, not ours: it has 23 raw axios call sites and no shared
 * client, so the wire is whatever those senders send. Parsed by hand rather than bound with Gson so a
 * malformed field is a 400 with a reason, not a half-built object.</p>
 */
final class GiveRequests {
    private GiveRequests() {}

    /** {@code {uuid, pokespec, sendMessage}} — {@code sendMessage} defaults to true, as 1.16.5 did. */
    record PokemonGive(UUID uuid, String pokespec, boolean sendMessage) {}

    /** One entry of {@code items[]}. {@code display_name} and {@code lore} are optional. */
    record ItemGive(String id, int amount, String displayName, List<String> lore) {}

    /** {@code {uuid, items:[…]}}. */
    record ItemsGive(UUID uuid, List<ItemGive> items) {}

    /** Thrown with a caller-safe reason; the handler turns it into a 400. */
    static final class BadRequest extends RuntimeException {
        BadRequest(String message) {
            super(message);
        }
    }

    static PokemonGive parsePokemon(String body) {
        JsonObject json = object(body);
        UUID uuid = uuid(json);
        String pokespec = string(json, "pokespec");
        if (pokespec == null || pokespec.isBlank()) {
            throw new BadRequest("'pokespec' is required");
        }
        JsonElement sendMessage = json.get("sendMessage");
        boolean send = true;
        if (sendMessage != null && sendMessage.isJsonPrimitive()
                && sendMessage.getAsJsonPrimitive().isBoolean()) {
            send = sendMessage.getAsBoolean();
        }
        return new PokemonGive(uuid, pokespec, send);
    }

    static ItemsGive parseItems(String body) {
        JsonObject json = object(body);
        UUID uuid = uuid(json);
        JsonElement rawItems = json.get("items");
        if (rawItems == null || !rawItems.isJsonArray() || rawItems.getAsJsonArray().isEmpty()) {
            throw new BadRequest("'items' is required and cannot be empty");
        }
        List<ItemGive> items = new ArrayList<>();
        for (JsonElement element : rawItems.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new BadRequest("'items' entries must be objects");
            }
            JsonObject entry = element.getAsJsonObject();
            String id = string(entry, "id");
            if (id == null || id.isBlank()) {
                throw new BadRequest("every item needs an 'id'");
            }
            JsonElement amount = entry.get("amount");
            if (amount == null || !amount.isJsonPrimitive() || !amount.getAsJsonPrimitive().isNumber()) {
                throw new BadRequest("item '" + id + "' needs a numeric 'amount'");
            }
            items.add(new ItemGive(id, amount.getAsInt(), string(entry, "display_name"),
                    stringList(entry, "lore")));
        }
        return new ItemsGive(uuid, items);
    }

    private static JsonObject object(String body) {
        try {
            JsonElement parsed = JsonParser.parseString(body == null ? "" : body);
            if (!parsed.isJsonObject()) {
                throw new BadRequest("body must be a JSON object");
            }
            return parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new BadRequest("body is not valid JSON");
        }
    }

    private static UUID uuid(JsonObject json) {
        String raw = string(json, "uuid");
        if (raw == null || raw.isBlank()) {
            throw new BadRequest("'uuid' is required");
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequest("'uuid' is malformed");
        }
    }

    private static String string(JsonObject json, String key) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return null;
        }
        return value.getAsString();
    }

    private static List<String> stringList(JsonObject json, String key) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray()) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                out.add(element.getAsString());
            }
        }
        return out;
    }
}
