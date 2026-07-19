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
    HIDDEN,
    /**
     * Barred doorway into a DEVIL_DEAL room. Unlike a sealed combat door this one is placed shut
     * and stays that way until the floor's boss falls, which is what makes the room a promise on
     * the minimap rather than a surprise.
     */
    DEVIL;

    public static DoorKind between(RoomType a, RoomType b) {
        if (a == RoomType.SUPER_SECRET || b == RoomType.SUPER_SECRET) {
            return HIDDEN;
        }
        if (a == RoomType.SECRET || b == RoomType.SECRET) {
            return SECRET_CRACK;
        }
        // Before the boss clause: a devil room next to the boss is still barred, not a boss door.
        if (a == RoomType.DEVIL_DEAL || b == RoomType.DEVIL_DEAL) {
            return DEVIL;
        }
        if (a == RoomType.BOSS || b == RoomType.BOSS
                || a == RoomType.MINI_BOSS || b == RoomType.MINI_BOSS) {
            return BOSS;
        }
        return OPEN;
    }
}
