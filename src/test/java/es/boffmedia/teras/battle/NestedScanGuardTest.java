package es.boffmedia.teras.battle;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the latch that stops the backpack scanner recursing into nested backpacks.
 *
 * <p>Worth testing on its own because both failure modes are silent: leaking the latch disables the
 * scanner for the rest of that thread's life (battle items in backpacks quietly stop being offered),
 * while failing to hold it lets {@code checkInventory}'s re-entry recurse until the stack overflows.</p>
 */
class NestedScanGuardTest {

    @Test
    void admitsTheFirstScan() {
        NestedScanGuard guard = new NestedScanGuard();
        assertFalse(guard.isInside(), "starts outside");
        assertTrue(guard.enter(), "the outermost scan may run");
        assertTrue(guard.isInside());
    }

    /** The nested backpack case: checkInventory re-enters the scanner that is already running. */
    @Test
    void refusesAReentrantScan() {
        NestedScanGuard guard = new NestedScanGuard();
        assertTrue(guard.enter());
        assertFalse(guard.enter(), "a backpack inside a backpack must not descend");
        assertFalse(guard.enter(), "and still not on a third attempt");
    }

    @Test
    void admitsAgainOnceTheScanFinishes() {
        NestedScanGuard guard = new NestedScanGuard();
        assertTrue(guard.enter());
        guard.exit();
        assertFalse(guard.isInside(), "exit releases the latch");
        assertTrue(guard.enter(), "the next backpack in the same inventory scans normally");
    }

    /**
     * The scanner pairs enter/exit in a finally, so a throw mid-scan must not leave the latch held —
     * otherwise one bad backpack silently disables the feature until the server restarts.
     */
    @Test
    void releasesTheLatchWhenAScanThrows() {
        NestedScanGuard guard = new NestedScanGuard();
        try {
            if (guard.enter()) {
                try {
                    throw new IllegalStateException("a backpack mod blew up mid-scan");
                } finally {
                    guard.exit();
                }
            }
        } catch (IllegalStateException expected) {
            // the scanner lets it propagate; only the latch's state matters here
        }
        assertFalse(guard.isInside(), "latch released despite the throw");
        assertTrue(guard.enter(), "and the next scan still runs");
    }

    /** Two players' scans can land on different threads; one must never block or unlatch the other. */
    @Test
    void latchesPerThread() throws Exception {
        NestedScanGuard guard = new NestedScanGuard();
        assertTrue(guard.enter(), "held on the test thread");

        AtomicBoolean otherThreadEntered = new AtomicBoolean();
        CountDownLatch done = new CountDownLatch(1);
        Thread other = new Thread(() -> {
            otherThreadEntered.set(guard.enter());
            done.countDown();
        });
        other.start();
        assertTrue(done.await(5, TimeUnit.SECONDS), "the other thread must not block on our latch");

        assertTrue(otherThreadEntered.get(), "another thread is unaffected by ours being held");
        assertTrue(guard.isInside(), "and ours is still held");
    }
}
