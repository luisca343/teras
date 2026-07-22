package es.boffmedia.teras.dungeon.piso;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mechanic a piso runs, and the numbers it runs it with.
 *
 * <p>Two properties matter more than the parsing: a config written before params existed still
 * means what it always meant, and a param that will not parse falls back to the shipped value
 * rather than taking the mechanic — or the floor — down with it.</p>
 */
class MechanicDefTest {

    @Test
    void aBareNameIsStillAMechanic() {
        MechanicDef def = MechanicDef.of("infestacion");
        assertEquals("infestacion", def.id());
        assertTrue(def.params().isEmpty());
        assertTrue(def.is("infestacion"));
        assertFalse(def.isNone());
    }

    @Test
    void emptyAndNullAreNoMechanic() {
        assertTrue(MechanicDef.of("").isNone());
        assertTrue(MechanicDef.of(null).isNone());
        assertTrue(MechanicDef.of("   ").isNone());
        assertTrue(MechanicDef.NONE.isNone());
        // A piso with no mechanic must never match one, or every hook would fire everywhere.
        assertFalse(MechanicDef.NONE.is(""));
        assertFalse(MechanicDef.NONE.is("infestacion"));
    }

    @Test
    void paramsOverrideAndAnythingUnsaidFallsBack() {
        MechanicDef def = new MechanicDef("infestacion",
                Map.of("retrasoTicks", "140", "cria", "lepisma_cueva"));
        assertEquals(140, def.intParam("retrasoTicks", 80));
        assertEquals("lepisma_cueva", def.param("cria", "cria"));
        assertEquals(2, def.intParam("porNido", 2), "unsaid params keep the shipped value");
    }

    /**
     * A mechanic that refused to run over a typo would take the floor's content down for a
     * cosmetic mistake, and the fallback is exactly the behaviour that shipped.
     */
    @Test
    void anUnparseableParamFallsBackRatherThanThrowing() {
        MechanicDef def = new MechanicDef("infestacion",
                Map.of("retrasoTicks", "80 ticks", "porNido", "", "peso", "mucho"));
        assertEquals(80, def.intParam("retrasoTicks", 80));
        assertEquals(2, def.intParam("porNido", 2));
        assertEquals(1.0, def.doubleParam("peso", 1.0));
        assertEquals("cria", def.param("porNido", "cria"), "a blank value is not an override");
    }

    /**
     * The reason the registry exists at all. A mechanic id that does not resolve used to be
     * indistinguishable from having none: the floor built, nothing ran, and nothing said why.
     */
    @Test
    void anUnknownIdIsReportedAndNamesWhatWouldHaveWorked() {
        List<String> known = List.of("infestacion");
        assertTrue(MechanicDef.of("infestacion").problems(known).isEmpty());
        assertTrue(MechanicDef.NONE.problems(known).isEmpty(), "no mechanic is not a mistake");

        List<String> problems = MechanicDef.of("infestacon").problems(known);
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("infestacon"));
        assertTrue(problems.get(0).contains("infestacion"), "it has to say what was valid");
    }

    @Test
    void nullsAreNormalisedSoNoCallerHasToGuard() {
        MechanicDef def = new MechanicDef(null, null);
        assertTrue(def.isNone());
        assertTrue(def.params().isEmpty());
    }
}
