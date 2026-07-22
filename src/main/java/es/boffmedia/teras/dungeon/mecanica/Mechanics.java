package es.boffmedia.teras.dungeon.mecanica;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.piso.FloorDef;
import es.boffmedia.teras.dungeon.piso.MechanicDef;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every mechanic the mod knows, by id.
 *
 * <p>The registry exists as much for what it <b>refuses</b> as for what it resolves. Before it, a
 * piso's {@code mecanica} was a string compared against one constant inside {@code Nests}, so a
 * misspelling was indistinguishable from "this piso has no mechanic" — the floor built, nothing ran,
 * and nothing said why. That is the same silent-content failure as §25, §33 and the unread secret
 * loot marker, and it is cheap to close: the set of valid ids is finite and known at compile time,
 * so anything else can be named out loud at load.</p>
 *
 * <p>Resolution never returns null. A piso with no mechanic, or with one that does not exist, gets
 * {@link #NONE} — the floor still runs, just without the mechanic, which is the right failure for
 * something cosmetic-to-structural rather than fatal.</p>
 */
public final class Mechanics {
    private Mechanics() {}

    /** What a piso without a mechanic runs: every hook a no-op. */
    public static final Mechanic NONE = () -> "";

    private static final Map<String, Mechanic> REGISTRY = new LinkedHashMap<>();

    static {
        register(new Nests());
    }

    private static void register(Mechanic mechanic) {
        REGISTRY.put(mechanic.id(), mechanic);
    }

    /** The ids a piso may name, for error messages and command completion. */
    public static List<String> ids() {
        return List.copyOf(REGISTRY.keySet());
    }

    public static boolean exists(String id) {
        return REGISTRY.containsKey(id);
    }

    /**
     * The mechanic this piso runs, or {@link #NONE}. Cheap enough to call per hook — a map lookup
     * on a string — so callers do not have to cache it or guard on "does this piso have one".
     */
    public static Mechanic of(FloorDef piso) {
        if (piso == null || piso.mecanica().isNone()) {
            return NONE;
        }
        Mechanic mechanic = REGISTRY.get(piso.mecanica().id());
        return mechanic == null ? NONE : mechanic;
    }

    /** The mechanic def to hand the hook, so a mechanic reads its params from the piso that ran it. */
    public static MechanicDef defOf(FloorDef piso) {
        return piso == null ? MechanicDef.NONE : piso.mecanica();
    }

    /**
     * Why this piso's {@code mecanica} cannot run, or an empty list. Called at load beside the room
     * pool checks, so a typo is a line in the log at boot rather than a mechanic that silently never
     * fires. The rule itself is {@link MechanicDef#problems}, which is pure and tested.
     */
    public static List<String> problems(FloorDef piso) {
        return piso.mecanica().problems(REGISTRY.keySet());
    }

    /** Says the above out loud. Kept here so the message lives beside the rule. */
    public static void report(FloorDef piso) {
        for (String problem : problems(piso)) {
            Teras.LOGGER.error("Dungeons: piso '{}' {}", piso.id(), problem);
        }
    }
}
