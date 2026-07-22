package es.boffmedia.teras.dungeon.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Delaying something by a number of ticks.
 *
 * <p>Worth its own type and its own tests because the obvious way to do this in Minecraft is wrong:
 * {@code MinecraftServer.tell(new TickTask(now + n, …))} runs the task as soon as the tick has spare
 * time, whatever tick it names. The infestation's egg sacs were scheduled that way and always
 * cracked immediately, so the pause the mechanic was designed around never once happened.</p>
 */
class TickQueueTest {

    @Test
    void nothingComesDueBeforeItsTime() {
        TickQueue<String> queue = new TickQueue<>();
        queue.in(100, 80, "hatch");
        assertEquals(List.of(), queue.drain(100), "the tick it was queued on");
        assertEquals(List.of(), queue.drain(179), "one short");
        assertEquals(List.of("hatch"), queue.drain(180));
        assertTrue(queue.isEmpty(), "and it is gone once taken");
    }

    /** A tick can be missed — a lagging server, a task queued while the loop was busy. */
    @Test
    void anythingOverdueComesOutToo() {
        TickQueue<String> queue = new TickQueue<>();
        queue.in(0, 5, "a");
        queue.in(0, 10, "b");
        assertEquals(List.of("a", "b"), queue.drain(500), "both are overdue, earliest first");
    }

    @Test
    void thingsComeOutInDeadlineOrderNotInsertionOrder() {
        TickQueue<String> queue = new TickQueue<>();
        queue.in(0, 30, "late");
        queue.in(0, 10, "early");
        queue.in(0, 20, "middle");
        assertEquals(List.of("early", "middle", "late"), queue.drain(100));
    }

    @Test
    void onlyWhatIsDueLeavesTheQueue() {
        TickQueue<String> queue = new TickQueue<>();
        queue.in(0, 5, "now");
        queue.in(0, 50, "later");
        assertEquals(List.of("now"), queue.drain(10));
        assertEquals(1, queue.size());
        assertEquals(List.of("later"), queue.drain(60));
    }

    /** A zero or negative delay means the next drain, not never and not a crash. */
    @Test
    void aZeroDelayIsDueImmediately() {
        TickQueue<String> queue = new TickQueue<>();
        queue.in(40, 0, "now");
        queue.in(40, -5, "also now");
        assertEquals(List.of("now", "also now"), queue.drain(40));
    }

    @Test
    void clearDropsEverything() {
        TickQueue<String> queue = new TickQueue<>();
        queue.in(0, 10, "a");
        queue.clear();
        assertTrue(queue.isEmpty());
        assertEquals(List.of(), queue.drain(1000));
    }
}
