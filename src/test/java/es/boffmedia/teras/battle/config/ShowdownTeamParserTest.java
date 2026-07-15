package es.boffmedia.teras.battle.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Engine-agnostic tests for the PokePaste/Showdown parser used by the Cobblemon provider. */
class ShowdownTeamParserTest {

    @Test
    void parsesFullSet() {
        String paste = """
                Pikachu (M) @ Light Ball
                Ability: Static
                Level: 50
                Shiny: Yes
                Tera Type: Electric
                EVs: 252 SpA / 4 SpD / 252 Spe
                Timid Nature
                IVs: 0 Atk
                - Thunderbolt
                - Volt Switch
                - Surf
                - Nasty Plot
                """;
        List<ShowdownSet> sets = ShowdownTeamParser.parse(paste);
        assertEquals(1, sets.size());
        ShowdownSet s = sets.get(0);

        assertEquals("Pikachu", s.species);
        assertNull(s.nickname);
        assertEquals("M", s.gender);
        assertEquals("Light Ball", s.item);
        assertEquals("Static", s.ability);
        assertEquals(50, s.level);
        assertTrue(s.shiny);
        assertEquals("Electric", s.teraType);
        assertEquals("Timid", s.nature);
        assertEquals(List.of("Thunderbolt", "Volt Switch", "Surf", "Nasty Plot"), s.moves);
        assertEquals(252, s.evs.get("SPA"));
        assertEquals(252, s.evs.get("SPE"));
        assertEquals(0, s.ivs.get("ATK"));
    }

    @Test
    void parsesNicknameAndSpecies() {
        ShowdownSet s = ShowdownTeamParser.parse("Sparky (Pikachu) (F) @ Leftovers\n").get(0);
        assertEquals("Sparky", s.nickname);
        assertEquals("Pikachu", s.species);
        assertEquals("F", s.gender);
        assertEquals("Leftovers", s.item);
    }

    @Test
    void parsesBareSpecies() {
        ShowdownSet s = ShowdownTeamParser.parse("Garchomp\n- Earthquake\n").get(0);
        assertEquals("Garchomp", s.species);
        assertNull(s.nickname);
        assertNull(s.gender);
        assertEquals(List.of("Earthquake"), s.moves);
    }

    @Test
    void parsesMultipleSetsSeparatedByBlankLines() {
        String paste = """
                Great Tusk @ Booster Energy
                - Headlong Rush

                Iron Valiant @ Booster Energy
                - Moonblast
                """;
        List<ShowdownSet> sets = ShowdownTeamParser.parse(paste);
        assertEquals(2, sets.size());
        assertEquals("Great Tusk", sets.get(0).species);
        assertEquals("Iron Valiant", sets.get(1).species);
    }

    @Test
    void emptyPasteYieldsNoSets() {
        assertTrue(ShowdownTeamParser.parse("").isEmpty());
        assertTrue(ShowdownTeamParser.parse(null).isEmpty());
        assertTrue(ShowdownTeamParser.parse("   \n  \n").isEmpty());
    }
}
