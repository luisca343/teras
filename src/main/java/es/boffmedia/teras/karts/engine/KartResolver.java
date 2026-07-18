package es.boffmedia.teras.karts.engine;

import es.boffmedia.teras.karts.model.KartProvisioning;
import es.boffmedia.teras.karts.store.GarageStore;
import es.boffmedia.teras.karts.store.KartPresetStore;
import es.boffmedia.teras.karts.store.KartSelectionStore;
import es.boffmedia.teras.karts.vehicle.KartLoadout;
import es.boffmedia.teras.karts.vehicle.KartSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Decides which kart each racer gets, from the race's provisioning rule and whatever the racer
 * chose.
 *
 * <p>Resolution never fails silently: if a rule cannot produce a kart — a preset pointing at an
 * uninstalled pack, an empty garage — {@link #resolve} returns empty and the racer is told at the
 * grid rather than being dropped into a race with no car.</p>
 */
public final class KartResolver implements Function<UUID, KartLoadout> {

    /**
     * The catalogs a resolver reads. Injected rather than called statically so the resolution rules
     * can be tested without a game — the stores behind the default implementation read config files
     * through {@code FMLPaths}, which only exists at runtime.
     */
    public interface Catalog {
        Optional<KartLoadout> presetLoadout(String presetName);

        List<String> selection(String selectionName);

        List<String> garageEntryIds(UUID player);

        Optional<KartLoadout> garageLoadout(UUID player, String entryId);

        Optional<KartLoadout> preferredGarageLoadout(UUID player);
    }

    /** Reads the real preset, selection and garage files. */
    public static final Catalog STORES = new Catalog() {
        @Override
        public Optional<KartLoadout> presetLoadout(String presetName) {
            KartPresetStore.Preset preset = KartPresetStore.get(presetName);
            return preset == null ? Optional.empty() : Optional.of(preset.loadout());
        }

        @Override
        public List<String> selection(String selectionName) {
            return KartSelectionStore.get(selectionName);
        }

        @Override
        public List<String> garageEntryIds(UUID player) {
            return GarageStore.listFor(player).stream().map(GarageStore.GarageEntry::id).toList();
        }

        @Override
        public Optional<KartLoadout> garageLoadout(UUID player, String entryId) {
            return GarageStore.find(player, entryId).map(GarageStore.GarageEntry::loadout);
        }

        @Override
        public Optional<KartLoadout> preferredGarageLoadout(UUID player) {
            return GarageStore.preferred(player).map(GarageStore.GarageEntry::loadout);
        }
    };

    private final KartProvisioning provisioning;
    private final Catalog catalog;
    /** What each racer picked with {@code /karts elegir}, by preset name or garage entry id. */
    private final Map<UUID, String> choices = new LinkedHashMap<>();

    public KartResolver(KartProvisioning provisioning) {
        this(provisioning, STORES);
    }

    public KartResolver(KartProvisioning provisioning, Catalog catalog) {
        this.provisioning = provisioning;
        this.catalog = catalog;
    }

    public KartProvisioning provisioning() {
        return provisioning;
    }

    /** Records a racer's pick. Returns false if it is not something they may choose. */
    public boolean choose(UUID player, String choice) {
        if (!isValidChoice(player, choice)) {
            return false;
        }
        choices.put(player, choice);
        return true;
    }

    public Optional<String> choiceOf(UUID player) {
        return Optional.ofNullable(choices.get(player));
    }

    /** What this racer may pick, for command suggestions and the "choose a kart" prompt. */
    public List<String> optionsFor(UUID player) {
        return switch (provisioning.mode()) {
            case SPEC -> List.of();
            case CURATED -> catalog.selection(provisioning.reference());
            case GARAGE -> catalog.garageEntryIds(player);
        };
    }

    private boolean isValidChoice(UUID player, String choice) {
        return optionsFor(player).stream().anyMatch(option -> option.equalsIgnoreCase(choice));
    }

    @Override
    public KartLoadout apply(UUID player) {
        return resolve(player).orElse(null);
    }

    /** The kart this racer should be given, if one can be determined. */
    public Optional<KartLoadout> resolve(UUID player) {
        return switch (provisioning.mode()) {
            case SPEC -> catalog.presetLoadout(provisioning.reference());
            case CURATED -> resolveCurated(player);
            case GARAGE -> resolveGarage(player);
        };
    }

    /** A racer who never chose gets the first option, so not picking is never a blocker. */
    private Optional<KartLoadout> resolveCurated(UUID player) {
        List<String> options = catalog.selection(provisioning.reference());
        if (options.isEmpty()) {
            return Optional.empty();
        }
        String chosen = choices.getOrDefault(player, options.get(0));
        return catalog.presetLoadout(chosen).or(() -> catalog.presetLoadout(options.get(0)));
    }

    private Optional<KartLoadout> resolveGarage(UUID player) {
        String chosen = choices.get(player);
        if (chosen != null) {
            Optional<KartLoadout> loadout = catalog.garageLoadout(player, chosen);
            if (loadout.isPresent()) {
                return loadout;
            }
        }
        return catalog.preferredGarageLoadout(player);
    }
}
