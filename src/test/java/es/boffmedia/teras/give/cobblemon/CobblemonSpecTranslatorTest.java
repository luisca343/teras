package es.boffmedia.teras.give.cobblemon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Pixelmon→Cobblemon spec translator. The load-bearing assertion is not what it maps but what it
 * refuses: Cobblemon parses leniently, so an unmapped token means a silently wrong Pokémon. Refusing
 * must stay the default for anything unverified.
 */
class CobblemonSpecTranslatorTest {

    @Test
    void mapsSpeciesAndLevel() {
        assertEquals("species=incineroar level=50",
                CobblemonSpecTranslator.translate("Incineroar lvl:50"));
    }

    @Test
    void mapsBareSpeciesAlone() {
        assertEquals("species=pikachu", CobblemonSpecTranslator.translate("Pikachu"));
    }

    @Test
    void mapsShinyBothAsFlagAndAsKey() {
        assertEquals("species=gengar shiny=true", CobblemonSpecTranslator.translate("Gengar shiny"));
        assertEquals("species=gengar shiny=true",
                CobblemonSpecTranslator.translate("Gengar shiny:true"));
    }

    /**
     * The exact spec every live pokemon row carries. It MUST refuse — otn/ivhp have no verified
     * Cobblemon mapping, and granting a plain level-50 Incineroar instead of Wolfey's 31-IV one is the
     * silent-wrong-Pokémon failure this whole class exists to prevent.
     */
    @Test
    void refusesTheLiveSpecRatherThanDroppingOtnAndIvs() {
        assertNull(CobblemonSpecTranslator.translate("Incineroar lvl:50 otn:Wolfey ivhp:31"));
    }

    @Test
    void refusesAnyUnmappedModifier() {
        assertNull(CobblemonSpecTranslator.translate("Incineroar ivhp:31"));
        assertNull(CobblemonSpecTranslator.translate("Incineroar otn:Wolfey"));
        assertNull(CobblemonSpecTranslator.translate("Incineroar nature:adamant"));
        assertNull(CobblemonSpecTranslator.translate("Incineroar ability:blaze"));
    }

    @Test
    void refusesInputWithNoSpecies() {
        assertNull(CobblemonSpecTranslator.translate("lvl:50"));
        assertNull(CobblemonSpecTranslator.translate(""));
        assertNull(CobblemonSpecTranslator.translate("   "));
        assertNull(CobblemonSpecTranslator.translate(null));
    }

    /** A second bare token can't be a second species — that's ambiguous, so refuse. */
    @Test
    void refusesASecondBareToken() {
        assertNull(CobblemonSpecTranslator.translate("Incineroar Pikachu"));
    }

    @Test
    void isCaseInsensitiveOnKeysAndLowercasesSpecies() {
        assertEquals("species=incineroar level=50",
                CobblemonSpecTranslator.translate("INCINEROAR LVL:50"));
    }

    @Test
    void whatItAcceptsNeverSilentlyLosesAModifier() {
        // Everything accepted round-trips to a level, a species, or a shiny — never an empty result
        // that would look like a plain-species grant.
        String out = CobblemonSpecTranslator.translate("Incineroar lvl:50 shiny");
        assertTrue(out.contains("species=incineroar"));
        assertTrue(out.contains("level=50"));
        assertTrue(out.contains("shiny=true"));
    }
}
