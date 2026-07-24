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
    DEVIL,
    /**
     * Walkable doorway into a CURSE room, framed in spikes. Open — you may always go through — but
     * unmistakable from a room away, because crossing it costs blood and a price you cannot see
     * before you pay it is an ambush rather than a decision.
     */
    CURSE,
    /**
     * The doorway into the EXIT room. Unlike DEVIL nothing is placed at build time — the wall
     * stays solid, the chamber invisible — and the boss falling is what carves it open: the seal
     * re-pins and the rock gives way. The sala del sello is generated with the floor but only
     * ever <i>revealed</i>, never promised.
     */
    SELLO,
    /**
     * Barred doorway into the ORDEN room, the Acreedor's counter-pole. Placed shut like
     * {@link #DEVIL} and carved open on the boss's death — but only when the party earned her
     * (PISOS §63c). Its own kind rather than a reused DEVIL so the two satellite doors can read
     * differently from inside the sala del sello, and so the reveal can tell them apart.
     */
    GRACIA;

    /**
     * Whether this is a doorway a player can walk through, and therefore one that has to be sealed
     * during combat, cleared of bodies before it seals, and used as a source for door cues.
     *
     * <p>Four places used to spell this out as {@code == OPEN || == BOSS}. Adding CURSE to three of
     * them and missing the fourth would have left a sealed fight with one open exit — so the rule
     * lives here, and the kinds are the only thing that decides it.</p>
     */
    public boolean walkable() {
        return this == OPEN || this == BOSS || this == CURSE;
    }

    public static DoorKind between(RoomType a, RoomType b) {
        if (a == RoomType.SUPER_SECRET || b == RoomType.SUPER_SECRET) {
            return HIDDEN;
        }
        if (a == RoomType.SECRET || b == RoomType.SECRET) {
            return SECRET_CRACK;
        }
        // Safety net only: the exit room's one edge is appended by hand in PostRooms, after the
        // door graph is derived. If a graph is ever rebuilt over a grid that already holds an
        // exit, a sealed door is the failure mode that stays safe.
        if (a == RoomType.EXIT || b == RoomType.EXIT) {
            return SELLO;
        }
        // Same safety net as EXIT above: the Orden's one edge is appended by hand in PostRooms.
        if (a == RoomType.ORDEN || b == RoomType.ORDEN) {
            return GRACIA;
        }
        // Before the boss clause: a devil room next to the boss is still barred, not a boss door.
        if (a == RoomType.DEVIL_DEAL || b == RoomType.DEVIL_DEAL) {
            return DEVIL;
        }
        // Also before it: the toll is the thing a player must be able to see coming, so a curse
        // room next to the boss keeps its spikes rather than borrowing the boss frame.
        if (a == RoomType.CURSE || b == RoomType.CURSE) {
            return CURSE;
        }
        if (a == RoomType.BOSS || b == RoomType.BOSS
                || a == RoomType.MINI_BOSS || b == RoomType.MINI_BOSS) {
            return BOSS;
        }
        return OPEN;
    }
}
