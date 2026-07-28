package es.boffmedia.teras.dungeon.instance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The run lifecycle's three pieces of bookkeeping: which slots are taken, who is in which run, and
 * the way home of a player who was offline when their run ended.
 *
 * <h2>What is not here, and why</h2>
 *
 * <p>{@code DungeonRunManager.start()}, {@code advanceStage()} and {@code end()} need a
 * {@code MinecraftServer}, a {@code ServerLevel} and a materializer that pastes blocks; the test
 * sourceset deliberately has no Minecraft on it (see build.gradle), so they are manual test cases —
 * {@code /teras dungeon} on a live server — rather than untested-and-unmentioned. What they are
 * <i>made of</i> is here: the slot table, the membership index and the journal all moved out into
 * classes that can be exercised without a world, which is most of what those three methods get
 * wrong when they get anything wrong.</p>
 */
class DungeonRunManagerTest {

    private static final UUID ANA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BRUNO = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID CARLA = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    // --- slots ---------------------------------------------------------------------------------

    @Test
    void slotsAreHandedOutLowestFirst() {
        SlotTable slots = new SlotTable();
        assertEquals(0, slots.allocate(4));
        assertEquals(1, slots.allocate(4));
        assertEquals(2, slots.allocate(4));
        assertEquals(3, slots.used());
    }

    @Test
    void aFreedSlotIsReused() {
        SlotTable slots = new SlotTable();
        slots.allocate(4);
        slots.allocate(4);
        slots.free(0);
        assertFalse(slots.isTaken(0));
        assertEquals(0, slots.allocate(4), "the lowest free slot is the one below the live run");
        assertTrue(slots.isTaken(0));
    }

    @Test
    void theCapRefusesRatherThanGrowing() {
        SlotTable slots = new SlotTable();
        assertEquals(0, slots.allocate(1));
        assertEquals(-1, slots.allocate(1));
        assertEquals(-1, slots.nextFree(1), "a full lattice has to refuse before the generator runs");
        slots.free(0);
        assertEquals(0, slots.nextFree(1));
    }

    @Test
    void aCapLoweredUnderLiveRunsRefusesInsteadOfMisbehaving() {
        // The cap is config and is read fresh at every allocation, so an operator can shrink it
        // while runs are standing. Those runs keep their slots; no new one is handed out.
        SlotTable slots = new SlotTable();
        slots.allocate(8);
        slots.allocate(8);
        assertEquals(-1, slots.allocate(2));
        assertEquals(2, slots.used());
    }

    // --- membership ----------------------------------------------------------------------------

    @Test
    void everyMemberPointsAtTheirRun() {
        RunIndex index = new RunIndex();
        index.index(List.of(ANA, BRUNO), 7);
        assertEquals(7, index.runIdOf(ANA));
        assertEquals(7, index.runIdOf(BRUNO));
        assertNull(index.runIdOf(CARLA));
    }

    @Test
    void unindexingDropsTheWholeParty() {
        RunIndex index = new RunIndex();
        index.index(List.of(ANA, BRUNO), 7);
        index.index(List.of(CARLA), 8);
        index.unindex(7);
        assertNull(index.runIdOf(ANA));
        assertNull(index.runIdOf(BRUNO));
        assertEquals(8, index.runIdOf(CARLA), "another run's members are untouched");
    }

    @Test
    void unindexingIsByRunIdSoAnEmptiedPartyStillCleansUp() {
        // The bug this shape exists for: a run whose members all walked out has an empty party, so
        // walking party() at the end would leave their index entries pointing at a dead run.
        RunIndex index = new RunIndex();
        index.index(List.of(ANA, BRUNO), 7);
        index.unindex(7);
        assertEquals(0, index.size());
    }

    @Test
    void oneMemberLeavingLeavesTheRestIndexed() {
        RunIndex index = new RunIndex();
        index.index(List.of(ANA, BRUNO), 7);
        index.remove(ANA);
        assertNull(index.runIdOf(ANA));
        assertEquals(7, index.runIdOf(BRUNO));
    }

    // --- the returns journal --------------------------------------------------------------------

    @Test
    void returnsSurviveARoundTrip(@TempDir Path dir) {
        Path file = dir.resolve("returns.json");
        DungeonRun.ReturnPoint point = new DungeonRun.ReturnPoint(
                "minecraft:overworld", 12.5, 64.0, -33.25, 90f, -12f, "creative");
        RunJournal.saveReturns(Map.of(ANA, new RunJournal.TimestampedReturn(point, 1_000L)), file);

        Map<UUID, RunJournal.TimestampedReturn> read = RunJournal.loadReturns(file);
        assertEquals(1, read.size());
        RunJournal.TimestampedReturn entry = read.get(ANA);
        assertNotNull(entry);
        assertEquals(point, entry.point());
        assertEquals(1_000L, entry.savedAtMs());
    }

    @Test
    void aMissingReturnsFileIsEmptyRatherThanAFailure(@TempDir Path dir) {
        assertTrue(RunJournal.loadReturns(dir.resolve("nothing-here.json")).isEmpty());
    }

    @Test
    void aCorruptReturnsFileIsEmptyRatherThanAFailure(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("returns.json");
        Files.writeString(file, "{ this is not json");
        assertTrue(RunJournal.loadReturns(file).isEmpty());
    }

    @Test
    void anEntryWrittenBeforeTimestampsExistedIsNeverExpired(@TempDir Path dir) throws Exception {
        // The old format was a bare return point per uuid. Reading one as "saved now" is the safe
        // direction: an upgrade must not sweep away the ways home it inherits.
        Path file = dir.resolve("returns.json");
        Files.writeString(file, "{\"" + ANA + "\":{\"dim\":\"minecraft:overworld\",\"x\":1.0,"
                + "\"y\":2.0,\"z\":3.0,\"yaw\":0.0,\"pitch\":0.0,\"mode\":\"survival\"}}");

        RunJournal.TimestampedReturn entry = RunJournal.loadReturns(file).get(ANA);
        assertNotNull(entry);
        assertEquals(1.0, entry.point().x());
        assertTrue(entry.savedAtMs() > System.currentTimeMillis() - 60_000,
                "an undated entry reads as filed now, so it cannot be swept on the boot that "
                        + "first reads it");
    }

    @Test
    void aReturnPointWrittenWithoutAGameModeReadsAsSurvival(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("returns.json");
        Files.writeString(file, "{\"" + ANA + "\":{\"point\":{\"dim\":\"minecraft:overworld\","
                + "\"x\":1.0,\"y\":2.0,\"z\":3.0,\"yaw\":0.0,\"pitch\":0.0},\"savedAtMs\":5}}");
        assertEquals("survival", RunJournal.loadReturns(file).get(ANA).point().gameMode());
    }

    @Test
    void savingAnEmptyMapClearsTheFile(@TempDir Path dir) {
        Path file = dir.resolve("returns.json");
        RunJournal.saveReturns(Map.of(ANA, new RunJournal.TimestampedReturn(
                new DungeonRun.ReturnPoint("minecraft:overworld", 0, 0, 0, 0, 0, "survival"),
                1L)), file);
        RunJournal.saveReturns(Map.of(), file);
        assertTrue(RunJournal.loadReturns(file).isEmpty());
    }
}
