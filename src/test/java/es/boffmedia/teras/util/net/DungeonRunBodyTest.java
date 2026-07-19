package es.boffmedia.teras.util.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import es.boffmedia.teras.dungeon.instance.DungeonRunResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wire shape of {@code POST /smartrotom/dungeons/run}. Pinned in a test because the backend
 * route is written against this document (docs/SMARTROTOM_ENDPOINTS_HANDOFF.md) and the two are
 * deployed independently — a renamed field here is a silently dropped column there.
 */
class DungeonRunBodyTest {

    private static DungeonRunResult result(boolean completed) {
        return new DungeonRunResult("semilla-1", 1, 4, 3, completed, 725_000L,
                List.of("LABYRINTH"), 480, 320, 1600,
                List.of(new DungeonRunResult.Participant(
                                "11111111-1111-1111-1111-111111111111", "Ana", 2, false),
                        new DungeonRunResult.Participant(
                                "22222222-2222-2222-2222-222222222222", "Beto", 0, true)));
    }

    @Test
    void carriesTheServerIdAtTheTopLevel() {
        // A tripwire route: the backend's MinecraftMiddleware 403s a body without it.
        JsonObject body = JsonParser.parseString(
                SmartRotomService.dungeonBody("teras-1", result(true), 1_700_000_000_000L))
                .getAsJsonObject();

        assertEquals("teras-1", body.get("server").getAsString());
    }

    @Test
    void carriesTheRunSummary() {
        JsonObject body = JsonParser.parseString(
                SmartRotomService.dungeonBody("teras-1", result(true), 1_700_000_000_000L))
                .getAsJsonObject();

        assertEquals("semilla-1", body.get("semilla").getAsString());
        assertEquals(1, body.get("etapaInicial").getAsInt());
        assertEquals(4, body.get("etapaFinal").getAsInt());
        assertEquals(3, body.get("pisosSuperados").getAsInt());
        assertTrue(body.get("completada").getAsBoolean());
        assertEquals(725_000L, body.get("duracionMs").getAsLong());
        assertEquals(1_700_000_000_000L, body.get("fecha").getAsLong());
        assertEquals("LABYRINTH", body.getAsJsonArray("maldiciones").get(0).getAsString());
    }

    /** Coins are run-level: the purse is shared, so there is no per-player split to report. */
    @Test
    void carriesTheSharedCoinTotals() {
        JsonObject body = JsonParser.parseString(
                SmartRotomService.dungeonBody("teras-1", result(true), 0L)).getAsJsonObject();

        assertEquals(480, body.get("monedasGanadas").getAsInt());
        assertEquals(320, body.get("monedasGastadas").getAsInt());
        assertEquals(1600, body.get("monedasConvertidas").getAsInt());
        assertFalse(body.has("monedas"), "coins must not also appear per participant");
    }

    @Test
    void carriesEveryParticipantWithDeathsAndWhetherTheyWalkedOut() {
        JsonObject body = JsonParser.parseString(
                SmartRotomService.dungeonBody("teras-1", result(true), 0L)).getAsJsonObject();
        var participants = body.getAsJsonArray("participantes");

        assertEquals(2, participants.size());
        JsonObject ana = participants.get(0).getAsJsonObject();
        assertEquals("11111111-1111-1111-1111-111111111111", ana.get("uuid").getAsString());
        assertEquals("Ana", ana.get("nombre").getAsString());
        assertEquals(2, ana.get("muertes").getAsInt());
        assertFalse(ana.get("abandono").getAsBoolean());
        assertTrue(participants.get(1).getAsJsonObject().get("abandono").getAsBoolean());
    }

    /** An abandoned run is still a row — the leaderboard wants the attempt, not just the wins. */
    @Test
    void reportsAbandonedRunsToo() {
        JsonObject body = JsonParser.parseString(
                SmartRotomService.dungeonBody("teras-1", result(false), 0L)).getAsJsonObject();

        assertFalse(body.get("completada").getAsBoolean());
    }
}
