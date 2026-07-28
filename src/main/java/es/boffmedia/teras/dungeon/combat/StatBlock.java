package es.boffmedia.teras.dungeon.combat;

import java.util.EnumMap;
import java.util.Map;

/**
 * One combatant's sheet: a {@link Stat}'s base, everything added to it, and everything scaling it.
 *
 * <h2>Why it is recomputed rather than mutated in place</h2>
 *
 * <p>A run's power comes from three sources that all change at different moments — equipo is swapped,
 * reliquias are absorbed, bendiciones are granted — and the only way to keep "what is my damage" from
 * drifting is to make it a <i>function</i> of those sources rather than a number somebody remembered
 * to update. So a block is cheap to build, {@link #reset} exists, and the intended shape is to throw
 * the old one away and re-add every source. Nothing here is a running total.</p>
 *
 * <h2>Multipliers stack additively, on purpose</h2>
 *
 * <p>Two reliquias saying "+50%" produce ×2.0, not ×2.25. Multiplicative stacking is the standard
 * way a late roguelike run stops being readable: the twentieth pickup is worth more than the first
 * ten together, and no player can tell which of their things is doing the work. Additive-into-a-
 * multiplier keeps the tenth copy worth exactly what the first was, which also makes
 * {@link Stat#max} a ceiling a build can approach instead of one it vaults over.</p>
 *
 * <p>Pure — no Minecraft.</p>
 */
public final class StatBlock {

    private final Map<Stat, Double> base = new EnumMap<>(Stat.class);
    private final Map<Stat, Double> flat = new EnumMap<>(Stat.class);
    /** Stored as the bonus fraction, so 0 means ×1 and no entry has to be pre-seeded. */
    private final Map<Stat, Double> scale = new EnumMap<>(Stat.class);

    /** A sheet with every stat at its catalogue default. */
    public StatBlock() {
        reset();
    }

    /** Back to catalogue defaults, dropping every modifier. */
    public void reset() {
        base.clear();
        flat.clear();
        scale.clear();
        for (Stat stat : Stat.values()) {
            base.put(stat, stat.base());
        }
    }

    /**
     * Replaces the starting value — what a weapon or an enemy's own definition sets, as opposed to
     * what modifies it afterwards.
     */
    public StatBlock setBase(Stat stat, double value) {
        base.put(stat, value);
        return this;
    }

    /** Adds to the total before scaling. */
    public StatBlock addFlat(Stat stat, double amount) {
        flat.merge(stat, amount, Double::sum);
        return this;
    }

    /** Adds a bonus fraction: {@code 0.5} is "+50%", and two of them make ×2.0. */
    public StatBlock addScale(Stat stat, double fraction) {
        scale.merge(stat, fraction, Double::sum);
        return this;
    }

    /** The resolved value, clamped to the stat's bounds. */
    public double get(Stat stat) {
        double raw = (base.getOrDefault(stat, stat.base()) + flat.getOrDefault(stat, 0.0))
                * (1 + scale.getOrDefault(stat, 0.0));
        return stat.clamp(raw);
    }

    /**
     * The resolved value as a count.
     *
     * <p>Rounding lives in {@link Stat#clamp} rather than here so that a stat declared integral reads
     * the same through {@link #get} — a caller that asks for contenedores as a double must not see
     * 5.5 while the HUD shows 6.</p>
     */
    public int getInt(Stat stat) {
        return (int) Math.rint(get(stat));
    }

    /**
     * An independent sheet with the same sources — what a "what if I took this" comparison needs, and
     * pinned by test so the two can never share a modifier map.
     */
    public StatBlock copy() {
        StatBlock other = new StatBlock();
        other.base.putAll(base);
        other.flat.putAll(flat);
        other.scale.putAll(scale);
        return other;
    }
}
