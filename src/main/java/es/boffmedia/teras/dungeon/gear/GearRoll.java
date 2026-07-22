package es.boffmedia.teras.dungeon.gear;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Which piece of gear a loot table hands over.
 *
 * <h2>What this replaces</h2>
 *
 * <p>Loot tables named pieces outright: an entry for {@code teras:gear_espada} carrying a
 * {@code teras:gear_id} of {@code espada_abisal}, one per piece, per table. That made the tables a
 * <b>second catalog, mirrored by hand</b>, with three consequences that were all invisible:</p>
 *
 * <ul>
 *   <li>a piece added to {@code gear.json} could never drop — it existed, could be given by command,
 *       and no table knew about it. {@code escudo_hyliano} was the live proof;</li>
 *   <li>the two halves could disagree — an item and a {@code gear_id} whose kinds did not match,
 *       which migration silently repaired on pickup so the game looked right and the table was
 *       wrong ({@code GearLootTablesTest} caught exactly that);</li>
 *   <li>{@link GearDef.Rarity} meant nothing. Its own javadoc said so: "tooltip colour only; drop
 *       chance is the loot table's business". Rarity was a word on a tooltip while the actual odds
 *       lived in hand-tuned weights somewhere else entirely.</li>
 * </ul>
 *
 * <p>Now a table says <i>how rare</i> and the catalog says <i>which</i>. Adding a piece to the
 * config makes it droppable at its own rarity, from every table that rolls that rarity, with no
 * datapack edit at all.</p>
 *
 * <p>Pure — no Minecraft — so the odds and the draw are testable without a server. The caller
 * supplies the randomness as plain doubles.</p>
 */
public final class GearRoll {
    private GearRoll() {}

    /**
     * Relative odds of each rarity for one draw. Weights, not percentages, so a table can be tuned
     * without every number having to add up to a hundred.
     */
    public record Odds(int comun, int raro, int epico) {

        public Odds {
            if (comun < 0 || raro < 0 || epico < 0) {
                throw new IllegalArgumentException("Gear rarity weights cannot be negative");
            }
        }

        public int total() {
            return comun + raro + epico;
        }

        public int weightOf(GearDef.Rarity rarity) {
            return switch (rarity) {
                case COMUN -> comun;
                case RARO -> raro;
                case EPICO -> epico;
            };
        }
    }

    /**
     * The rarity for one draw, or null when every weight is zero.
     *
     * @param roll uniform in [0, 1)
     */
    public static GearDef.Rarity rollRarity(Odds odds, double roll) {
        int total = odds.total();
        if (total <= 0) {
            return null;
        }
        double target = Math.max(0, Math.min(0.9999999, roll)) * total;
        double seen = 0;
        for (GearDef.Rarity rarity : GearDef.Rarity.values()) {
            seen += odds.weightOf(rarity);
            if (target < seen) {
                return rarity;
            }
        }
        return GearDef.Rarity.EPICO;
    }

    /**
     * The catalog's ids grouped by rarity, each list in a stable order.
     *
     * <p>Sorted rather than left in map order so the same seed draws the same piece across restarts.
     * The catalog is a {@code LinkedHashMap} whose order depends on what a server's {@code gear.json}
     * added and when, which is not something a seeded run should be sensitive to.</p>
     */
    public static Map<GearDef.Rarity, List<String>> byRarity(Map<String, GearDef> catalog) {
        Map<GearDef.Rarity, List<String>> out = new EnumMap<>(GearDef.Rarity.class);
        for (GearDef.Rarity rarity : GearDef.Rarity.values()) {
            out.put(rarity, new ArrayList<>());
        }
        for (Map.Entry<String, GearDef> entry : catalog.entrySet()) {
            out.get(entry.getValue().rarity()).add(entry.getKey());
        }
        out.values().forEach(java.util.Collections::sort);
        return out;
    }

    /**
     * Draws one piece.
     *
     * <p>A rarity the catalog cannot fill <b>falls back</b> to the nearest one that it can, rather
     * than returning nothing. An empty drop is indistinguishable from a table that meant to give
     * nothing, so a server whose {@code gear.json} happens to define no epic piece would get silent
     * blanks from every boss instead of an error it could act on. {@code GearRollTest} pins that
     * the shipped catalog fills all three, so the fallback stays a safety net rather than the
     * normal path.</p>
     *
     * @param rarityRoll uniform in [0, 1), picks the rarity
     * @param pieceRoll  uniform in [0, 1), picks within it
     * @return the catalog id, or null only when the catalog holds no gear at all
     */
    public static String roll(Odds odds, Map<GearDef.Rarity, List<String>> byRarity,
                              double rarityRoll, double pieceRoll) {
        GearDef.Rarity rarity = rollRarity(odds, rarityRoll);
        if (rarity == null) {
            return null;
        }
        List<String> ids = byRarity.getOrDefault(rarity, List.of());
        if (ids.isEmpty()) {
            ids = fallback(byRarity, rarity);
        }
        if (ids.isEmpty()) {
            return null;
        }
        int index = (int) (Math.max(0, Math.min(0.9999999, pieceRoll)) * ids.size());
        return ids.get(Math.min(index, ids.size() - 1));
    }

    /** The nearest populated rarity, searching outward: epic falls to rare before common. */
    private static List<String> fallback(Map<GearDef.Rarity, List<String>> byRarity,
                                         GearDef.Rarity wanted) {
        GearDef.Rarity[] all = GearDef.Rarity.values();
        for (int distance = 1; distance < all.length; distance++) {
            for (int direction = -1; direction <= 1; direction += 2) {
                int index = wanted.ordinal() + distance * direction;
                if (index >= 0 && index < all.length) {
                    List<String> ids = byRarity.getOrDefault(all[index], List.of());
                    if (!ids.isEmpty()) {
                        return ids;
                    }
                }
            }
        }
        return List.of();
    }
}
