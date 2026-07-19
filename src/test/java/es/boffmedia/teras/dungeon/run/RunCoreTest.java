package es.boffmedia.teras.dungeon.run;

import es.boffmedia.teras.dungeon.gen.DungeonGenerator;
import es.boffmedia.teras.dungeon.gen.GenConfig;
import es.boffmedia.teras.dungeon.model.DungeonLayout;
import es.boffmedia.teras.dungeon.model.Room;
import es.boffmedia.teras.dungeon.model.RoomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The floor loop, driven with scripted entries and kills against a real generated layout —
 * the same recording-callbacks pattern as {@code RaceCoreTest}.
 */
class RunCoreTest {

    private static final UUID ANA = UUID.nameUUIDFromBytes("ana".getBytes());

    private DungeonLayout layout;
    private RecordingCallbacks callbacks;
    private RunCore core;

    private static final class RecordingCallbacks implements RunCallbacks {
        final List<Room> discovered = new ArrayList<>();
        final List<Room> sealed = new ArrayList<>();
        final List<Room> opened = new ArrayList<>();
        final List<Room> cleared = new ArrayList<>();
        final List<Room> trapdoors = new ArrayList<>();
        final List<DungeonSound> cues = new ArrayList<>();
        int mapSyncs;
        int nextSpawnCount = 3;

        @Override
        public void roomDiscovered(Room room, UUID discoverer) {
            discovered.add(room);
        }

        @Override
        public void sealRoom(Room room) {
            sealed.add(room);
        }

        @Override
        public void openRoom(Room room) {
            opened.add(room);
        }

        @Override
        public void sound(DungeonSound sound, Room room) {
            cues.add(sound);
        }

        @Override
        public int spawnEncounter(Room room) {
            return nextSpawnCount;
        }

        @Override
        public void roomCleared(Room room) {
            cleared.add(room);
        }

        @Override
        public void openTrapdoor(Room bossRoom) {
            trapdoors.add(bossRoom);
        }

        @Override
        public void syncMap() {
            mapSyncs++;
        }
    }

    @BeforeEach
    void setUp() {
        layout = DungeonGenerator.generate(GenConfig.defaults(), 1, java.util.Set.of(), "runcore");
        callbacks = new RecordingCallbacks();
        core = new RunCore(layout, callbacks);
        core.start();
    }

    /**
     * The constructor must not call back into its owner: the engine's callbacks reference the
     * object under construction, and a callback fired from the constructor reached it half-built.
     * That crashed the first live stage-advance, so it stays pinned.
     */
    @Test
    void constructorFiresNoCallbacks() {
        RecordingCallbacks fresh = new RecordingCallbacks();
        new RunCore(layout, fresh);

        assertTrue(fresh.discovered.isEmpty());
        assertEquals(0, fresh.mapSyncs);
    }

    private Room roomOf(RoomType type) {
        return layout.roomOfType(type).orElseThrow();
    }

    @Test
    void startRoomIsDiscoveredButNeverSealed() {
        assertEquals(RoomState.DISCOVERED, core.state(layout.start()));
        assertTrue(callbacks.discovered.contains(layout.start()));
        assertTrue(callbacks.sealed.isEmpty());
    }

    @Test
    void enteringANormalRoomSealsAndSpawns() {
        Room normal = firstNormalRoom();
        core.playerEnteredCell(ANA, normal.cells().get(0));

        assertEquals(RoomState.IN_COMBAT, core.state(normal));
        assertEquals(List.of(normal), callbacks.sealed);
        assertEquals(3, core.enemiesRemaining(normal));
    }

    @Test
    void killingEveryEnemyClearsAndOpens() {
        Room normal = firstNormalRoom();
        core.playerEnteredCell(ANA, normal.cells().get(0));
        core.enemyRemoved(normal);
        core.enemyRemoved(normal);
        assertEquals(RoomState.IN_COMBAT, core.state(normal));
        core.enemyRemoved(normal);

        assertEquals(RoomState.CLEARED, core.state(normal));
        assertEquals(List.of(normal), callbacks.opened);
        assertEquals(List.of(normal), callbacks.cleared);
    }

    @Test
    void clearedRoomsNeverRespawn() {
        Room normal = firstNormalRoom();
        core.playerEnteredCell(ANA, normal.cells().get(0));
        for (int i = 0; i < 3; i++) {
            core.enemyRemoved(normal);
        }
        core.playerEnteredCell(ANA, normal.cells().get(0));

        assertEquals(1, callbacks.sealed.size());
        assertEquals(RoomState.CLEARED, core.state(normal));
    }

    @Test
    void reenteringDuringCombatDoesNothing() {
        Room normal = firstNormalRoom();
        core.playerEnteredCell(ANA, normal.cells().get(0));
        core.playerEnteredCell(ANA, normal.cells().get(0));

        assertEquals(1, callbacks.sealed.size());
        assertEquals(3, core.enemiesRemaining(normal));
    }

    /** Standing in the doorway discovers the room but must not close the doors on the player. */
    @Test
    void standingInADoorwayDiscoversWithoutSealing() {
        Room normal = firstNormalRoom();
        core.playerEnteredCell(ANA, normal.cells().get(0), false);

        assertEquals(RoomState.DISCOVERED, core.state(normal));
        assertTrue(callbacks.sealed.isEmpty());
        assertTrue(callbacks.discovered.contains(normal));
    }

    /** …and stepping clear of it then starts the fight. */
    @Test
    void steppingClearOfTheDoorwaySeals() {
        Room normal = firstNormalRoom();
        core.playerEnteredCell(ANA, normal.cells().get(0), false);
        core.playerEnteredCell(ANA, normal.cells().get(0), true);

        assertEquals(RoomState.IN_COMBAT, core.state(normal));
        assertEquals(List.of(normal), callbacks.sealed);
        assertEquals(1, callbacks.discovered.stream().filter(r -> r == normal).count());
    }

    @Test
    void treasureAndShopOnlyDiscover() {
        Room treasure = roomOf(RoomType.TREASURE);
        Room shop = roomOf(RoomType.SHOP);
        core.playerEnteredCell(ANA, treasure.cells().get(0));
        core.playerEnteredCell(ANA, shop.cells().get(0));

        assertEquals(RoomState.DISCOVERED, core.state(treasure));
        assertEquals(RoomState.DISCOVERED, core.state(shop));
        assertTrue(callbacks.sealed.isEmpty());
    }

    @Test
    void bossClearOpensTheTrapdoor() {
        Room boss = roomOf(RoomType.BOSS);
        callbacks.nextSpawnCount = 1;
        core.playerEnteredCell(ANA, boss.cells().get(0));
        assertFalse(core.isTrapdoorOpen());
        core.enemyRemoved(boss);

        assertTrue(core.isTrapdoorOpen());
        assertEquals(List.of(boss), callbacks.trapdoors);
    }

    @Test
    void zeroSpawnsClearInstantlyInsteadOfSealingForever() {
        Room normal = firstNormalRoom();
        callbacks.nextSpawnCount = 0;
        core.playerEnteredCell(ANA, normal.cells().get(0));

        assertEquals(RoomState.CLEARED, core.state(normal));
        assertEquals(List.of(normal), callbacks.opened);
    }

    @Test
    void multiCellRoomsDiscoverOnce() {
        Room large = layout.rooms().stream()
                .filter(r -> r.shape().cellCount() > 1)
                .findFirst().orElse(null);
        if (large == null) {
            return;
        }
        for (var cell : large.cells()) {
            core.playerEnteredCell(ANA, cell);
        }
        assertEquals(1, callbacks.discovered.stream().filter(r -> r == large).count());
        assertEquals(1, callbacks.sealed.stream().filter(r -> r == large).count());
    }

    @Test
    void floorFullyClearedTracksEveryEncounterRoom() {
        assertFalse(core.isFloorFullyCleared());
        for (Room room : layout.rooms()) {
            if (!RunCore.hasEncounter(room)) {
                continue;
            }
            core.playerEnteredCell(ANA, room.cells().get(0));
            while (core.enemiesRemaining(room) > 0) {
                core.enemyRemoved(room);
            }
        }
        assertTrue(core.isFloorFullyCleared());
    }

    @Test
    void sealingAndClearingEachEmitTheirCueExactlyOnce() {
        Room normal = firstNormalRoom();
        callbacks.nextSpawnCount = 2;
        core.playerEnteredCell(ANA, normal.cells().get(0));
        assertEquals(List.of(DungeonSound.ROOM_SEALED), callbacks.cues);

        core.enemyRemoved(normal);
        core.enemyRemoved(normal);
        assertEquals(List.of(DungeonSound.ROOM_SEALED, DungeonSound.ROOM_OPENED), callbacks.cues);
    }

    /** An empty wave still has to announce both halves, or the doors read as never having shut. */
    @Test
    void anInstantlyClearedRoomStillEmitsBothCues() {
        callbacks.nextSpawnCount = 0;
        core.playerEnteredCell(ANA, firstNormalRoom().cells().get(0));

        assertEquals(List.of(DungeonSound.ROOM_SEALED, DungeonSound.ROOM_OPENED), callbacks.cues);
    }

    @Test
    void theBossRoomGetsItsOwnSealAndDefeatCues() {
        Room boss = roomOf(RoomType.BOSS);
        callbacks.nextSpawnCount = 1;
        core.playerEnteredCell(ANA, boss.cells().get(0));
        core.enemyRemoved(boss);

        assertEquals(List.of(DungeonSound.BOSS_SEALED, DungeonSound.ROOM_OPENED,
                DungeonSound.BOSS_DEFEATED), callbacks.cues);
    }

    /**
     * Adds summoned mid-fight have to hold the room open. Without this a boss that spawns three
     * skeletons at half health clears its room the instant the boss itself dies.
     */
    @Test
    void summonedAddsKeepTheRoomSealed() {
        Room boss = roomOf(RoomType.BOSS);
        callbacks.nextSpawnCount = 1;
        core.playerEnteredCell(ANA, boss.cells().get(0));

        assertTrue(core.enemyAdded(boss));
        assertTrue(core.enemyAdded(boss));
        assertEquals(3, core.enemiesRemaining(boss));

        core.enemyRemoved(boss);
        assertEquals(RoomState.IN_COMBAT, core.state(boss));
        core.enemyRemoved(boss);
        core.enemyRemoved(boss);
        assertEquals(RoomState.CLEARED, core.state(boss));
        assertTrue(core.isTrapdoorOpen());
    }

    /** Nothing may reseal a room that is already done — a late summon is dropped, not honoured. */
    @Test
    void addingAnEnemyToAClearedRoomIsRefused() {
        Room normal = firstNormalRoom();
        callbacks.nextSpawnCount = 1;
        core.playerEnteredCell(ANA, normal.cells().get(0));
        core.enemyRemoved(normal);
        assertEquals(RoomState.CLEARED, core.state(normal));

        assertFalse(core.enemyAdded(normal));
        assertEquals(RoomState.CLEARED, core.state(normal));
    }

    /** A deserted fight resets: doors open, no reward, and the rematch spawns fresh. */
    @Test
    void abandoningAFightReopensTheRoomForARematch() {
        Room normal = firstNormalRoom();
        callbacks.nextSpawnCount = 2;
        core.playerEnteredCell(ANA, normal.cells().get(0));
        assertEquals(RoomState.IN_COMBAT, core.state(normal));

        core.abandonCombat(normal);
        assertEquals(RoomState.DISCOVERED, core.state(normal));
        assertEquals(List.of(normal), callbacks.opened);
        assertTrue(callbacks.cleared.isEmpty());
        assertEquals(0, core.enemiesRemaining(normal));

        core.playerEnteredCell(ANA, normal.cells().get(0));
        assertEquals(RoomState.IN_COMBAT, core.state(normal));
        core.enemyRemoved(normal);
        core.enemyRemoved(normal);
        assertEquals(RoomState.CLEARED, core.state(normal));
        assertEquals(List.of(normal), callbacks.cleared);
    }

    /**
     * Dying to the boss costs the attempt, not the floor — the playtest saw the trapdoor open
     * over a boss nobody killed, because desertion used to read as the wave being removed.
     */
    @Test
    void abandonedBossFightDoesNotOpenTheTrapdoor() {
        Room boss = roomOf(RoomType.BOSS);
        callbacks.nextSpawnCount = 1;
        core.playerEnteredCell(ANA, boss.cells().get(0));

        core.abandonCombat(boss);

        assertFalse(core.isTrapdoorOpen());
        assertTrue(callbacks.trapdoors.isEmpty());
        assertEquals(RoomState.DISCOVERED, core.state(boss));
    }

    @Test
    void abandonOutsideCombatIsIgnored() {
        Room normal = firstNormalRoom();
        core.abandonCombat(normal);
        assertEquals(RoomState.UNDISCOVERED, core.state(normal));

        Room cleared = firstNormalRoom();
        callbacks.nextSpawnCount = 0;
        core.playerEnteredCell(ANA, cleared.cells().get(0));
        assertEquals(RoomState.CLEARED, core.state(cleared));
        core.abandonCombat(cleared);
        assertEquals(RoomState.CLEARED, core.state(cleared));
    }

    private Room firstNormalRoom() {
        return layout.rooms().stream()
                .filter(r -> r.type() == RoomType.NORMAL)
                .findFirst().orElseThrow();
    }
}
