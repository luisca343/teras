package es.boffmedia.teras.dungeon.piso;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a piso's {@code decoracion} markers turn into, keyed by the surface they sit on.
 *
 * <p>Typed by surface rather than one pooled table, because untyped is wrong in a way that only
 * shows up in the world: a single table can drop a mushroom on a ceiling and hang dripstone off a
 * floor. The marker carries its surface ({@code decoracion:techo}), the table answers it, and the
 * two can never disagree.</p>
 *
 * <p>This is also the cheapest variety a piso can buy. One authored room varies between runs
 * without a second template — the layout is the same, what grows in it is not.</p>
 */
public record DecorTables(Map<String, List<DecorRef>> bySurface) {

    /** Ceiling. */
    public static final String TECHO = "techo";
    /** Floor. */
    public static final String SUELO = "suelo";
    /** Walls. */
    public static final String PARED = "pared";

    /**
     * One entry: a block state id, or a structure to place when a feature is wanted rather than a
     * block. Exactly one of the two is set; a ref with both names a block and logs.
     *
     * @param bloque    block id, e.g. {@code minecraft:pointed_dripstone}
     * @param estructura structure id to place instead, for a decoration that is more than one block
     * @param peso      relative weight, at least 1
     */
    public record DecorRef(String bloque, String estructura, int peso) {
        public DecorRef {
            peso = Math.max(1, peso);
        }

        public static DecorRef block(String bloque, int peso) {
            return new DecorRef(bloque, null, peso);
        }

        public boolean isStructure() {
            return estructura != null && !estructura.isBlank();
        }
    }

    public static final DecorTables EMPTY = new DecorTables(Map.of());

    public DecorTables {
        Map<String, List<DecorRef>> copy = new LinkedHashMap<>();
        if (bySurface != null) {
            for (Map.Entry<String, List<DecorRef>> entry : bySurface.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    copy.put(entry.getKey(), List.copyOf(entry.getValue()));
                }
            }
        }
        bySurface = Map.copyOf(copy);
    }

    /** The table for a surface, empty when the piso declared none — the marker is then aired out. */
    public List<DecorRef> forSurface(String surface) {
        return bySurface.getOrDefault(surface, List.of());
    }

    public boolean isEmpty() {
        return bySurface.isEmpty();
    }

    /**
     * Surfaces named here that are not one of the three the marker vocabulary defines. A typo like
     * {@code suelos} would otherwise be a table that silently never fires, and a decoration that
     * never appears looks exactly like a decoration nobody authored.
     */
    public List<String> problems(String pisoId) {
        List<String> problems = new ArrayList<>();
        for (String surface : bySurface.keySet()) {
            if (!TECHO.equals(surface) && !SUELO.equals(surface) && !PARED.equals(surface)) {
                problems.add("piso '" + pisoId + "' has a decoracion table for '" + surface
                        + "', which is not techo, suelo or pared — it would never be used");
            }
        }
        return problems;
    }
}
