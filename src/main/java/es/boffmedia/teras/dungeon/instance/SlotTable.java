package es.boffmedia.teras.dungeon.instance;

import java.util.BitSet;

/**
 * Which instance slots of the void lattice are taken.
 *
 * <p>Its own class so the allocation rule — lowest free index, refused past the configured cap — can
 * be exercised without a server. It is deliberately not aware of the cap itself: the cap is config,
 * read fresh at every allocation, and a table that cached it would hand out a slot the operator had
 * already taken away.</p>
 */
final class SlotTable {

    private final BitSet taken = new BitSet();

    /** The lowest free slot below {@code max}, or -1 when there is none; nothing is claimed. */
    int nextFree(int max) {
        int slot = taken.nextClearBit(0);
        return slot >= max ? -1 : slot;
    }

    /** The lowest free slot below {@code max}, claimed, or -1 when there is none. */
    int allocate(int max) {
        int slot = nextFree(max);
        if (slot >= 0) {
            taken.set(slot);
        }
        return slot;
    }

    /** Gives a slot back; it is the next one handed out if nothing below it is free. */
    void free(int slot) {
        taken.clear(slot);
    }

    boolean isTaken(int slot) {
        return taken.get(slot);
    }

    int used() {
        return taken.cardinality();
    }

    void clear() {
        taken.clear();
    }
}
