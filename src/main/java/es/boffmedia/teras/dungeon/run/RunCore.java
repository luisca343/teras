package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.dungeon.model.DungeonLayout;
import es.boffmedia.teras.dungeon.model.GridPos;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.RoomType;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One floor's Isaac loop, pure logic — the dungeon counterpart of the karts {@code RaceCore}.
 * Driven by cell entries and enemy removals; reaches the world only through
 * {@link RunCallbacks}. The legacy system had no equivalent: it stopped at pasting blocks.
 *
 * <p>Rooms with an encounter (NORMAL, MINI_BOSS, BOSS, CHALLENGE) seal and spawn on first
 * entry; every other type only gets discovered. Rooms are independent — two players can hold
 * two rooms in combat at once. Enemy counting lives here as a ledger per room; who maps entity
 * UUIDs to rooms is the engine's business.</p>
 */
public final class RunCore {

    private final DungeonLayout layout;
    private final RunCallbacks callbacks;
    private final Map<Room, RoomState> states = new HashMap<>();
    private final Map<Room, Integer> ledger = new HashMap<>();
    private final Set<Room> discovered = new LinkedHashSet<>();
    /** Waves still owed by a challenge room after the one currently standing. */
    private final Map<Room, Integer> wavesLeft = new HashMap<>();
    private boolean trapdoorOpen;

    public RunCore(DungeonLayout layout, RunCallbacks callbacks) {
        this.layout = layout;
        this.callbacks = callbacks;
        for (Room room : layout.rooms()) {
            states.put(room, RoomState.UNDISCOVERED);
        }
    }

    /**
     * Discovers the start room and syncs the map. Separate from the constructor <b>on purpose</b>:
     * the engine's callbacks hold a reference back to the object that owns this core, so firing a
     * callback while that object is still being constructed reaches it half-built. That is exactly
     * how the first live stage-advance died — {@code syncMap} ran during construction and read a
     * still-null core. Call this once, after the owner is fully built.
     */
    public void start() {
        // The start room holds no encounter, so door clearance is moot here.
        enter(layout.start(), null, true);
    }

    public DungeonLayout layout() {
        return layout;
    }

    public RoomState state(Room room) {
        return states.getOrDefault(room, RoomState.UNDISCOVERED);
    }

    public Set<Room> discovered() {
        return Set.copyOf(discovered);
    }

    public boolean isTrapdoorOpen() {
        return trapdoorOpen;
    }

    /** True when every room holding an encounter has been fought and cleared. */
    public boolean isFloorFullyCleared() {
        return layout.rooms().stream()
                .filter(RunCore::hasEncounter)
                .allMatch(r -> state(r) == RoomState.CLEARED);
    }

    /** Convenience for callers with nothing standing in a doorway — tests, mostly. */
    public void playerEnteredCell(UUID player, GridPos cell) {
        playerEnteredCell(player, cell, true);
    }

    /**
     * @param clearOfDoors whether the player is far enough from this room's doorways for them to
     *                     be sealed without closing on top of them. Discovery happens either way;
     *                     combat waits. Standing in a doorway is how you look before you commit.
     */
    public void playerEnteredCell(UUID player, GridPos cell, boolean clearOfDoors) {
        Room room = layout.grid().roomAt(cell);
        if (room != null) {
            enter(room, player, clearOfDoors);
        }
    }

    /**
     * One more enemy joined a fight already in progress — a boss summoning adds. It has to enter
     * the ledger or the room clears the moment the boss dies with its adds still standing.
     * Ignored outside combat: nothing may reseal a cleared room.
     *
     * @return whether the enemy was taken on, so the engine knows to track it (and to discard it
     *         if it was not)
     */
    public boolean enemyAdded(Room room) {
        if (states.get(room) != RoomState.IN_COMBAT) {
            return false;
        }
        ledger.merge(room, 1, Integer::sum);
        return true;
    }

    /**
     * The fight in {@code room} was deserted — every party member inside died or vanished. The
     * room drops back to DISCOVERED with its doors open and its ledger gone: the next entry seals
     * and spawns a fresh wave, nothing is rewarded, and — unlike the ledger reaching zero — a
     * deserted boss room does <b>not</b> open the trapdoor. Dying to the boss costs the attempt,
     * not the floor.
     *
     * <p>This is the one sanctioned backward step in the room state machine. The engine discards
     * whatever is left of the wave before calling it.</p>
     */
    public void abandonCombat(Room room) {
        if (states.get(room) != RoomState.IN_COMBAT) {
            return;
        }
        states.put(room, RoomState.DISCOVERED);
        ledger.remove(room);
        // A deserted challenge starts over from wave one — the plate is there to be stepped on again.
        wavesLeft.remove(room);
        callbacks.openRoom(room);
        callbacks.syncMap();
    }

    /**
     * A challenge room's plate was stepped on. Unlike every other encounter this one is opt-in:
     * the room is entered freely and only the plate starts the fight, which is what makes it a
     * challenge rather than an ambush. Ignored unless the room is a discovered, not-yet-fought
     * CHALLENGE, so a cleared room's plate is inert and a second player cannot restart a fight.
     */
    public void activatePlate(Room room) {
        if (room.type() != RoomType.CHALLENGE || states.get(room) != RoomState.DISCOVERED) {
            return;
        }
        int waves = Math.max(1, callbacks.challengeWaves(room));
        wavesLeft.put(room, waves - 1);
        states.put(room, RoomState.IN_COMBAT);
        callbacks.sealRoom(room);
        callbacks.sound(DungeonSound.CHALLENGE_STARTED, room);
        int spawned = callbacks.spawnChallengeWave(room, 0);
        ledger.put(room, spawned);
        if (spawned <= 0) {
            advanceWave(room);
        }
        callbacks.syncMap();
    }

    /** The engine reports one enemy of {@code room} gone — killed, despawned or unloaded. */
    public void enemyRemoved(Room room) {
        int remaining = ledger.getOrDefault(room, 0) - 1;
        if (states.get(room) != RoomState.IN_COMBAT) {
            return;
        }
        ledger.put(room, remaining);
        if (remaining <= 0) {
            advanceWave(room);
        }
    }

    /**
     * A wave fell. For a challenge with waves still owed, the doors stay shut and the next one
     * spawns; for everything else this is the clear. A wave that spawns nothing falls through to
     * the next rather than leaving the room sealed on an empty ledger.
     */
    private void advanceWave(Room room) {
        int left = wavesLeft.getOrDefault(room, 0);
        if (left <= 0) {
            wavesLeft.remove(room);
            clear(room);
            return;
        }
        // Clamped the same way activatePlate does, so a config reload mid-fight cannot make the
        // wave index disagree with the countdown that was set when the plate was stepped on.
        int waves = Math.max(1, callbacks.challengeWaves(room));
        int next = waves - left;
        wavesLeft.put(room, left - 1);
        callbacks.sound(DungeonSound.WAVE_CLEARED, room);
        int spawned = callbacks.spawnChallengeWave(room, next);
        ledger.put(room, spawned);
        if (spawned <= 0) {
            advanceWave(room);
        }
    }

    /** Waves a challenge room still owes after the one standing; 0 when it is on its last. */
    public int wavesRemaining(Room room) {
        return wavesLeft.getOrDefault(room, 0);
    }

    public int enemiesRemaining(Room room) {
        return states.get(room) == RoomState.IN_COMBAT ? ledger.getOrDefault(room, 0) : 0;
    }

    private void enter(Room room, UUID player, boolean clearOfDoors) {
        RoomState state = states.get(room);
        if (state == null || state == RoomState.IN_COMBAT || state == RoomState.CLEARED) {
            return;
        }
        if (state == RoomState.UNDISCOVERED) {
            states.put(room, RoomState.DISCOVERED);
            discovered.add(room);
            callbacks.roomDiscovered(room, player);
            callbacks.syncMap();
        }
        if (hasEncounter(room) && clearOfDoors) {
            beginCombat(room);
        }
    }

    private void beginCombat(Room room) {
        states.put(room, RoomState.IN_COMBAT);
        callbacks.sealRoom(room);
        callbacks.sound(isBossRoom(room) ? DungeonSound.BOSS_SEALED : DungeonSound.ROOM_SEALED, room);
        int spawned = callbacks.spawnEncounter(room);
        ledger.put(room, spawned);
        if (spawned <= 0) {
            clear(room);
        }
    }

    private void clear(Room room) {
        states.put(room, RoomState.CLEARED);
        ledger.remove(room);
        callbacks.openRoom(room);
        callbacks.sound(DungeonSound.ROOM_OPENED, room);
        callbacks.roomCleared(room);
        if (room.type() == RoomType.CHALLENGE) {
            callbacks.challengeCompleted(room);
        }
        if (room.type() == RoomType.BOSS) {
            trapdoorOpen = true;
            callbacks.sound(DungeonSound.BOSS_DEFEATED, room);
            callbacks.openTrapdoor(room);
        }
        callbacks.syncMap();
    }

    private static boolean isBossRoom(Room room) {
        return room.type() == RoomType.BOSS || room.type() == RoomType.MINI_BOSS;
    }

    /**
     * Which rooms fight you for walking in. CHALLENGE is deliberately absent: it seals on its
     * plate instead ({@link #activatePlate}), so entering one is safe and committing to it is a
     * choice.
     */
    static boolean hasEncounter(Room room) {
        return switch (room.type()) {
            case NORMAL, MINI_BOSS, BOSS -> true;
            case START, TREASURE, SHOP, SECRET, SUPER_SECRET, CURSE, CHALLENGE,
                 SACRIFICE, ARCADE, DEVIL_DEAL -> false;
        };
    }
}
