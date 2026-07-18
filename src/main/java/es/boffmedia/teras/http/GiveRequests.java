package es.boffmedia.teras.http;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The bodies of {@code POST /givepokemon} and {@code POST /giveitems}, parsed via {@link JsonBody}. */
final class GiveRequests {
    private GiveRequests() {}

    /** {@code sendMessage} defaults to true, as 1.16.5 did. */
    record PokemonGive(UUID uuid, String pokespec, boolean sendMessage) {}

    /** {@code display_name} and {@code lore} are optional. */
    record ItemGive(String id, int amount, String displayName, List<String> lore) {}

    record ItemsGive(UUID uuid, List<ItemGive> items) {}

    static PokemonGive parsePokemon(String body) {
        JsonObject json = JsonBody.object(body);
        UUID uuid = JsonBody.uuid(json);
        String pokespec = JsonBody.string(json, "pokespec");
        if (pokespec == null || pokespec.isBlank()) {
            throw new JsonBody.BadRequest("'pokespec' is required");
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
        JsonObject json = JsonBody.object(body);
        UUID uuid = JsonBody.uuid(json);
        JsonElement rawItems = json.get("items");
        if (rawItems == null || !rawItems.isJsonArray() || rawItems.getAsJsonArray().isEmpty()) {
            throw new JsonBody.BadRequest("'items' is required and cannot be empty");
        }
        List<ItemGive> items = new ArrayList<>();
        for (JsonElement element : rawItems.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new JsonBody.BadRequest("'items' entries must be objects");
            }
            JsonObject entry = element.getAsJsonObject();
            String id = JsonBody.string(entry, "id");
            if (id == null || id.isBlank()) {
                throw new JsonBody.BadRequest("every item needs an 'id'");
            }
            JsonElement amount = entry.get("amount");
            if (amount == null || !amount.isJsonPrimitive() || !amount.getAsJsonPrimitive().isNumber()) {
                throw new JsonBody.BadRequest("item '" + id + "' needs a numeric 'amount'");
            }
            items.add(new ItemGive(id, amount.getAsInt(), JsonBody.string(entry, "display_name"),
                    JsonBody.stringList(entry, "lore")));
        }
        return new ItemsGive(uuid, items);
    }
}
