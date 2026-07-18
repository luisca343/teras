package es.boffmedia.teras.karts.vehicle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link KartSpec} is how a kart is named in configs, commands and the garage file, so its string
 * form has to round-trip exactly — a spec that parses back into a different kart would silently put
 * a racer in the wrong car.
 */
class KartSpecTest {

    @Test
    @DisplayName("round-trips a spec with a variant")
    void roundTripsWithSubName() {
        KartSpec spec = new KartSpec("oamp", "hatchback", "red");
        assertEquals("oamp:hatchback:red", spec.toId());
        assertEquals(spec, KartSpec.parse(spec.toId()));
    }

    @Test
    @DisplayName("omits the variant when there is none, and round-trips that too")
    void roundTripsWithoutSubName() {
        KartSpec spec = KartSpec.of("oamp", "hatchback");
        assertEquals("oamp:hatchback", spec.toId());
        assertEquals(spec, KartSpec.parse(spec.toId()));
        assertEquals("", spec.subName());
    }

    @Test
    @DisplayName("treats a null variant as absent rather than carrying a null through")
    void normalisesNulls() {
        KartSpec spec = new KartSpec("oamp", "hatchback", null);
        assertEquals("", spec.subName());
        assertEquals("oamp:hatchback", spec.toId());
    }

    @Test
    @DisplayName("trims whitespace around every part, so a hand-edited config still resolves")
    void trimsWhitespace() {
        assertEquals(new KartSpec("oamp", "hatchback", "red"),
                KartSpec.parse("  oamp : hatchback : red  "));
        assertEquals("oamp", new KartSpec(" oamp ", "hatchback", "").packId());
    }

    @Test
    @DisplayName("a variant containing a colon survives, because only the first two colons split")
    void keepsColonsInsideVariant() {
        KartSpec spec = KartSpec.parse("pack:model:variant:with:colons");
        assertEquals("pack", spec.packId());
        assertEquals("model", spec.systemName());
        assertEquals("variant:with:colons", spec.subName());
    }

    @Test
    @DisplayName("rejects anything that is not at least pack:model")
    void rejectsMalformed() {
        assertNull(KartSpec.parse(null));
        assertNull(KartSpec.parse(""));
        assertNull(KartSpec.parse("solopack"));
        assertNull(KartSpec.parse(":modelo"));
        assertNull(KartSpec.parse("pack:"));
    }

    @Test
    @DisplayName("validity requires both a pack and a model")
    void validity() {
        assertTrue(KartSpec.of("oamp", "hatchback").isValid());
        assertFalse(KartSpec.of("", "hatchback").isValid());
        assertFalse(KartSpec.of("oamp", "").isValid());
    }
}
