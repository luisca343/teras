package es.boffmedia.teras.http;

import es.boffmedia.teras.http.JsonBody.BadRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The field names are the backend's {@code MovePokemonDto}, so these pin them. The negative cases pin
 * that a slot address is never guessed at: a swap built from a wrong number moves a Pokémon
 * somewhere nobody asked for.
 */
class StorageRequestsTest {

    private static final String UUID_STR = "67d9b543-5ac9-41e1-a8a5-20d7689e24a4";

    @Test
    void parsesTheUuidOfAReadBody() {
        assertEquals(UUID.fromString(UUID_STR),
                StorageRequests.parseUuid("{\"uuid\":\"" + UUID_STR + "\"}").uuid());
    }

    @Test
    void rejectsAReadBodyWithoutAUsableUuid() {
        assertThrows(BadRequest.class, () -> StorageRequests.parseUuid("{}"));
        assertThrows(BadRequest.class, () -> StorageRequests.parseUuid("{\"uuid\":\"nope\"}"));
        assertThrows(BadRequest.class, () -> StorageRequests.parseUuid("not json"));
    }

    @Test
    void parsesAMove() {
        StorageRequests.Move move = StorageRequests.parseMove("{\"uuid\":\"" + UUID_STR + "\","
                + "\"sourceBox\":2,\"sourceIndex\":7,\"destinationBox\":5,\"destinationIndex\":29}");
        assertEquals(UUID.fromString(UUID_STR), move.uuid());
        assertEquals(2, move.sourceBox());
        assertEquals(7, move.sourceIndex());
        assertEquals(5, move.destinationBox());
        assertEquals(29, move.destinationIndex());
    }

    /** Box -1 is the party. */
    @Test
    void parsesThePartyBox() {
        StorageRequests.Move move = StorageRequests.parseMove("{\"uuid\":\"" + UUID_STR + "\","
                + "\"sourceBox\":-1,\"sourceIndex\":0,\"destinationBox\":0,\"destinationIndex\":0}");
        assertEquals(-1, move.sourceBox());
    }

    @Test
    void rejectsAMoveMissingASlot() {
        assertThrows(BadRequest.class, () -> StorageRequests.parseMove("{\"uuid\":\"" + UUID_STR + "\","
                + "\"sourceBox\":0,\"sourceIndex\":0,\"destinationBox\":0}"));
    }

    /** The backend sends numbers; coercing "0" would hide a caller's bug. */
    @Test
    void rejectsANonNumericSlot() {
        assertThrows(BadRequest.class, () -> StorageRequests.parseMove("{\"uuid\":\"" + UUID_STR + "\","
                + "\"sourceBox\":\"0\",\"sourceIndex\":0,\"destinationBox\":0,\"destinationIndex\":0}"));
    }

    /** Truncating 1.5 to slot 1 would swap the wrong Pokémon. */
    @Test
    void rejectsAFractionalSlot() {
        assertThrows(BadRequest.class, () -> StorageRequests.parseMove("{\"uuid\":\"" + UUID_STR + "\","
                + "\"sourceBox\":0,\"sourceIndex\":1.5,\"destinationBox\":0,\"destinationIndex\":0}"));
    }
}
