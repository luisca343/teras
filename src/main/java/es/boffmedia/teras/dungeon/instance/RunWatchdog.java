package es.boffmedia.teras.dungeon.instance;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Notices runs that have stopped being runs.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Every deliberate way out of a dungeon tore it down properly — walking out, dying out, finishing,
 * an admin ending it. What none of them covered was a run that simply <b>stopped having anyone in
 * it</b>. Nothing listened for a disconnect, so a party that lost its connection, was kicked, or shut
 * the game down left an ACTIVE run holding a slot, a built floor, its shop displays and its reward
 * pedestals, with no player left who could ever end it. On a server that restarts nightly the boot
 * sweep hid this; on one that stays up for weeks it is a slow leak of both slots and geometry.</p>
 *
 * <p>The second case is a build that never finishes. A run is registered and journalled <i>before</i>
 * the first block, and only becomes ACTIVE in the build job's completion callback. If that job dies
 * — a broken template, a full disk, anything the materializer logs and drops — the run stays BUILDING
 * forever: {@code end()} refuses it (it only ends ACTIVE runs), so the slot is never freed and the
 * half-built floor is never cleared.</p>
 *
 * <h2>Polling, not events</h2>
 *
 * <p>A logout listener would catch the common case and miss the rest. Asking "is anyone from this
 * party online?" once a second catches disconnects, kicks, client crashes, a player moved to another
 * server, and every future way a player can leave without asking — including ones that fire no event
 * at all. The check is the state itself rather than a notification about it, which is what makes it
 * hard to leak past.</p>
 *
 * <p>Both timers are held here rather than on the run, so the rule is one Minecraft-free class with
 * tests. The caller supplies the observations; this decides.</p>
 */
public final class RunWatchdog {

    /** What to do about a run this tick. */
    public enum Verdict {
        /** Nothing — either healthy, or inside its grace period. */
        HEALTHY,
        /** Nobody has been online for the grace period: end it and sweep the floor. */
        END_DESERTED,
        /** It never finished building: clear whatever was written and free the slot. */
        FAIL_STUCK
    }

    private final long graceTicks;
    private final long buildTimeoutTicks;
    private final Map<Integer, Long> desertedSince = new LinkedHashMap<>();
    private final Map<Integer, Long> buildingSince = new LinkedHashMap<>();

    public RunWatchdog(long graceTicks, long buildTimeoutTicks) {
        this.graceTicks = graceTicks;
        this.buildTimeoutTicks = buildTimeoutTicks;
    }

    /**
     * One observation of one run.
     *
     * @param building  whether it is still waiting on a build job (a stage advance counts: the party
     *                  is on the old pad with the new one being written)
     * @param anyOnline whether any party member is connected right now
     * @param tick      the server tick this observation was made on
     */
    public Verdict check(int runId, boolean building, boolean anyOnline, long tick) {
        if (building) {
            // Deliberately not desertion-checked: a build with an empty party is already folded by
            // the completion callback, and a build that never completes is this timer's business.
            desertedSince.remove(runId);
            long since = buildingSince.computeIfAbsent(runId, k -> tick);
            return tick - since >= buildTimeoutTicks ? Verdict.FAIL_STUCK : Verdict.HEALTHY;
        }
        buildingSince.remove(runId);
        if (anyOnline) {
            desertedSince.remove(runId);
            return Verdict.HEALTHY;
        }
        long since = desertedSince.computeIfAbsent(runId, k -> tick);
        return tick - since >= graceTicks ? Verdict.END_DESERTED : Verdict.HEALTHY;
    }

    /** Drops a run's timers. Called when it ends, however it ended. */
    public void forget(int runId) {
        desertedSince.remove(runId);
        buildingSince.remove(runId);
    }

    /** How long {@code runId} has been empty, in ticks; -1 when it is not being counted. */
    public long desertedFor(int runId, long tick) {
        Long since = desertedSince.get(runId);
        return since == null ? -1 : tick - since;
    }

    public void clear() {
        desertedSince.clear();
        buildingSince.clear();
    }
}
