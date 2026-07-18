package es.boffmedia.teras.karts.store;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import es.boffmedia.teras.karts.model.KartTrack;
import es.boffmedia.teras.karts.model.TrackCheckpoint;
import es.boffmedia.teras.karts.model.TrackPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Circuits are the one thing in karts an admin cannot rebuild quickly, so the file format has to
 * round-trip exactly and the one-way upgrade from the 1.16.5 file has to preserve the geometry.
 */
class KartsJsonTest {

    private static final Gson GSON = new Gson();

    private static KartTrack sampleTrack() {
        KartTrack track = new KartTrack("circuito_playa");
        track.setDisplayName("Circuito de la Playa");
        track.setDimension("minecraft:overworld");
        track.setDefaultLaps(5);
        track.addStartingPoint(new TrackPoint(10.5, 64, 20.5, 90f));
        track.addStartingPoint(new TrackPoint(13.5, 64, 20.5, 90f));
        track.addCheckpoint(new TrackCheckpoint(TrackPoint.at(0, 64, 0), TrackPoint.at(4, 68, 4)));
        track.addCheckpoint(new TrackCheckpoint(TrackPoint.at(50, 64, 0), TrackPoint.at(54, 68, 4), 2.5));
        track.addCheckpoint(new TrackCheckpoint(TrackPoint.at(50, 64, 50), TrackPoint.at(54, 68, 54)));
        return track;
    }

    @Test
    @DisplayName("a circuit survives a write/read round-trip unchanged")
    void roundTripsCurrentFormat() {
        Map<String, KartTrack> original = new LinkedHashMap<>();
        original.put("circuito_playa", sampleTrack());

        Map<String, KartTrack> reloaded = KartsJson.read(KartsJson.write(original));

        assertEquals(1, reloaded.size());
        KartTrack track = reloaded.get("circuito_playa");
        assertEquals("Circuito de la Playa", track.displayName());
        assertEquals("minecraft:overworld", track.dimension());
        assertEquals(5, track.defaultLaps());

        assertEquals(2, track.startingPoints().size());
        assertEquals(10.5, track.startingPoints().get(0).x(), 1.0e-9);
        assertEquals(90f, track.startingPoints().get(0).yaw(), 1.0e-6);

        assertEquals(3, track.checkpoints().size());
        assertEquals(2.5, track.checkpoints().get(1).padding(), 1.0e-9);
        assertEquals(4, track.checkpoints().get(0).cornerB().x(), 1.0e-9);
    }

    @Test
    @DisplayName("the written file declares its version and nests circuits under a key")
    void writesVersionedShape() {
        JsonObject root = KartsJson.write(Map.of("c", sampleTrack()));
        assertEquals(KartsJson.CURRENT_VERSION, root.get("version").getAsInt());
        assertTrue(root.has("circuitos"));
        assertFalse(KartsJson.isLegacy(root));
    }

    @Test
    @DisplayName("a 1.16.5 file is recognised as legacy and its geometry converts intact")
    void migratesLegacyFormat() {
        String legacy = """
                {
                  "circuito_viejo": {
                    "name": "circuito_viejo",
                    "startingDirection": "EAST",
                    "startingPoints": [
                      {"x": 1.0, "y": 64.0, "z": 2.0},
                      {"x": 4.0, "y": 64.0, "z": 2.0}
                    ],
                    "checkpoints": [
                      {"start": {"x": 0.0, "y": 64.0, "z": 0.0}, "end": {"x": 4.0, "y": 68.0, "z": 4.0}},
                      {"start": {"x": 20.0, "y": 64.0, "z": 0.0}, "end": {"x": 24.0, "y": 68.0, "z": 4.0}},
                      {"start": {"x": 20.0, "y": 64.0, "z": 20.0}, "end": {"x": 24.0, "y": 68.0, "z": 24.0}}
                    ]
                  }
                }
                """;
        JsonObject root = GSON.fromJson(legacy, JsonObject.class);
        assertTrue(KartsJson.isLegacy(root));

        Map<String, KartTrack> tracks = KartsJson.read(root);
        KartTrack track = tracks.get("circuito_viejo");

        assertEquals(2, track.startingPoints().size());
        assertEquals(3, track.checkpoints().size());
        assertEquals(1.0, track.startingPoints().get(0).x(), 1.0e-9);
        assertEquals(24.0, track.checkpoints().get(2).cornerB().x(), 1.0e-9);
    }

    @Test
    @DisplayName("the legacy compass direction becomes a per-slot yaw")
    void convertsStartingDirectionToYaw() {
        assertEquals(-90f, yawFor("EAST"), 1.0e-6);
        assertEquals(90f, yawFor("WEST"), 1.0e-6);
        assertEquals(180f, yawFor("NORTH"), 1.0e-6);
        assertEquals(0f, yawFor("SOUTH"), 1.0e-6);
    }

    private static float yawFor(String direction) {
        String legacy = """
                { "c": { "name": "c", "startingDirection": "%s",
                         "startingPoints": [{"x":0.0,"y":64.0,"z":0.0}],
                         "checkpoints": [] } }
                """.formatted(direction);
        Map<String, KartTrack> tracks = KartsJson.read(GSON.fromJson(legacy, JsonObject.class));
        return tracks.get("c").startingPoints().get(0).yaw();
    }

    @Test
    @DisplayName("a migrated circuit has no dimension, so validation asks for a re-save")
    void legacyTracksHaveNoDimension() {
        String legacy = """
                { "c": { "name": "c", "startingDirection": "NORTH",
                         "startingPoints": [{"x":0.0,"y":64.0,"z":0.0}],
                         "checkpoints": [] } }
                """;
        Map<String, KartTrack> tracks = KartsJson.read(GSON.fromJson(legacy, JsonObject.class));
        assertNull(tracks.get("c").dimension());
    }

    @Test
    @DisplayName("one malformed circuit is skipped without costing the rest of the file")
    void skipsMalformedEntries() {
        String mixed = """
                { "version": 2,
                  "circuitos": {
                    "roto": { "salidas": [ {"x": "no-es-un-numero"} ] },
                    "bueno": { "displayName": "Bueno", "vueltasDefecto": 2,
                               "salidas": [ {"x":1.0,"y":64.0,"z":1.0,"yaw":0.0} ],
                               "checkpoints": [] }
                  } }
                """;
        Map<String, KartTrack> tracks = KartsJson.read(GSON.fromJson(mixed, JsonObject.class));
        assertFalse(tracks.containsKey("roto"));
        assertTrue(tracks.containsKey("bueno"));
        assertEquals(2, tracks.get("bueno").defaultLaps());
    }

    @Test
    @DisplayName("an empty or absent document yields no circuits rather than failing")
    void toleratesEmptyDocuments() {
        assertTrue(KartsJson.read(null).isEmpty());
        assertTrue(KartsJson.read(new JsonObject()).isEmpty());
        assertTrue(KartsJson.read(GSON.fromJson("{\"version\":2}", JsonObject.class)).isEmpty());
    }
}
