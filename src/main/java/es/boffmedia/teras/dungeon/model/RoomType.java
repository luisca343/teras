package es.boffmedia.teras.dungeon.model;

/**
 * Room roles, straight from the legacy generator minus {@code WALL}: an empty grid cell is now
 * simply unoccupied instead of holding a fake 1×1 wall room.
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
    CURSE;

    /** Secret rooms are entered through a cracked wall, never a doorway. */
    public boolean isSecret() {
        return this == SECRET || this == SUPER_SECRET;
    }

    /** Everything that claims a 1×1 dead end during placement (i.e. not NORMAL/START). */
    public boolean isSpecial() {
        return this != NORMAL && this != START;
    }
}
