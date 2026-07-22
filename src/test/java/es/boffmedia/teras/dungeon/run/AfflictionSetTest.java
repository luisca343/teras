package es.boffmedia.teras.dungeon.run;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The third currency: run-long drawbacks a party chooses to accept.
 *
 * <p>The catalog rule is the interesting part and is asserted here — every affliction has to change
 * <i>what you do</i>, not how long it takes. A drafted "doors stay sealed longer" was cut for
 * failing it, and there is no test that can catch that one; what can be caught is two afflictions
 * sharing an axis, which is why the ids and scopes are pinned.</p>
 */
class AfflictionSetTest {

    @Test
    void theCatalogIsWhatWeAgreed() {
        assertEquals(List.of("niebla", "enjambre", "avaricia", "sangria", "plomo", "pulso_debil"),
                Afliccion.all().stream().map(Afliccion::id).toList());
        assertTrue(Afliccion.NIEBLA.isParty());
        assertFalse(Afliccion.PLOMO.isParty(), "mobility is yours alone");
        assertFalse(Afliccion.PULSO_DEBIL.isParty(), "so is your body");
    }

    @Test
    void takingTheSameOneTwiceIsNotTwiceAsBad() {
        AfflictionSet set = new AfflictionSet();
        assertTrue(set.add(Afliccion.NIEBLA));
        assertFalse(set.add(Afliccion.NIEBLA), "an offer that was already taken should not be made");
        assertEquals(1, set.size());
    }

    /** Without a way to shed one, an affliction is a difficulty slider rather than a currency. */
    @Test
    void oneCanBeShed() {
        AfflictionSet set = new AfflictionSet();
        set.add(Afliccion.AVARICIA);
        assertTrue(set.remove(Afliccion.AVARICIA));
        assertFalse(set.has(Afliccion.AVARICIA));
        assertFalse(set.remove(Afliccion.AVARICIA), "shedding one you do not carry is not a sale");
    }

    @Test
    void thereIsNoCap() {
        AfflictionSet set = new AfflictionSet();
        Afliccion.all().forEach(set::add);
        assertEquals(Afliccion.all().size(), set.size(), "a party may take every one there is");
    }

    /** A curse room offers what you are not already carrying, of the scope it deals in. */
    @Test
    void offerableExcludesWhatIsCarried() {
        AfflictionSet set = new AfflictionSet();
        set.add(Afliccion.NIEBLA);
        List<Afliccion> party = set.offerable(Afliccion.Scope.PARTY);
        assertFalse(party.contains(Afliccion.NIEBLA));
        assertTrue(party.contains(Afliccion.AVARICIA));
        assertTrue(party.stream().allMatch(Afliccion::isParty), "scope is respected");
    }

    @Test
    void carriedKeepsTheOrderTaken() {
        AfflictionSet set = new AfflictionSet();
        set.add(Afliccion.SANGRIA);
        set.add(Afliccion.NIEBLA);
        assertEquals(List.of("sangria", "niebla"), set.carried().stream().map(Afliccion::id).toList());
    }

    @Test
    void anUnknownIdIsReported() {
        assertTrue(Afliccion.problems(List.of("niebla", "plomo")).isEmpty());
        List<String> problems = Afliccion.problems(List.of("cerrojos"));
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("cerrojos"));
        assertTrue(problems.get(0).contains("niebla"), "it has to say what was valid");
    }
}
