package es.boffmedia.teras.karts.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import es.boffmedia.teras.karts.engine.KartsRaceManager;
import es.boffmedia.teras.karts.engine.RaceSession;
import es.boffmedia.teras.karts.model.KartTrack;
import es.boffmedia.teras.karts.model.TrackValidator;
import es.boffmedia.teras.karts.store.LeaderboardStore;
import es.boffmedia.teras.karts.store.TrackStore;

import java.util.List;

/**
 * Shapes karts data for the HTTP API — the same job {@code RegionJson} does for regions, kept out of
 * the stores so the wire format can change without touching what is saved to disk.
 */
public final class KartsJsonView {
    private KartsJsonView() {}

    /** A circuit's records: best total times and best single laps. */
    public static JsonObject leaderboard(String track, int limit) {
        JsonObject json = new JsonObject();
        json.addProperty("circuito", track);
        json.add("mejoresTiempos", records(LeaderboardStore.bestTimes(track, limit)));
        json.add("mejorVuelta", records(LeaderboardStore.bestLaps(track, limit)));
        return json;
    }

    /** Every circuit that has records, for a leaderboard index. */
    public static JsonArray leaderboardIndex(int limit) {
        JsonArray array = new JsonArray();
        for (String track : LeaderboardStore.trackNames()) {
            array.add(leaderboard(track, limit));
        }
        return array;
    }

    private static JsonArray records(List<LeaderboardStore.Record> records) {
        JsonArray array = new JsonArray();
        int position = 1;
        for (LeaderboardStore.Record record : records) {
            JsonObject json = new JsonObject();
            json.addProperty("posicion", position++);
            json.addProperty("uuid", record.playerId().toString());
            json.addProperty("nombre", record.playerName());
            json.addProperty("tiempoMs", record.timeMs());
            json.addProperty("vueltas", record.laps());
            json.addProperty("fecha", record.achievedAt());
            array.add(json);
        }
        return array;
    }

    /** What is happening right now: the circuits available and any race under way. */
    public static JsonObject status() {
        JsonObject root = new JsonObject();

        JsonArray circuits = new JsonArray();
        for (String name : TrackStore.names()) {
            KartTrack track = TrackStore.get(name);
            JsonObject json = new JsonObject();
            json.addProperty("nombre", name);
            json.addProperty("displayName", track.displayName());
            json.addProperty("vueltasDefecto", track.defaultLaps());
            json.addProperty("plazas", track.gridSize());
            json.addProperty("checkpoints", track.checkpoints().size());
            json.addProperty("disponible", TrackValidator.isRaceable(track));
            circuits.add(json);
        }
        root.add("circuitos", circuits);

        JsonArray races = new JsonArray();
        for (RaceSession session : KartsRaceManager.activeRaces()) {
            JsonObject json = new JsonObject();
            json.addProperty("circuito", session.race().trackName());
            json.addProperty("estado", session.race().phase().name());
            json.addProperty("modo", session.race().mode().id());
            json.addProperty("vueltas", session.race().laps());

            JsonArray racers = new JsonArray();
            session.race().participants().forEach(participant -> {
                JsonObject entry = new JsonObject();
                entry.addProperty("uuid", participant.playerId().toString());
                entry.addProperty("nombre", participant.playerName());
                entry.addProperty("vuelta", participant.currentLap());
                entry.addProperty("posicion", session.race().positionOf(participant.playerId()));
                entry.addProperty("terminado", participant.hasFinished());
                entry.addProperty("abandonado", participant.dnf());
                racers.add(entry);
            });
            json.add("participantes", racers);
            races.add(json);
        }
        root.add("carreras", races);
        return root;
    }
}
