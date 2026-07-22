package es.boffmedia.teras.dungeon.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Things to do later, keyed on the server tick they come due.
 *
 * <p>Exists because <b>{@code MinecraftServer.tell(new TickTask(now + n, …))} does not delay
 * anything.</b> Vanilla's scheduler runs a task when
 * {@code task.getTick() + 3 < tickCount || this.haveTime()}, and {@code haveTime()} is true whenever
 * the current tick has spare time left — which is almost always. The tick number on a {@code
 * TickTask} is a deadline for catching up, not a delay, so a task scheduled eighty ticks out runs in
 * the same tick on any server that is not already overloaded.</p>
 *
 * <p>That is why the infestation's egg sacs always cracked the instant the doors sealed. The four
 * second pause they were written around — long enough for the party to have started fighting, and
 * to have decided whether to fight near a sac or away from it — never happened once.</p>
 *
 * <p>Pure, so the ordering can be tested without a server: {@link es.boffmedia.teras.dungeon.run
 * .DungeonScheduler} is the thin shim that owns one of these and drains it on the tick event.</p>
 *
 * @param <T> whatever the caller wants back when the time comes
 */
public final class TickQueue<T> {

    private record Pending<T>(long due, T payload) {}

    private final List<Pending<T>> pending = new ArrayList<>();

    /** Queues {@code payload} to come due {@code delay} ticks after {@code now}. */
    public void in(long now, int delay, T payload) {
        pending.add(new Pending<>(now + Math.max(0, delay), payload));
    }

    /**
     * Everything due at or before {@code now}, removed from the queue, oldest deadline first.
     *
     * <p>Ordered because two things queued for the same tick from different rooms should still come
     * out in the order they were promised, and a caller that reschedules from inside a drained task
     * must not be able to starve anything.</p>
     */
    public List<T> drain(long now) {
        if (pending.isEmpty()) {
            return List.of();
        }
        List<Pending<T>> due = new ArrayList<>();
        pending.removeIf(item -> {
            if (item.due() <= now) {
                due.add(item);
                return true;
            }
            return false;
        });
        due.sort(Comparator.comparingLong(Pending::due));
        return due.stream().map(Pending::payload).toList();
    }

    /** Drops everything, for a server shutting down or a run being torn down. */
    public void clear() {
        pending.clear();
    }

    public int size() {
        return pending.size();
    }

    public boolean isEmpty() {
        return pending.isEmpty();
    }
}
