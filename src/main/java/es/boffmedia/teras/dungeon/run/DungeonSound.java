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
    ENEMY_ENRAGED,
    /** Coins went into the party purse. */
    COIN_PICKUP,
    /** A pedestal was bought. */
    PURCHASE,
    /** A purchase the party could not afford. */
    PURCHASE_DENIED,
    /** The challenge plate was stepped on and the doors shut. */
    CHALLENGE_STARTED,
    /** One wave of a challenge fell, with more to come. */
    WAVE_CLEARED,
    /** The sacrifice plate took its bite. */
    SACRIFICE,
    /** The sacrifice paid out. */
    SACRIFICE_REWARD,
    /** Coins went into the arcade machine. */
    ARCADE_PLAY,
    /** The arcade paid out. */
    ARCADE_WIN,
    /** The arcade machine gave up. */
    ARCADE_BREAK,
    /** The boss fell and the barred devil door opened. */
    DEVIL_OPENED,
    /** A devil deal was struck. */
    DEVIL_DEAL,
    /** A phoenix charm burned to cancel a death. */
    PHOENIX,
    /** The boss fell and the floor's seal re-pinned: runes lit, pit bars retracted. */
    SEAL_RESTORED,
    /** One second less on the straggler countdown's last stretch. */
    DESCENT_TICK
}
