package es.boffmedia.teras.region;

import es.boffmedia.teras.plot.PlotStore;
import es.boffmedia.teras.region.model.RegionFlag;
import es.boffmedia.teras.region.model.TerasRegion;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Read-side lookups over {@link RegionStore}: regions grouped per dimension, containment queries,
 * and flag checks. The grouping is rebuilt lazily whenever the store's generation moves, so the
 * per-query cost is a linear scan of the player's dimension only. Snapshot-based and safe from any
 * thread (a racing rebuild just does the same work twice).
 */
public final class RegionIndex {
    private RegionIndex() {}

    private record Snapshot(long generation, Map<String, List<TerasRegion>> byDimension) {}

    private static volatile Snapshot snapshot = new Snapshot(-1, Map.of());

    /**
     * Regions in the given dimension (key like {@code minecraft:overworld}), highest priority
     * first as {@link RegionResolver#ordered} requires; may be empty.
     */
    public static List<TerasRegion> inDimension(String dimension) {
        return current().byDimension.getOrDefault(dimension, List.of());
    }

    /** Names of every region containing the position, in store order. */
    public static Set<String> namesAt(String dimension, double x, double y, double z) {
        Set<String> names = new LinkedHashSet<>();
        for (TerasRegion region : inDimension(dimension)) {
            if (region.contains(x, y, z)) names.add(region.getName());
        }
        return names;
    }

    /**
     * Whether {@code flag} is denied at the position by an explicit flag; see
     * {@link RegionResolver#denies}. Plot ownership is not consulted — use
     * {@link #decide} for anything a player did.
     */
    public static boolean denies(String dimension, double x, double y, double z, RegionFlag flag) {
        return RegionResolver.denies(inDimension(dimension), x, y, z, flag);
    }

    /**
     * The full verdict for {@code player} acting at the position, plots included; see
     * {@link RegionResolver#decide}.
     */
    public static RegionResolver.Decision decide(String dimension, double x, double y, double z,
                                                 RegionFlag flag, UUID player) {
        return RegionResolver.decide(inDimension(dimension), x, y, z, flag, player,
                PlotStore::get, System.currentTimeMillis());
    }

    private static Snapshot current() {
        Snapshot cached = snapshot;
        long generation = RegionStore.generation();
        if (cached.generation == generation) return cached;

        Map<String, List<TerasRegion>> byDimension = new HashMap<>();
        for (TerasRegion region : RegionStore.all().values()) {
            byDimension.computeIfAbsent(region.getDimension(), k -> new java.util.ArrayList<>()).add(region);
        }
        byDimension.replaceAll((dim, list) -> RegionResolver.ordered(list));
        Snapshot rebuilt = new Snapshot(generation, Map.copyOf(byDimension));
        snapshot = rebuilt;
        return rebuilt;
    }
}
