package es.boffmedia.teras.dungeon.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ids into readable names.
 *
 * <p>Three callers need this — an item with no lang entry, an elite's nameplate, a boss bar — and
 * lang entries only exist for what shipped in code. Gear showed what happens without a floor under
 * it: a config-defined piece rendered as {@code item.teras.escudo_hyliano} in a player's hand.</p>
 */
class NamesTest {

    @Test
    void anIdBecomesWords() {
        assertEquals("Reina Madre", Names.fromId("reina_madre"));
        assertEquals("Saqueador Cuevas", Names.fromId("saqueador_cuevas"));
        assertEquals("Escudo Hyliano", Names.fromId("escudo_hyliano"));
        assertEquals("Lepisma", Names.fromId("lepisma"));
    }

    /** Clone ids can carry a path; the separator is a word break, not part of a word. */
    @Test
    void pathSeparatorsAreWordBreaksToo() {
        assertEquals("Husk Guardian", Names.fromId("husk/guardian"));
    }

    @Test
    void nothingIsNeverRenderedAsNothing() {
        assertEquals("?", Names.fromId(null));
        assertEquals("?", Names.fromId("   "));
        assertEquals("_", Names.fromId("_"), "an id of only separators keeps itself");
    }
}
