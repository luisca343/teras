package es.boffmedia.teras.util.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import es.boffmedia.teras.karts.engine.RaceResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wire shape of {@code POST /smartrotom/karts/carrera}. Pinned in a test because the backend
 * route is written against this contract (docs/KARTS_CARRERA_CONTRACT.md) and the two are deployed
 * independently — a renamed field here is a silently dropped column there.
 *
 * <p>Every race report 400'd from the port until this contract landed: the mod sent {@code modo},
 * {@code vueltas} and {@code resultados} while the backend accepted only {@code participantes}, and
 * its pipe rejects unknown properties outright.</p>
 */
class RaceReportBodyTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BETO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    /** Ana wins on the road; Beto retires on lap 1, so he carries the sentinels. */
    private static RaceResult result() {
        return new RaceResult("Rainbow Road", "clasica", 3, List.of(
                new RaceResult.Placement(ANA, "Ana", 1, 90_500L, 29_800L, 3, false),
                new RaceResult.Placement(BETO, "Beto", 2, -1L, -1L, 1, true)));
    }

    private static JsonObject body() {
        return JsonParser.parseString(
                        SmartRotomService.raceBody("teras-1", result(), 1_700_000_000_000L))
                .getAsJsonObject();
    }

    @Test
    void carriesTheServerIdAtTheTopLevel() {
        // A tripwire route: the backend's MinecraftMiddleware 403s a body without it.
        assertEquals("teras-1", body().get("server").getAsString());
    }

    @Test
    void carriesTheRaceSummary() {
        JsonObject body = body();

        assertEquals("Rainbow Road", body.get("circuito").getAsString());
        assertEquals("clasica", body.get("modo").getAsString());
        assertEquals(3, body.get("vueltas").getAsInt());
        assertEquals(1_700_000_000_000L, body.get("fecha").getAsLong());
    }

    /**
     * The array is {@code participantes}, not {@code resultados}. This is the rename that unbroke the
     * route, so it is asserted by absence as well as presence.
     */
    @Test
    void racersTravelUnderParticipantes() {
        JsonObject body = body();

        assertTrue(body.has("participantes"));
        assertFalse(body.has("resultados"), "the backend whitelist rejects unknown properties");
        assertEquals(2, body.getAsJsonArray("participantes").size());
    }

    @Test
    void carriesEveryRacerInFinishingOrder() {
        JsonArray racers = body().getAsJsonArray("participantes");

        JsonObject ana = racers.get(0).getAsJsonObject();
        assertEquals(ANA.toString(), ana.get("uuid").getAsString());
        assertEquals("Ana", ana.get("nombre").getAsString());
        assertEquals(1, ana.get("posicion").getAsInt());
        assertEquals(90_500L, ana.get("tiempoMs").getAsLong());
        assertEquals(29_800L, ana.get("mejorVueltaMs").getAsLong());
        assertEquals(3, ana.get("vueltasCompletadas").getAsInt());
        assertFalse(ana.get("dnf").getAsBoolean());
    }

    /**
     * The -1 sentinels must survive serialization. A leaderboard has to tell "did not finish" from
     * "finished instantly", and 0 cannot say that — so normalizing them away here would produce a
     * backend ranking with every DNF at the top.
     */
    @Test
    void aRetiredRacerKeepsTheSentinelsAndThePositionTheyWouldHaveHeld() {
        JsonObject beto = body().getAsJsonArray("participantes").get(1).getAsJsonObject();

        assertTrue(beto.get("dnf").getAsBoolean());
        assertEquals(-1L, beto.get("tiempoMs").getAsLong());
        assertEquals(-1L, beto.get("mejorVueltaMs").getAsLong());
        assertEquals(2, beto.get("posicion").getAsInt());
    }

    /**
     * Laps driven, not laps configured. An elimination survivor wins without covering the distance,
     * so a time-based record table needs this to compare like with like.
     */
    @Test
    void carriesLapsActuallyDriven() {
        JsonArray racers = body().getAsJsonArray("participantes");

        assertEquals(3, racers.get(0).getAsJsonObject().get("vueltasCompletadas").getAsInt());
        assertEquals(1, racers.get(1).getAsJsonObject().get("vueltasCompletadas").getAsInt());
    }

    /** The whole body, key by key: the backend rejects any property it does not know. */
    @Test
    void sendsNothingTheBackendDoesNotAccept() {
        assertEquals(
                java.util.Set.of("server", "circuito", "modo", "vueltas", "fecha", "participantes"),
                body().keySet());
        assertEquals(
                java.util.Set.of("uuid", "nombre", "posicion", "tiempoMs", "mejorVueltaMs",
                        "vueltasCompletadas", "dnf"),
                body().getAsJsonArray("participantes").get(0).getAsJsonObject().keySet());
    }
}
