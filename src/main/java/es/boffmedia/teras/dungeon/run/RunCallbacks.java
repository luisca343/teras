package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.dungeon.model.Room;

import java.util.UUID;

/**
 * Everything {@link RunCore} may do to the world — the dungeon counterpart of the karts
 * {@code RaceCallbacks}: the core stays pure and unit-testable, the engine implements this
 * against the built floor.
 */
public interface RunCallbacks {

    /**
     * First time any party member sets foot in the room. {@code discoverer} is null only for the
     * start room, which is discovered at floor creation — whoever walks into a CURSE room pays
     * its toll, so the identity matters.
     */
    void roomDiscovered(Room room, UUID discoverer);

    /** Fill the room's doorways: combat has started inside. */
    void sealRoom(Room room);

    /** Reopen the room's doorways. */
    void openRoom(Room room);

    /**
     * Play a cue for the party. Separate from {@link #sealRoom} / {@link #openRoom} so the core
     * says what happened and the engine decides what it sounds like and where it comes from — the
     * doors are heard at the doorway you walked through, not from the middle of the room.
     */
    void sound(DungeonSound sound, Room room);

    /**
     * Spawn the room's wave (or its boss) and return how many enemies now stand. Returning 0
     * means nothing could spawn — the core treats the room as instantly cleared rather than
     * leaving it sealed forever.
     */
    int spawnEncounter(Room room);

    /**
     * Spawn wave {@code wave} (0-based) of a challenge room and return how many enemies now stand.
     * Separate from {@link #spawnEncounter} because a challenge escalates: the engine sizes each
     * wave from its index, and the core only counts what it is told.
     */
    int spawnChallengeWave(Room room, int wave);

    /** How many waves this room's challenge runs. At least 1; the core clamps. */
    int challengeWaves(Room room);

    /** The last wave of a challenge fell — pay the bonus for surviving it. */
    void challengeCompleted(Room room);

    /** All enemies down: rewards, drops. */
    void roomCleared(Room room);

    /** The boss fell; the way down is open. */
    void openTrapdoor(Room bossRoom);

    /** Discovered-map state changed; resync clients (stage 5 wires the payload). */
    void syncMap();
}
