package es.boffmedia.teras.battle;

/**
 * A per-thread latch that stops the backpack scanner from descending into a backpack inside a backpack.
 *
 * <p>Pixelmon's {@code BattleItemScanner.checkInventory} re-enters <b>every</b> registered scanner for
 * each stack it walks. The backpack scanner calls {@code checkInventory} on a backpack's contents, so
 * a nested backpack lands straight back in the scanner that is already running — unbounded, and a cycle
 * (which Sophisticated's UUID-keyed global content store makes representable, not merely theoretical)
 * would be a {@code StackOverflowError} the first time a player opens the battle bag.</p>
 *
 * <p>Stopping at one level is also what makes the scanner <i>consistent</i>. Reporting an item is one
 * callback and retrieving it is another: the find/consume side reads a single backpack's slots and
 * cannot reach a nested one. Letting the scan descend where find and consume cannot would advertise
 * items to the battle bag that then fail to be used. So the rule is one level deep on all three paths —
 * a battle item must be in a backpack you carry, not in a backpack in a backpack.</p>
 *
 * <p>Kept free of Minecraft types so it can be unit-tested; the latch is the part worth testing, since
 * a leaked one silently disables the scanner for the rest of that thread's life.</p>
 */
public final class NestedScanGuard {

    private final ThreadLocal<Boolean> inside = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * Claims the latch.
     *
     * @return {@code true} if this thread was outside and may now scan; {@code false} if a scan is
     *         already in progress on this thread, meaning the caller is a nested re-entry and must not
     *         descend. Every {@code true} must be paired with {@link #exit()} in a {@code finally}.
     */
    public boolean enter() {
        if (inside.get()) {
            return false;
        }
        inside.set(Boolean.TRUE);
        return true;
    }

    /** Releases the latch. Removes rather than clears, so the scan leaves nothing on a pooled thread. */
    public void exit() {
        inside.remove();
    }

    /** Whether a scan is in progress on this thread. */
    public boolean isInside() {
        return inside.get();
    }
}
