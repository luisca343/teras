package es.boffmedia.teras.dungeon.model;

/**
 * Room roles, straight from the legacy generator minus {@code WALL}: an empty grid cell is now
 * simply unoccupied instead of holding a fake 1×1 wall room.
 *
 * <p><b>Append only.</b> These ordinals go over the wire in {@code DungeonMapPayload} and index
 * the client's glyph table, so inserting a value silently relabels every room on every minimap.</p>
 */
public enum RoomType {
    NORMAL,
    START,
    BOSS,
    MINI_BOSS,
    SHOP,
    TREASURE,
    SECRET,
    SUPER_SECRET,
    CHALLENGE,
    CURSE,
    /** Spikes you step on: pay health, improve the odds, take the payout or keep bleeding. */
    SACRIFICE,
    /** A machine that eats coins and occasionally gives more back. */
    ARCADE,
    /** Barred until the floor's boss falls; sells one strong item for coins or for hearts. */
    DEVIL_DEAL;

    /** Secret rooms are entered through a cracked wall, never a doorway. */
    public boolean isSecret() {
        return this == SECRET || this == SUPER_SECRET;
    }

    /** Everything that claims a 1×1 dead end during placement (i.e. not NORMAL/START). */
    public boolean isSpecial() {
        return this != NORMAL && this != START;
    }
}
