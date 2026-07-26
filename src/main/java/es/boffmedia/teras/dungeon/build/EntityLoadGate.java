package es.boffmedia.teras.dungeon.build;

import java.util.function.LongConsumer;
import java.util.function.LongPredicate;

/**
 * Holds a job back until the entities of the chunks it is about to sweep are actually in memory.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Every sweep in the dungeon system used to load a chunk and read its entities in the same tick,
 * which cannot work. Blocks and entities are loaded by two different mechanisms in 1.21:
 * {@code ServerLevel.getChunk} blocks until the <i>block</i> chunk is there, but the entity sections
 * are read off-thread — {@code PersistentEntitySectionManager.requestChunkLoad} starts a future,
 * whose result is only merged into the level's entity storage by {@code processPendingLoads()} from
 * the <i>next</i> {@code ServerLevel.tick}. So on a pad whose chunks had gone quiet — after a
 * restart, after a party logged out, after any pad simply stopped being looked at —
 * {@code getEntities} returned nothing, the blocks were cleared around entities nobody had removed,
 * and the shop wares and chest labels of a finished floor were left floating in the void for the
 * next floor built on that pad to paste itself around.</p>
 *
 * <p>Nothing said a word: from the sweep's point of view it succeeded. It discarded every entity it
 * could see.</p>
 *
 * <h2>Why the chunks are re-asked for every tick</h2>
 *
 * <p>{@code TicketType.UNKNOWN} — the ticket {@code getChunk} adds — has a lifespan of <b>one
 * tick</b>. A gate that asked for its chunks once and then only watched would see the first ones
 * unload while it waited for the last, and unloading a chunk unloads the entities that had just
 * arrived in it. So every tick re-asks for everything already asked for; that is what keeps the
 * batch resident long enough to be swept in one piece.</p>
 *
 * <p>New chunks are asked for at a bounded rate because the caller's batch can be a whole pad. The
 * requests pipeline — the off-thread reads all run concurrently — so the wait is one round of I/O
 * latency for the batch rather than one per chunk.</p>
 *
 * <p>Minecraft-free on purpose: the caller supplies "ask for this chunk" and "are this chunk's
 * entities loaded", so the pacing is one testable class.</p>
 */
public final class EntityLoadGate {

    /** What the caller should do this tick. */
    public enum State {
        /** Nothing yet — come back next tick. */
        WAITING,
        /** Every chunk's entities are in memory: sweep now. */
        READY,
        /**
         * They never arrived. The caller sweeps anyway (it is still better than not sweeping) and
         * says so in the log: an entity chunk that never loads is a broken read, not a slow one, and
         * retrying it forever would stall every other run behind it in the queue.
         */
        GAVE_UP
    }

    private final long[] chunks;
    private final int requestsPerTick;
    private final int patienceTicks;
    /** How many of {@link #chunks} have been asked for at least once. */
    private int requested;
    /** Ticks spent waiting <i>after</i> everything was asked for; the patience is about the read. */
    private int waited;

    /**
     * @param chunks         chunk keys ({@code ChunkPos.asLong}) covering everything about to be
     *                       swept; an empty array is immediately {@link State#READY}
     * @param requestsPerTick how many new chunks to ask for each tick
     * @param patienceTicks  how long to wait, once all of them have been asked for, before giving up
     */
    public EntityLoadGate(long[] chunks, int requestsPerTick, int patienceTicks) {
        this.chunks = chunks.clone();
        this.requestsPerTick = Math.max(1, requestsPerTick);
        this.patienceTicks = Math.max(0, patienceTicks);
    }

    /**
     * One tick of waiting.
     *
     * @param request asks the level for a chunk, renewing its load ticket
     * @param loaded  whether that chunk's entities are in the level's entity storage
     */
    public State poll(LongConsumer request, LongPredicate loaded) {
        for (int i = 0; i < requested; i++) {
            request.accept(chunks[i]);
        }
        for (int budget = requestsPerTick; budget > 0 && requested < chunks.length; budget--) {
            request.accept(chunks[requested++]);
        }
        if (requested < chunks.length) {
            return State.WAITING;
        }
        for (long chunk : chunks) {
            if (!loaded.test(chunk)) {
                return ++waited > patienceTicks ? State.GAVE_UP : State.WAITING;
            }
        }
        return State.READY;
    }

    /** How many chunks the gate is holding resident. */
    public int size() {
        return chunks.length;
    }
}
