package es.boffmedia.teras.dungeon.gen;

/**
 * The odds that each of the seal chamber's satellites is appended to this floor, 0–100, already
 * answered by {@link SatelliteOdds} against the run's ledger and the floor just played.
 *
 * <p>Chances rather than booleans on purpose: the generator rolls them with its own seeded rng, so
 * a floor stays a deterministic function of (seed, chances) — the roll cannot drift with wall time
 * or call order. What run state changes is the <i>chance</i>, never the mechanism.</p>
 *
 * <p>This is also why a satellite is not part of {@link GenConfig}: config is what a piso and the
 * server decided, and this is what a party earned.</p>
 */
public record SatelliteChances(int acreedor, int orden) {

    /** No satellites: the first floor of a run before any state exists, and every plain call. */
    public static final SatelliteChances NONE = new SatelliteChances(0, 0);

    public SatelliteChances {
        acreedor = clamp(acreedor);
        orden = clamp(orden);
    }

    private static int clamp(int chance) {
        return Math.max(0, Math.min(100, chance));
    }

    public boolean any() {
        return acreedor > 0 || orden > 0;
    }
}
