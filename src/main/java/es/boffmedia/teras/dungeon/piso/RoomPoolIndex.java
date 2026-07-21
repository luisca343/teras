package es.boffmedia.teras.dungeon.piso;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Which templates exist, read from the folder layout — the single source of truth for what a room
 * key can be.
 *
 * <h2>The layout</h2>
 *
 * <pre>
 * dungeon/&lt;set&gt;/&lt;roomKey&gt;/&lt;name&gt;.nbt
 *
 * dungeon/cuevas/normal/repisa.nbt        the piso's own
 * dungeon/cuevas/normal/pozo.nbt
 * dungeon/comun/diablo/altar.nbt          shared, drawn by every piso that inherits 'comun'
 * </pre>
 *
 * <p>A {@code set} is either a piso id or a shared set a piso names in {@code hereda}. A piso's pool
 * for a key is its own folder plus each inherited set's, in declared order.</p>
 *
 * <h2>Why discovery rather than a declaration</h2>
 *
 * <p>This replaced a {@code salas} block in the piso's json that listed the templates for each key.
 * Two places then answered "which rooms exist" — the folder and the list — and a file present in one
 * and absent from the other is invisible in play: a new room that never appears, or a deleted one
 * that keeps being asked for. §31 (the player's cell derived twice) and §33 (enemy tables held
 * twice) were both that shape, and both cost days. The folder is now the only answer; {@code pesos}
 * may adjust a variant's odds but can never add or remove one, so the two cannot contradict each
 * other — they answer different questions.</p>
 *
 * <p>Pure: it is handed a list of paths, so the whole pool model is unit-testable without a server.
 * {@code RoomPools} is the thin build-layer shim that feeds it from the structure manager.</p>
 */
public final class RoomPoolIndex {

    /** Every dungeon template lives under this prefix. */
    public static final String ROOT = "dungeon/";

    /** The namespace shipped and editor-saved templates use. */
    public static final String NAMESPACE = "teras";

    /**
     * The shared set every piso draws from unless it says otherwise. An absent {@code hereda} means
     * this one, so a piso written before shared sets existed keeps working and the common case needs
     * no field.
     */
    public static final String DEFAULT_SET = "comun";

    /** Files whose name starts with this are ignored, so {@code _wip.nbt} can sit in the folder. */
    public static final char PARKED = '_';

    public static final RoomPoolIndex EMPTY = new RoomPoolIndex(Map.of());

    /** set -> room key -> file names, both levels sorted so pool indices are stable. */
    private final Map<String, Map<String, List<String>>> bySet;

    private RoomPoolIndex(Map<String, Map<String, List<String>>> bySet) {
        this.bySet = bySet;
    }

    /**
     * Builds the index from template paths — {@code dungeon/cuevas/normal/repisa}, without namespace
     * or extension.
     *
     * <p>Anything that is not exactly four segments deep is ignored, which is precisely the old flat
     * layout ({@code dungeon/cuevas/normal}). Those files become inert rather than half-working,
     * and {@code piso migrar} is what moves them in.</p>
     */
    public static RoomPoolIndex of(Collection<String> paths) {
        Map<String, Map<String, List<String>>> bySet = new TreeMap<>();
        for (String path : paths) {
            if (path == null || !path.startsWith(ROOT)) {
                continue;
            }
            String[] parts = path.split("/");
            if (parts.length != 4) {
                continue;
            }
            String file = parts[3];
            if (file.isEmpty() || file.charAt(0) == PARKED) {
                continue;
            }
            bySet.computeIfAbsent(parts[1], s -> new TreeMap<>())
                    .computeIfAbsent(parts[2], k -> new ArrayList<>())
                    .add(file);
        }
        for (Map<String, List<String>> keys : bySet.values()) {
            for (List<String> files : keys.values()) {
                files.sort(String::compareTo);
            }
        }
        return new RoomPoolIndex(bySet);
    }

    /** Every set that has at least one template. */
    public Set<String> sets() {
        return Set.copyOf(bySet.keySet());
    }

    /**
     * Everything {@code roomKey} could be for this piso, including variants it has weighted to zero.
     * The piso's own folder first, then each inherited set in declared order; alphabetical within
     * each, so the indices {@code sala editar} and {@code sala borrar} take do not move between
     * sessions.
     */
    public List<RoomVariant> declared(FloorDef piso, String roomKey) {
        List<RoomVariant> variants = new ArrayList<>();
        collect(variants, piso, piso.id(), roomKey, false);
        for (String set : piso.hereda()) {
            // A set may not lend to itself twice, and a piso inheriting its own id would double
            // every one of its rooms.
            if (!set.equals(piso.id())) {
                collect(variants, piso, set, roomKey, true);
            }
        }
        return variants;
    }

    /** The subset that can actually be drawn. This is what generation sees. */
    public List<RoomVariant> pool(FloorDef piso, String roomKey) {
        return declared(piso, roomKey).stream().filter(RoomVariant::enabled).toList();
    }

    /** Room keys the piso owes and cannot supply — an empty folder, or every variant zeroed. */
    public List<String> emptyKeys(FloorDef piso) {
        List<String> empty = new ArrayList<>();
        for (String key : piso.requiredRooms()) {
            if (pool(piso, key).isEmpty()) {
                empty.add(key);
            }
        }
        return empty;
    }

    /**
     * What is wrong with the piso's own declarations, as lines to log. Not fatal on its own — a
     * mistyped set or a weight for a room that no longer exists should be said out loud, not take a
     * dungeon offline. A key left with nothing to draw is caught by {@link #emptyKeys} instead,
     * which is fatal, because there is no fallback between pisos.
     */
    public List<String> problems(FloorDef piso) {
        List<String> problems = new ArrayList<>();
        for (String set : piso.hereda()) {
            if (set.equals(piso.id())) {
                problems.add("hereda '" + set + "', which is the piso itself — ignored");
            } else if (!bySet.containsKey(set) && !set.equals(DEFAULT_SET)) {
                // The default set is exempt: every piso names it, and "nobody has authored a shared
                // room yet" is the normal state, not a mistake worth a line per piso per boot.
                problems.add("hereda '" + set + "', which holds no templates"
                        + (bySet.isEmpty() ? "" : " (known sets: " + String.join(", ", bySet.keySet()) + ")"));
            }
        }
        for (Map.Entry<String, Map<String, Double>> entry : piso.pesos().entrySet()) {
            Set<String> present = new LinkedHashSet<>();
            for (RoomVariant variant : declared(piso, entry.getKey())) {
                present.add(variant.name());
            }
            for (String name : entry.getValue().keySet()) {
                if (!present.contains(name)) {
                    problems.add("pesos names '" + entry.getKey() + " / " + name
                            + "', which is not on disk — a peso can never add a room, only weight"
                            + " one, so this line does nothing");
                }
            }
        }
        return problems;
    }

    private void collect(List<RoomVariant> into, FloorDef piso, String set, String roomKey,
                         boolean qualify) {
        Map<String, List<String>> keys = bySet.get(set);
        if (keys == null) {
            return;
        }
        List<String> files = keys.get(roomKey);
        if (files == null) {
            return;
        }
        for (String file : files) {
            String name = qualify ? set + "/" + file : file;
            into.add(new RoomVariant(name,
                    NAMESPACE + ":" + ROOT + set + "/" + roomKey + "/" + file,
                    piso.peso(roomKey, name)));
        }
    }
}
