package es.boffmedia.teras.dungeon.run;

/**
 * Cues the floor loop asks for, named by what happened rather than by which sound plays — the
 * dungeon counterpart of the karts {@code RaceSound}. Keeping the enum here means {@link RunCore}
 * stays free of Minecraft: which {@code SoundEvent} each one maps to, and where it is played from,
 * is {@link RunEngine}'s business.
 */
public enum DungeonSound {
    /** Doors just shut on an ordinary encounter. */
    ROOM_SEALED,
    /** Doors just shut on a boss or mini-boss. */
    BOSS_SEALED,
    /** The room cleared and its doors reopened. */
    ROOM_OPENED,
    /** The floor's boss went down. */
    BOSS_DEFEATED,
    /** The way to the next floor opened. */
    TRAPDOOR_OPEN,
    /** A secret wall gave way. */
    SECRET_OPENED,
    /** An enemy crossed an ability threshold and changed state. */
    ENEMY_ENRAGED
}
