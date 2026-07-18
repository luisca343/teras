package es.boffmedia.teras.storage.model;

/**
 * One occupied PC slot, as {@code POST /pc} returns it. Empty slots are not sent — the web PC treats
 * every unmentioned slot as free, and a full PC would otherwise be 900 mostly-null entries.
 */
public record PcEntry(int box, int index, StoredMon pokemon) {
}
