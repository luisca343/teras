package es.boffmedia.teras.dungeon.run;

/**
 * A room's place in the Isaac loop. Rooms move forward through these, with one sanctioned
 * exception: a fight every player inside died out of or vanished from steps back from IN_COMBAT
 * to DISCOVERED ({@code RunCore.abandonCombat}) — a deserted room resets rather than resolves.
 */
public enum RoomState {
    UNDISCOVERED,
    DISCOVERED,
    IN_COMBAT,
    CLEARED
}
