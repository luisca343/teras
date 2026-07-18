package es.boffmedia.teras.storage.api;

import es.boffmedia.teras.storage.model.PcEntry;
import es.boffmedia.teras.storage.model.StoredMon;

import java.util.List;

/**
 * One player's already-loaded party and PC, from {@link StorageProvider#open}.
 *
 * <p><b>Server thread only</b> — this is game state. The loading that could not happen there is
 * already done.</p>
 */
public interface StorageSession {

    /**
     * Box {@code -1} is the party — Pixelmon's own {@code PartyStorage.PARTY_BOX_INDEX}, which the
     * backend DTO and the web PC both speak.
     */
    int PARTY_BOX = -1;

    /** Every occupied PC slot, in box then index order. See {@link PcEntry} for why empties are cut. */
    List<PcEntry> readPc();

    /** The party: always six entries, {@code null} for an empty slot, as 1.16.5's /equipo returned. */
    List<StoredMon> readParty();

    /**
     * Swaps two slots, returning whether it happened.
     *
     * <p>A <b>swap</b>, not a move: it is the only write the PC has ever exposed and the web client
     * builds bulk operations out of sequences of it. An empty destination is a swap with null and
     * must succeed; an out-of-range slot must fail rather than clamp.</p>
     */
    boolean swap(int sourceBox, int sourceIndex, int destBox, int destIndex);
}
