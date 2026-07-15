package es.boffmedia.teras.battle.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Engine-agnostic tests for the combat-config model. These exercise the config-file contract (the
 * part the SmartRotom web tooling writes and the mod reads) without any Minecraft or Pokémon-engine
 * runtime, so they run in plain JUnit on either engine's absence.
 */
class BattleConfigTest {

    private static final Gson GSON = new Gson();

    @Test
    void deserializesTrainerConfigFromJson() {
        String json = """
                {
                  "nombre": "Líder Roca",
                  "nivel": "+5",
                  "dinero": 3000,
                  "modalidad": "doble",
                  "tamanoEquipos": "3vs6",
                  "curar": true,
                  "preview": false,
                  "normas": ["sleepclause", "speciesclause"],
                  "equipos": [1, 2]
                }
                """;
        BattleConfig c = GSON.fromJson(json, BattleConfig.class);

        assertEquals("Líder Roca", c.getNombre());
        assertEquals(3000, c.getDinero());
        assertTrue(c.healBeforeStart());
        assertFalse(c.hasPreview());
        assertEquals(BattleMode.DOUBLE, c.getBattleMode());
        assertEquals(2, c.getPlayerActiveCount());   // doubles → 2 active per side
        assertEquals(2, c.getRivalActiveCount());
        assertEquals(2, c.getClauses().size());
        assertTrue(c.getClauses().contains("sleepclause"));
    }

    @Test
    void parsesTeamSizesFromAvsB() {
        BattleConfig c = new BattleConfig();
        c.setTamanoEquipos("3vs6");
        assertEquals(3, c.getPlayerTeamSize());
        assertEquals(6, c.getRivalTeamSize());
    }

    @Test
    void teamSizeFallsBackToFullPartyWhenMalformed() {
        BattleConfig c = new BattleConfig();
        c.setTamanoEquipos("garbage");
        assertEquals(6, c.getPlayerTeamSize());
        assertEquals(6, c.getRivalTeamSize());
    }

    @Test
    void levelSpecResolvesRelativeAndAbsolute() {
        BattleConfig c = new BattleConfig();

        c.setNivel("+5");
        assertEquals(55, c.calculateTeamLevel(50));

        c.setNivel("-3");
        assertEquals(47, c.calculateTeamLevel(50));

        c.setNivel("=");
        assertEquals(50, c.calculateTeamLevel(50));

        c.setNivel("80");
        assertEquals(80, c.calculateTeamLevel(50));
    }

    @Test
    void battleModeMapsSpanishLabels() {
        assertEquals(BattleMode.SINGLE, BattleMode.fromLabel(null));
        assertEquals(BattleMode.SINGLE, BattleMode.fromLabel("cualquiera"));
        assertEquals(BattleMode.DOUBLE, BattleMode.fromLabel("doble"));
        assertEquals(BattleMode.TRIPLE, BattleMode.fromLabel("triple"));
        assertEquals(BattleMode.HORDE, BattleMode.fromLabel("horda"));
        assertEquals(BattleMode.RAID, BattleMode.fromLabel("raid"));
        // Horde: 1 player Pokémon out vs 5 wild.
        assertEquals(1, BattleMode.HORDE.playerActive());
        assertEquals(5, BattleMode.HORDE.rivalActive());
    }

    @Test
    void aiModeAcceptsEnglishAndSpanish() {
        assertEquals(AiMode.TACTICAL, AiMode.fromLabel("TACTICAL"));
        assertEquals(AiMode.TACTICAL, AiMode.fromLabel("táctica"));
        assertEquals(AiMode.AGGRESSIVE, AiMode.fromLabel("agresiva"));
        assertEquals(AiMode.DEFAULT, AiMode.fromLabel("???"));
    }

    @Test
    void gimmicksDeserializeFromMecanicaKeyAndResolveCaseInsensitively() {
        // The SmartRotom config uses the "mecanica" key (the Java field is "gimmick").
        String json = """
                { "mecanica": ["MEGA", "Tera", "z"] }
                """;
        BattleConfig c = GSON.fromJson(json, BattleConfig.class);

        assertTrue(c.allowsMega());
        assertTrue(c.allowsTera());
        assertTrue(c.allowsZ());
        assertFalse(c.allowsDynamax());
    }

    @Test
    void gimmicksStillDeserializeFromLegacyGimmickKey() {
        BattleConfig c = GSON.fromJson("{ \"gimmick\": [\"dynamax\"] }", BattleConfig.class);
        assertTrue(c.allowsDynamax());
        assertFalse(c.allowsMega());
    }

    @Test
    void noGimmicksWhenAbsent() {
        BattleConfig c = GSON.fromJson("{}", BattleConfig.class);
        assertFalse(c.allowsMega());
        assertFalse(c.allowsDynamax());
        assertFalse(c.allowsTera());
        assertFalse(c.allowsZ());
    }

    @Test
    void trainerVsEventFolderDistinction() {
        BattleConfig trainer = new BattleConfig();
        trainer.setCarpeta("entrenadores");
        assertTrue(trainer.esEntrenador());

        BattleConfig event = new BattleConfig();
        event.setCarpeta("eventos");
        assertFalse(event.esEntrenador());
    }
}
