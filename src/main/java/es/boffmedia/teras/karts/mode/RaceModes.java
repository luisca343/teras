package es.boffmedia.teras.karts.mode;

import java.util.List;
import java.util.function.Supplier;

/**
 * The race modes a command can name. Each lookup builds a fresh instance, since a mode is handed to
 * exactly one race.
 */
public final class RaceModes {
    private RaceModes() {}

    private record Entry(String id, Supplier<RaceMode> factory) {}

    private static final List<Entry> MODES = List.of(
            new Entry(ClassicMode.ID, ClassicMode::new),
            new Entry(TimeTrialMode.ID, TimeTrialMode::new),
            new Entry(EliminationMode.ID, EliminationMode::new));

    public static List<String> ids() {
        return MODES.stream().map(Entry::id).toList();
    }

    /** The named mode, or a classic race when the name is unknown or absent. */
    public static RaceMode byId(String id) {
        if (id == null) {
            return new ClassicMode();
        }
        return MODES.stream()
                .filter(entry -> entry.id().equalsIgnoreCase(id))
                .findFirst()
                .map(entry -> entry.factory().get())
                .orElseGet(ClassicMode::new);
    }

    public static boolean exists(String id) {
        return MODES.stream().anyMatch(entry -> entry.id().equalsIgnoreCase(id));
    }
}
