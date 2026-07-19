package es.boffmedia.teras.world;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import net.minecraft.core.BlockPos;

/**
 * The coordinate map of the shared void dimension ({@code teras:vacio}). Several systems build
 * temporary structures there; nothing about a flat void keeps them apart, so this class does —
 * <b>every system that places blocks in the void claims its zone here</b>, and computes its
 * origins through a method that refuses to hand out a position outside that zone. A new system
 * wanting void space adds a zone to this map first; building at hand-picked coordinates is how
 * two features end up erasing each other's blocks.
 *
 * <p>The map, with {@code S = separacionSlots} (default 4096) and north = −z:</p>
 *
 * <pre>
 *                 │ z &lt; −S          FREE — unclaimed, future systems start here
 *                 ├─────────────────────────────────────────────────────────────
 *   x &lt; 0        │ −S ≤ z &lt; 0      ROOM EDITOR — pads eastward at (i·S, slotY, −S)
 *   FREE          ├─────────────────────────────────────────────────────────────
 *                 │ z ≥ 0           DUNGEON RUNS — slot lattice, 8 columns eastward,
 *                 │                 rows southward; each slot holds two build pads
 * </pre>
 *
 * <p>Both dungeon zones scale with the config: larger {@code separacionSlots} spreads everything
 * out but never moves a zone across another's boundary, because the boundaries are defined in
 * units of {@code S} too. Vertical space is shared — everything builds at {@code slotY}.</p>
 */
public final class VoidZones {
    private VoidZones() {}

    /**
     * DUNGEON RUNS zone (x ≥ 0, z ≥ 0): one origin per (slot, pad). A slot holds two build pads a
     * half-step apart so a stage advance can build the next floor before tearing down the current
     * one; the lattice is 8 columns wide and grows south a row per 8 slots.
     */
    public static BlockPos dungeonRunPad(int slot, int pad) {
        int spacing = DungeonsConfig.slotSpacing();
        BlockPos origin = new BlockPos((slot % 8) * spacing + pad * (spacing / 2),
                DungeonsConfig.slotY(), (slot / 8) * spacing);
        if (origin.getX() < 0 || origin.getZ() < 0) {
            throw new IllegalStateException("Run pad outside the DUNGEON RUNS zone: " + origin);
        }
        return origin;
    }

    /**
     * ROOM EDITOR zone (x ≥ 0, −S ≤ z < 0): one pad per concurrent editing session, eastward along
     * the strip just north of the run lattice. A room is at most two cells (≈42 blocks) deep, so a
     * pad can never reach across the strip into the run quadrant.
     */
    public static BlockPos roomEditorPad(int index) {
        int spacing = DungeonsConfig.slotSpacing();
        BlockPos origin = new BlockPos(index * spacing, DungeonsConfig.slotY(), -spacing);
        if (origin.getX() < 0 || origin.getZ() >= 0 || origin.getZ() < -spacing) {
            throw new IllegalStateException("Editor pad outside the ROOM EDITOR zone: " + origin);
        }
        return origin;
    }

    /** One startup log line so the reservation is visible in every server's log, not just here. */
    public static void logZoneMap() {
        int spacing = DungeonsConfig.slotSpacing();
        Teras.LOGGER.info("Void dimension ({}) zone map: DUNGEON RUNS x>=0,z>=0 (lattice of {}); "
                + "ROOM EDITOR x>=0,{}<=z<0; everything else free",
                DungeonsConfig.dimension(), spacing, -spacing);
    }
}
