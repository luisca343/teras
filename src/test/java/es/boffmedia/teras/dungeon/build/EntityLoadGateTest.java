package es.boffmedia.teras.dungeon.build;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gate that stops a floor being swept before its entities are in memory.
 *
 * <p>The bug it exists for: asking for a chunk gets its blocks immediately and its entities on a
 * later tick, so every sweep that loaded a chunk and read it in the same breath saw nothing on a pad
 * that had gone cold — and the chest labels and shop wares of a finished floor stayed standing.</p>
 */
class EntityLoadGateTest {

    /** A level that hands entities over {@code latency} ticks after a chunk is first asked for. */
    private static final class FakeLevel {
        private final int latency;
        private final Map<Long, Integer> askedOn = new HashMap<>();
        private final Set<Long> neverLoads = new HashSet<>();
        private final List<Long> requestsThisTick = new ArrayList<>();
        private int tick;

        FakeLevel(int latency) {
            this.latency = latency;
        }

        void request(long chunk) {
            requestsThisTick.add(chunk);
            askedOn.putIfAbsent(chunk, tick);
        }

        boolean loaded(long chunk) {
            Integer asked = askedOn.get(chunk);
            return asked != null && !neverLoads.contains(chunk) && tick - asked >= latency;
        }

        EntityLoadGate.State poll(EntityLoadGate gate) {
            requestsThisTick.clear();
            EntityLoadGate.State state = gate.poll(this::request, this::loaded);
            tick++;
            return state;
        }
    }

    private static long[] chunks(int count) {
        long[] keys = new long[count];
        for (int i = 0; i < count; i++) {
            keys[i] = i;
        }
        return keys;
    }

    @Test
    void nothingToWaitForIsReadyAtOnce() {
        EntityLoadGate gate = new EntityLoadGate(new long[0], 8, 100);
        assertEquals(EntityLoadGate.State.READY,
                gate.poll(chunk -> { }, chunk -> false));
    }

    @Test
    void alreadyLoadedChunksAreReadyOnTheFirstTick() {
        FakeLevel level = new FakeLevel(0);
        EntityLoadGate gate = new EntityLoadGate(chunks(4), 8, 100);
        assertEquals(EntityLoadGate.State.READY, level.poll(gate));
        assertEquals(4, level.requestsThisTick.size());
    }

    @Test
    void theSweepWaitsForTheEntityRead() {
        FakeLevel level = new FakeLevel(2);
        EntityLoadGate gate = new EntityLoadGate(chunks(4), 8, 100);
        // This is the tick the old code swept on, and it is the tick the entities are not there yet.
        assertEquals(EntityLoadGate.State.WAITING, level.poll(gate));
        assertEquals(EntityLoadGate.State.WAITING, level.poll(gate));
        assertEquals(EntityLoadGate.State.READY, level.poll(gate));
    }

    @Test
    void chunksAlreadyAskedForAreAskedForAgainEveryTick() {
        FakeLevel level = new FakeLevel(3);
        EntityLoadGate gate = new EntityLoadGate(chunks(2), 8, 100);
        level.poll(gate);
        // The load ticket lasts one tick: a gate that stopped asking would watch the chunks it is
        // waiting on unload again, taking the entities that had just arrived with them.
        assertEquals(List.of(0L, 1L), level.requestsThisTick);
        level.poll(gate);
        assertEquals(List.of(0L, 1L), level.requestsThisTick);
    }

    @Test
    void newChunksAreAskedForAtABoundedRate() {
        FakeLevel level = new FakeLevel(0);
        EntityLoadGate gate = new EntityLoadGate(chunks(10), 4, 100);
        assertEquals(EntityLoadGate.State.WAITING, level.poll(gate));
        assertEquals(4, level.requestsThisTick.size());
        assertEquals(EntityLoadGate.State.WAITING, level.poll(gate));
        // Four keep-alives and four new ones: a whole pad is hundreds of chunks and must not be
        // asked for in one tick.
        assertEquals(8, level.requestsThisTick.size());
        assertEquals(EntityLoadGate.State.READY, level.poll(gate));
        assertEquals(10, level.requestsThisTick.size());
    }

    @Test
    void patienceIsSpentOnTheReadAndNotOnTheAsking() {
        FakeLevel level = new FakeLevel(0);
        level.neverLoads.add(3L);
        EntityLoadGate gate = new EntityLoadGate(chunks(8), 2, 2);
        // Three ticks still asking (two chunks a tick, eight of them), none of which may count
        // against the patience — a big batch would otherwise time itself out before it had finished
        // asking for the chunks it is waiting on.
        for (int i = 0; i < 3; i++) {
            assertEquals(EntityLoadGate.State.WAITING, level.poll(gate));
        }
        // The fourth is the first tick everything has been asked for; the patience starts here.
        assertEquals(EntityLoadGate.State.WAITING, level.poll(gate));
        assertEquals(EntityLoadGate.State.WAITING, level.poll(gate));
        // A read that never lands is broken, not slow: the caller sweeps what it can see and says so,
        // rather than holding the shared job queue open for every other run behind it.
        assertEquals(EntityLoadGate.State.GAVE_UP, level.poll(gate));
    }

    @Test
    void oneUnreadyChunkHoldsTheWholeBatch() {
        FakeLevel level = new FakeLevel(0);
        level.neverLoads.add(2L);
        EntityLoadGate gate = new EntityLoadGate(chunks(4), 8, 3);
        // A batch is swept in one piece, so it is only safe when every chunk of it is readable.
        assertEquals(EntityLoadGate.State.WAITING, level.poll(gate));
        level.neverLoads.clear();
        assertEquals(EntityLoadGate.State.READY, level.poll(gate));
        assertTrue(level.requestsThisTick.contains(2L));
    }
}
