package es.boffmedia.teras.http;

import com.google.gson.JsonObject;

import java.util.UUID;

/** The bodies of {@code POST /pc}, {@code POST /equipo} and {@code POST /pc/move}. */
final class StorageRequests {
    private StorageRequests() {}

    /** All the shared handler needs: every PC route names the player the same way. */
    sealed interface Request {
        UUID uuid();
    }

    /** The read routes take nothing but a uuid. */
    record Read(UUID uuid) implements Request {}

    /** The backend's {@code MovePokemonDto}, field names included. Box {@code -1} is the party. */
    record Move(UUID uuid, int sourceBox, int sourceIndex, int destinationBox, int destinationIndex)
            implements Request {}

    static Read parseUuid(String body) {
        return new Read(JsonBody.uuid(JsonBody.object(body)));
    }

    static Move parseMove(String body) {
        JsonObject json = JsonBody.object(body);
        return new Move(
                JsonBody.uuid(json),
                JsonBody.integer(json, "sourceBox"),
                JsonBody.integer(json, "sourceIndex"),
                JsonBody.integer(json, "destinationBox"),
                JsonBody.integer(json, "destinationIndex"));
    }
}
