package es.boffmedia.teras.dungeon.instance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The two ways a run stops being a run without anyone telling it so.
 *
 * <p>Both were real leaks: a party that disconnected left an ACTIVE run holding a slot and a built
 * floor nobody could ever end, and a build job that died left a run BUILDING forever, which
 * {@code end()} refuses to touch. The grace period is the part worth pinning — a run must survive a
 * brief drop, and must not survive a permanent one.</p>
 */
class RunWatchdogTest {

    private static final long GRACE = 100;
    private static final long BUILD_TIMEOUT = 600;

    @Test
    void anOnlinePartyIsHealthy() {
        RunWatchdog watchdog = new RunWatchdog(GRACE, BUILD_TIMEOUT);
        assertEquals(RunWatchdog.Verdict.HEALTHY, watchdog.check(1, false, true, 0));
        assertEquals(RunWatchdog.Verdict.HEALTHY, watchdog.check(1, false, true, 10_000));
    }

    @Test
    void anEmptyPartyIsEndedOnlyAfterTheGracePeriod() {
        RunWatchdog watchdog = new RunWatchdog(GRACE, BUILD_TIMEOUT);
        assertEquals(RunWatchdog.Verdict.HEALTHY, watchdog.check(1, false, false, 0));
        assertEquals(RunWatchdog.Verdict.HEALTHY, watchdog.check(1, false, false, GRACE - 1));
        assertEquals(RunWatchdog.Verdict.END_DESERTED, watchdog.check(1, false, false, GRACE));
    }

    @Test
    void comingBackWithinTheGracePeriodResetsTheTimer() {
        RunWatchdog watchdog = new RunWatchdog(GRACE, BUILD_TIMEOUT);
        watchdog.check(1, false, false, 0);
        watchdog.check(1, false, true, GRACE - 1);
        // Without the reset this tick would end a run whose party is standing in it.
        assertEquals(RunWatchdog.Verdict.HEALTHY, watchdog.check(1, false, false, GRACE));
        assertEquals(RunWatchdog.Verdict.HEALTHY, watchdog.check(1, false, false, 2 * GRACE - 1));
        // The clock restarted when they came back, so the full grace is counted from tick GRACE.
        assertEquals(RunWatchdog.Verdict.END_DESERTED, watchdog.check(1, false, false, 2 * GRACE));
    }

    @Test
    void runsAreCountedSeparately() {
        RunWatchdog watchdog = new RunWatchdog(GRACE, BUILD_TIMEOUT);
        watchdog.check(1, false, false, 0);
        assertEquals(RunWatchdog.Verdict.HEALTHY, watchdog.check(2, false, false, GRACE));
        assertEquals(RunWatchdog.Verdict.END_DESERTED, watchdog.check(1, false, false, GRACE));
    }

    @Test
    void aBuildThatNeverFinishesIsFailed() {
        RunWatchdog watchdog = new RunWatchdog(GRACE, BUILD_TIMEOUT);
        assertEquals(RunWatchdog.Verdict.HEALTHY, watchdog.check(1, true, false, 0));
        assertEquals(RunWatchdog.Verdict.HEALTHY, watchdog.check(1, true, false, BUILD_TIMEOUT - 1));
        assertEquals(RunWatchdog.Verdict.FAIL_STUCK, watchdog.check(1, true, false, BUILD_TIMEOUT));
    }

    @Test
    void buildingIsNotDesertionCheckedEvenWithNobodyOnline() {
        RunWatchdog watchdog = new RunWatchdog(GRACE, BUILD_TIMEOUT);
        // Well past the desertion grace, well short of the build timeout: an empty party during a
        // build is the completion callback's business, not this one's.
        assertEquals(RunWatchdog.Verdict.HEALTHY, watchdog.check(1, true, false, GRACE * 3));
    }

    @Test
    void aStageAdvanceDoesNotInheritTheOldBuildTimer() {
        RunWatchdog watchdog = new RunWatchdog(GRACE, BUILD_TIMEOUT);
        watchdog.check(1, true, false, 0);
        watchdog.check(1, false, true, 100);
        // Descending puts the run back into BUILDING; the timer must start again from here, or a
        // long run would fail its own stage advance the moment it began one.
        assertEquals(RunWatchdog.Verdict.HEALTHY, watchdog.check(1, true, true, 5_000));
        assertEquals(RunWatchdog.Verdict.FAIL_STUCK,
                watchdog.check(1, true, true, 5_000 + BUILD_TIMEOUT));
    }

    @Test
    void forgettingClearsBothTimers() {
        RunWatchdog watchdog = new RunWatchdog(GRACE, BUILD_TIMEOUT);
        watchdog.check(1, false, false, 0);
        watchdog.forget(1);
        assertEquals(RunWatchdog.Verdict.HEALTHY, watchdog.check(1, false, false, GRACE));
        assertEquals(-1, watchdog.desertedFor(2, 0));
    }
}
