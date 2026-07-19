package es.boffmedia.teras.dungeon.model;

/**
 * What sits on the shared wall between two adjacent rooms. The legacy paster carved bare 3×3 air
 * for every pair — and carved nothing at all for secret rooms, which made them unreachable; the
 * cracked wall is the bomb-replacement mechanic that fixes that (see DUNGEONS.md §7).
 */
public enum DoorKind {
    /** Ordinary doorway; sealable while the room is in combat. */
    OPEN,
    /** Doorway into a boss or mini-boss room. */
    BOSS,
    /** Breakable cracked wall into a SECRET room — visibly hinted. */
    SECRET_CRACK,
    /** Unmarked breakable wall into a SUPER_SECRET room — no hint anywhere. */
    HIDDEN;

    public static DoorKind between(RoomType a, RoomType b) {
        if (a == RoomType.SUPER_SECRET || b == RoomType.SUPER_SECRET) {
            return HIDDEN;
        }
        if (a == RoomType.SECRET || b == RoomType.SECRET) {
            return SECRET_CRACK;
        }
        if (a == RoomType.BOSS || b == RoomType.BOSS
                || a == RoomType.MINI_BOSS || b == RoomType.MINI_BOSS) {
            return BOSS;
        }
        return OPEN;
    }
}
