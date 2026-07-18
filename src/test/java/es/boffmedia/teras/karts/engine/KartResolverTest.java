package es.boffmedia.teras.karts.engine;

import es.boffmedia.teras.karts.model.KartProvisioning;
import es.boffmedia.teras.karts.vehicle.KartLoadout;
import es.boffmedia.teras.karts.vehicle.KartSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which kart each racer ends up in. The cases that matter are the ones where a racer never chose,
 * or chose something that has since stopped existing — those must still put them on the grid, or
 * fail visibly, never hand back a broken kart.
 */
class KartResolverTest {

    private static final UUID ANA = UUID.nameUUIDFromBytes("ana".getBytes());
    private static final UUID BEA = UUID.nameUUIDFromBytes("bea".getBytes());

    private static final KartSpec ESTANDAR = KartSpec.parse("oamp:hatchback:blue");
    private static final KartSpec DEPORTIVO = KartSpec.parse("oamp:coupe:red");
    private static final KartSpec PROPIO = KartSpec.parse("ocp:pickup");

    /** An in-memory stand-in for the preset, selection and garage files. */
    private static final class FakeCatalog implements KartResolver.Catalog {
        final Map<String, KartLoadout> presets = new LinkedHashMap<>();
        final Map<String, List<String>> selections = new LinkedHashMap<>();
        final Map<UUID, Map<String, KartLoadout>> garages = new LinkedHashMap<>();
        final Map<UUID, String> preferred = new LinkedHashMap<>();

        @Override
        public Optional<KartLoadout> presetLoadout(String presetName) {
            return Optional.ofNullable(presets.get(presetName));
        }

        @Override
        public List<String> selection(String selectionName) {
            return selections.getOrDefault(selectionName, List.of());
        }

        @Override
        public List<String> garageEntryIds(UUID player) {
            return new ArrayList<>(garages.getOrDefault(player, Map.of()).keySet());
        }

        @Override
        public Optional<KartLoadout> garageLoadout(UUID player, String entryId) {
            return Optional.ofNullable(garages.getOrDefault(player, Map.of()).get(entryId));
        }

        @Override
        public Optional<KartLoadout> preferredGarageLoadout(UUID player) {
            Map<String, KartLoadout> owned = garages.getOrDefault(player, Map.of());
            String choice = preferred.get(player);
            if (choice != null && owned.containsKey(choice)) {
                return Optional.of(owned.get(choice));
            }
            return owned.values().stream().findFirst();
        }
    }

    private static FakeCatalog catalogWithEverything() {
        FakeCatalog catalog = new FakeCatalog();
        catalog.presets.put("estandar", KartLoadout.of(ESTANDAR));
        catalog.presets.put("deportivo", KartLoadout.of(DEPORTIVO));
        catalog.selections.put("copa", List.of("estandar", "deportivo"));
        catalog.garages.put(ANA, new LinkedHashMap<>(Map.of("1", KartLoadout.of(PROPIO))));
        return catalog;
    }

    // --- spec races -------------------------------------------------------------------------

    @Test
    @DisplayName("a spec race puts everyone in the same kart")
    void specRaceGivesEveryoneTheSameKart() {
        KartResolver resolver = new KartResolver(
                KartProvisioning.spec("estandar"), catalogWithEverything());

        assertEquals(Optional.of(KartLoadout.of(ESTANDAR)), resolver.resolve(ANA));
        assertEquals(Optional.of(KartLoadout.of(ESTANDAR)), resolver.resolve(BEA));
    }

    @Test
    @DisplayName("a spec race offers no choice")
    void specRaceOffersNoChoice() {
        KartResolver resolver = new KartResolver(
                KartProvisioning.spec("estandar"), catalogWithEverything());

        assertTrue(resolver.optionsFor(ANA).isEmpty());
        assertFalse(resolver.choose(ANA, "deportivo"), "picking is not allowed in a spec race");
    }

    @Test
    @DisplayName("a spec race naming a preset that does not exist resolves to nothing")
    void specRaceWithMissingPresetFails() {
        KartResolver resolver = new KartResolver(
                KartProvisioning.spec("no_existe"), catalogWithEverything());

        assertTrue(resolver.resolve(ANA).isEmpty());
    }

    // --- curated selections -----------------------------------------------------------------

    @Test
    @DisplayName("a racer gets the kart they picked from the selection")
    void curatedRaceHonoursTheChoice() {
        KartResolver resolver = new KartResolver(
                KartProvisioning.curated("copa"), catalogWithEverything());

        assertTrue(resolver.choose(ANA, "deportivo"));
        assertEquals(Optional.of(KartLoadout.of(DEPORTIVO)), resolver.resolve(ANA));
    }

    @Test
    @DisplayName("a racer who never picked gets the first kart in the selection")
    void curatedRaceFallsBackToFirstOption() {
        KartResolver resolver = new KartResolver(
                KartProvisioning.curated("copa"), catalogWithEverything());

        // Not choosing must never be what keeps someone off the grid.
        assertEquals(Optional.of(KartLoadout.of(ESTANDAR)), resolver.resolve(BEA));
    }

    @Test
    @DisplayName("only karts in the selection can be picked")
    void curatedRaceRejectsKartsOutsideTheSelection() {
        FakeCatalog catalog = catalogWithEverything();
        catalog.presets.put("prototipo", KartLoadout.of(KartSpec.parse("oamp:prototype")));
        KartResolver resolver = new KartResolver(KartProvisioning.curated("copa"), catalog);

        assertFalse(resolver.choose(ANA, "prototipo"));
        assertEquals(Optional.of(KartLoadout.of(ESTANDAR)), resolver.resolve(ANA), "still the default");
    }

    @Test
    @DisplayName("choices are case-insensitive, since players type them")
    void choicesAreCaseInsensitive() {
        KartResolver resolver = new KartResolver(
                KartProvisioning.curated("copa"), catalogWithEverything());

        assertTrue(resolver.choose(ANA, "DePoRtIvO"));
    }

    @Test
    @DisplayName("an empty selection resolves to nothing rather than an arbitrary kart")
    void emptySelectionResolvesToNothing() {
        KartResolver resolver = new KartResolver(
                KartProvisioning.curated("vacia"), catalogWithEverything());

        assertTrue(resolver.resolve(ANA).isEmpty());
    }

    @Test
    @DisplayName("a selection naming a deleted preset still fields the rest")
    void selectionSurvivesADeletedPreset() {
        FakeCatalog catalog = catalogWithEverything();
        catalog.selections.put("copa", List.of("borrado", "deportivo"));
        KartResolver resolver = new KartResolver(KartProvisioning.curated("copa"), catalog);

        // The first option no longer resolves, and neither does the fallback to it, so the racer
        // gets nothing rather than a kart they did not choose — the admin has to fix the selection.
        assertTrue(resolver.resolve(ANA).isEmpty());

        assertTrue(resolver.choose(ANA, "deportivo"));
        assertEquals(Optional.of(KartLoadout.of(DEPORTIVO)), resolver.resolve(ANA));
    }

    // --- garages ----------------------------------------------------------------------------

    @Test
    @DisplayName("a garage race uses the racer's own kart")
    void garageRaceUsesOwnedKart() {
        KartResolver resolver = new KartResolver(KartProvisioning.garage(), catalogWithEverything());

        assertEquals(Optional.of(KartLoadout.of(PROPIO)), resolver.resolve(ANA));
    }

    @Test
    @DisplayName("a racer with an empty garage resolves to nothing")
    void emptyGarageResolvesToNothing() {
        KartResolver resolver = new KartResolver(KartProvisioning.garage(), catalogWithEverything());

        assertTrue(resolver.resolve(BEA).isEmpty());
    }

    @Test
    @DisplayName("a racer can pick which of their karts to bring")
    void garageRaceHonoursTheChoice() {
        FakeCatalog catalog = catalogWithEverything();
        catalog.garages.get(ANA).put("2", KartLoadout.of(DEPORTIVO));
        KartResolver resolver = new KartResolver(KartProvisioning.garage(), catalog);

        assertTrue(resolver.choose(ANA, "2"));
        assertEquals(Optional.of(KartLoadout.of(DEPORTIVO)), resolver.resolve(ANA));
    }

    @Test
    @DisplayName("a kart sold out of the garage mid-lobby falls back to another one")
    void garageChoiceThatVanishedFallsBack() {
        FakeCatalog catalog = catalogWithEverything();
        catalog.garages.get(ANA).put("2", KartLoadout.of(DEPORTIVO));
        KartResolver resolver = new KartResolver(KartProvisioning.garage(), catalog);
        resolver.choose(ANA, "2");

        catalog.garages.get(ANA).remove("2");

        assertEquals(Optional.of(KartLoadout.of(PROPIO)), resolver.resolve(ANA));
    }

    @Test
    @DisplayName("the options offered are the racer's own garage entries")
    void garageOptionsAreTheirOwn() {
        FakeCatalog catalog = catalogWithEverything();
        catalog.garages.get(ANA).put("2", KartLoadout.of(DEPORTIVO));
        KartResolver resolver = new KartResolver(KartProvisioning.garage(), catalog);

        assertEquals(List.of("1", "2"), resolver.optionsFor(ANA));
        assertTrue(resolver.optionsFor(BEA).isEmpty());
    }

    @Test
    @DisplayName("resolving as a function returns null when nothing can be given")
    void applyReturnsNullWhenUnresolvable() {
        KartResolver resolver = new KartResolver(KartProvisioning.garage(), catalogWithEverything());

        assertEquals(KartLoadout.of(PROPIO), resolver.apply(ANA));
        org.junit.jupiter.api.Assertions.assertNull(resolver.apply(BEA));
    }
}
