package es.boffmedia.teras.http;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Typed reads over a request body, shared by every POST route. Parsed by hand rather than bound with
 * Gson so a malformed field is a 400 with a reason, not a half-built object.
 */
final class JsonBody {
    private JsonBody() {}

    /** Thrown with a caller-safe reason; the handler turns it into a 400. */
    static final class BadRequest extends RuntimeException {
        BadRequest(String message) {
            super(message);
        }
    }

    static JsonObject object(String body) {
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

    static UUID uuid(JsonObject json) {
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

    static String string(JsonObject json, String key) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return null;
        }
        return value.getAsString();
    }

    /** Required; rejects missing, non-numeric and fractional values. */
    static int integer(JsonObject json, String key) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new BadRequest("'" + key + "' is required and must be a number");
        }
        double raw = value.getAsDouble();
        if (raw != Math.floor(raw) || Double.isInfinite(raw)) {
            throw new BadRequest("'" + key + "' must be a whole number");
        }
        return value.getAsInt();
    }

    static List<String> stringList(JsonObject json, String key) {
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
