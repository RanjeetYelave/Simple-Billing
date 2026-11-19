package com.billing.simple.billsoft.service.scheduler;

import com.billing.simple.billsoft.licensing.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class GitCheckSchedulerServiceTest {

    @TempDir
    File tempDir;

    private File stateFile;
    private File lockFile;
    private MutableTestClock testClock;
    private GitCheckSchedulerService scheduler;

    public static class MutableTestClock extends Clock {
        private Instant currentInstant;
        private final ZoneOffset zone = ZoneOffset.UTC;

        public MutableTestClock(Instant start) {
            this.currentInstant = start;
        }

        public void advance(Duration duration) {
            this.currentInstant = this.currentInstant.plus(duration);
        }

        public void setInstant(Instant instant) {
            this.currentInstant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return zone;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }

    @BeforeEach
    void setUp() {
        stateFile = new File(tempDir, "git_scheduler_state.json");
        lockFile = new File(tempDir, "git_scheduler.lock");
        testClock = new MutableTestClock(Instant.parse("2026-11-19T12:00:00Z"));
        scheduler = new GitCheckSchedulerService(testClock, stateFile, lockFile);
    }

    @AfterEach
    void tearDown() {
        scheduler.stop();
    }

    @Test
    void testInitialState_AllChecksDueImmediately() {
        for (CheckType type : CheckType.values()) {
            assertTrue(scheduler.isCheckDue(type), "Check " + type + " should be due initially when no prior check exists");
        }
    }

    @Test
    void testExecutionSucceeds_UpdatesStateAndEnforcesInterval() {
        AtomicInteger callCount = new AtomicInteger(0);
        scheduler.registerHandler(CheckType.ANNOUNCEMENTS, (type, etag) -> {
            callCount.incrementAndGet();
            return GitCheckExecutionResult.success("etag-ann-123");
        });

        GitCheckExecutionResult result = scheduler.triggerCheck(CheckType.ANNOUNCEMENTS, false);
        assertTrue(result.isSuccess());
        assertEquals(1, callCount.get());

        // Check is no longer due immediately after successful run
        assertFalse(scheduler.isCheckDue(CheckType.ANNOUNCEMENTS));

        GitCheckState state = scheduler.getCheckState(CheckType.ANNOUNCEMENTS);
        assertEquals(testClock.instant(), state.getLastSuccessAt());
        assertEquals("etag-ann-123", state.getEtag());
        assertEquals(0, state.getConsecutiveFailures());

        // Advance 23 hours: announcements interval is 24h, so it should still NOT be due
        testClock.advance(Duration.ofHours(23));
        assertFalse(scheduler.isCheckDue(CheckType.ANNOUNCEMENTS), "Should not be due at 23 hours");

        // Advance 1 more hour (24 hours total): now it should be due
        testClock.advance(Duration.ofHours(1));
        assertTrue(scheduler.isCheckDue(CheckType.ANNOUNCEMENTS), "Should be due at 24 hours");
    }

    @Test
    void testLifetimeLicense_Enforces30DaysInterval() {
        LicensePayload lifetimeLic = new LicensePayload();
        lifetimeLic.setPlan(MembershipPlan.GOLD);
        lifetimeLic.setStatus(LicenseStatus.ACTIVE);
        scheduler.setLicenseSupplier(() -> lifetimeLic);

        Duration interval = scheduler.calculateLicenseInterval(lifetimeLic);
        assertEquals(Duration.ofDays(30), interval, "Lifetime license must check every 30 days");

        AtomicInteger callCount = new AtomicInteger(0);
        scheduler.registerHandler(CheckType.LICENSE, (type, etag) -> {
            callCount.incrementAndGet();
            return GitCheckExecutionResult.success("etag-lic-lifetime");
        });

        scheduler.triggerCheck(CheckType.LICENSE, false);
        assertEquals(1, callCount.get());

        // Day 29: not due
        testClock.advance(Duration.ofDays(29));
        assertFalse(scheduler.isCheckDue(CheckType.LICENSE), "Lifetime license should NOT be due at 29 days");

        // Day 30: due
        testClock.advance(Duration.ofDays(1));
        assertTrue(scheduler.isCheckDue(CheckType.LICENSE), "Lifetime license MUST be due at 30 days");
    }

    @Test
    void testSubscriptionLicense_Enforces15DaysInterval() {
        LicensePayload subLic = new LicensePayload();
        subLic.setPlan(MembershipPlan.SILVER);
        subLic.setStatus(LicenseStatus.ACTIVE);
        // Expiry far in future (60 days)
        subLic.setExpiresAt(testClock.instant().plus(Duration.ofDays(60)));

        Duration interval = scheduler.calculateLicenseInterval(subLic);
        assertEquals(Duration.ofDays(15), interval, "Normal subscription license must check every 15 days");
    }

    @Test
    void testSubscriptionLicense_ApproachingExpiry_ShortensTo1Day() {
        LicensePayload subLic = new LicensePayload();
        subLic.setPlan(MembershipPlan.SILVER);
        subLic.setStatus(LicenseStatus.ACTIVE);
        // Expiry approaching: 5 days from now (<= 7 days)
        subLic.setExpiresAt(testClock.instant().plus(Duration.ofDays(5)));

        Duration interval = scheduler.calculateLicenseInterval(subLic);
        assertEquals(Duration.ofDays(1), interval, "Approaching expiry must shorten check interval to 1 day");
    }

    @Test
    void testSubscriptionLicense_OverdueOrApproachingEmi_ShortensTo1Day() {
        LicensePayload subLic = new LicensePayload();
        subLic.setPlan(MembershipPlan.SILVER);
        subLic.setStatus(LicenseStatus.ACTIVE);
        subLic.setExpiresAt(testClock.instant().plus(Duration.ofDays(60)));

        EmiPayload emi = new EmiPayload();
        emi.setEnabled(true);
        EmiStatePayload state = new EmiStatePayload();
        state.setEmiStatus("OVERDUE");
        state.setAccessStatus("NORMAL");
        emi.setState(state);
        subLic.setEmi(emi);

        Duration interval = scheduler.calculateLicenseInterval(subLic);
        assertEquals(Duration.ofDays(1), interval, "Overdue EMI must shorten check interval to 1 day");
    }

    @Test
    void testAppUpdates_Enforces72HoursInterval() {
        Duration interval = scheduler.calculateInterval(CheckType.APP_UPDATES);
        assertEquals(Duration.ofHours(72), interval, "App updates interval must be 72 hours");

        scheduler.registerHandler(CheckType.APP_UPDATES, (type, etag) -> GitCheckExecutionResult.success("update-manifest-etag"));
        scheduler.triggerCheck(CheckType.APP_UPDATES, false);

        assertFalse(scheduler.isCheckDue(CheckType.APP_UPDATES));

        // 71 hours later: still not due
        testClock.advance(Duration.ofHours(71));
        assertFalse(scheduler.isCheckDue(CheckType.APP_UPDATES), "Updates should not be due at 71 hours");

        // 72 hours later: due
        testClock.advance(Duration.ofHours(1));
        assertTrue(scheduler.isCheckDue(CheckType.APP_UPDATES), "Updates must be due at 72 hours");
    }

    @Test
    void testDataProtection_Enforces24HoursInterval() {
        Duration interval = scheduler.calculateInterval(CheckType.DATA_PROTECTION);
        assertEquals(Duration.ofHours(24), interval, "Data protection interval must be 24 hours");

        scheduler.registerHandler(CheckType.DATA_PROTECTION, (type, etag) -> GitCheckExecutionResult.success(null));
        scheduler.triggerCheck(CheckType.DATA_PROTECTION, false);

        assertFalse(scheduler.isCheckDue(CheckType.DATA_PROTECTION));

        testClock.advance(Duration.ofHours(23));
        assertFalse(scheduler.isCheckDue(CheckType.DATA_PROTECTION));

        testClock.advance(Duration.ofHours(1));
        assertTrue(scheduler.isCheckDue(CheckType.DATA_PROTECTION));
    }

    @Test
    void testPersistenceAcrossRestarts_ZeroOutboundCallsOnStartupWhenNotDue() {
        scheduler.registerHandler(CheckType.ANNOUNCEMENTS, (type, etag) -> GitCheckExecutionResult.success("etag-ann"));
        scheduler.triggerCheck(CheckType.ANNOUNCEMENTS, false);

        assertTrue(stateFile.exists(), "State file must exist on disk");

        // Simulate app restart by stopping current scheduler and starting a new one pointing to the same files
        scheduler.stop();
        testClock.advance(Duration.ofMinutes(10)); // App restarted 10 minutes later

        AtomicInteger restartCallCount = new AtomicInteger(0);
        GitCheckSchedulerService restartedScheduler = new GitCheckSchedulerService(testClock, stateFile, lockFile);
        restartedScheduler.registerHandler(CheckType.ANNOUNCEMENTS, (type, etag) -> {
            restartCallCount.incrementAndGet();
            return GitCheckExecutionResult.success("etag-ann");
        });

        // Verify that on restart, check is NOT due and zero network calls are made
        assertFalse(restartedScheduler.isCheckDue(CheckType.ANNOUNCEMENTS), "Check must not be due after restart within interval");
        restartedScheduler.triggerCheck(CheckType.ANNOUNCEMENTS, false);
        assertEquals(0, restartCallCount.get(), "Zero network calls must be made on startup when check is not due");

        restartedScheduler.stop();
    }

    @Test
    void testNetworkFailure_AppliesBoundedExponentialBackoff() {
        AtomicInteger failCount = new AtomicInteger(0);
        scheduler.registerHandler(CheckType.ANNOUNCEMENTS, (type, etag) -> {
            failCount.incrementAndGet();
            return GitCheckExecutionResult.networkError("Connect timed out");
        });

        // Failure 1: backoff is 1h
        Instant t0 = testClock.instant();
        scheduler.triggerCheck(CheckType.ANNOUNCEMENTS, false);
        GitCheckState state = scheduler.getCheckState(CheckType.ANNOUNCEMENTS);
        assertEquals(1, state.getConsecutiveFailures());
        assertEquals(t0.plus(Duration.ofHours(1)), state.getNextScheduledAt());

        // Advance 30 mins: not due yet
        testClock.advance(Duration.ofMinutes(30));
        assertFalse(scheduler.isCheckDue(CheckType.ANNOUNCEMENTS));

        // Advance 30 mins (1h total): now due for retry
        testClock.advance(Duration.ofMinutes(30));
        assertTrue(scheduler.isCheckDue(CheckType.ANNOUNCEMENTS));

        // Failure 2: backoff is 2h
        Instant t1 = testClock.instant();
        scheduler.triggerCheck(CheckType.ANNOUNCEMENTS, false);
        state = scheduler.getCheckState(CheckType.ANNOUNCEMENTS);
        assertEquals(2, state.getConsecutiveFailures());
        assertEquals(t1.plus(Duration.ofHours(2)), state.getNextScheduledAt());

        // Failure 3: backoff is 4h
        testClock.advance(Duration.ofHours(2));
        Instant t2 = testClock.instant();
        scheduler.triggerCheck(CheckType.ANNOUNCEMENTS, false);
        state = scheduler.getCheckState(CheckType.ANNOUNCEMENTS);
        assertEquals(3, state.getConsecutiveFailures());
        assertEquals(t2.plus(Duration.ofHours(4)), state.getNextScheduledAt());
    }

    @Test
    void testNotModified304_ConfirmsSuccessWithoutAlteringEtag() {
        scheduler.registerHandler(CheckType.ANNOUNCEMENTS, (type, etag) -> GitCheckExecutionResult.success("etag-initial"));
        scheduler.triggerCheck(CheckType.ANNOUNCEMENTS, false);

        testClock.advance(Duration.ofHours(24));
        assertTrue(scheduler.isCheckDue(CheckType.ANNOUNCEMENTS));

        scheduler.registerHandler(CheckType.ANNOUNCEMENTS, (type, etag) -> {
            assertEquals("etag-initial", etag, "Handler must receive current ETag");
            return GitCheckExecutionResult.notModified("etag-initial");
        });

        GitCheckExecutionResult res = scheduler.triggerCheck(CheckType.ANNOUNCEMENTS, false);
        assertTrue(res.isSuccess());
        assertTrue(res.isNotModified());

        GitCheckState state = scheduler.getCheckState(CheckType.ANNOUNCEMENTS);
        assertEquals("NOT_MODIFIED", state.getLastResult());
        assertEquals(0, state.getConsecutiveFailures());
        assertEquals("etag-initial", state.getEtag());
        assertFalse(scheduler.isCheckDue(CheckType.ANNOUNCEMENTS));
    }

    @Test
    void testManualRefresh_DebouncingProtectsAgainstDuplicateRequests() {
        AtomicInteger callCount = new AtomicInteger(0);
        scheduler.registerHandler(CheckType.ANNOUNCEMENTS, (type, etag) -> {
            callCount.incrementAndGet();
            return GitCheckExecutionResult.success("etag-1");
        });

        // First manual refresh
        GitCheckExecutionResult res1 = scheduler.triggerCheck(CheckType.ANNOUNCEMENTS, true);
        assertTrue(res1.isSuccess());
        assertEquals(1, callCount.get());

        // Immediate second manual refresh (within 30 seconds): debounced!
        testClock.advance(Duration.ofSeconds(5));
        GitCheckExecutionResult res2 = scheduler.triggerCheck(CheckType.ANNOUNCEMENTS, true);
        assertEquals("DEBOUNCED", res2.getResultStatus());
        assertEquals(1, callCount.get(), "Debounced refresh must NOT invoke handler");

        // Advance 31 seconds: now manual refresh executes
        testClock.advance(Duration.ofSeconds(30));
        GitCheckExecutionResult res3 = scheduler.triggerCheck(CheckType.ANNOUNCEMENTS, true);
        assertEquals(2, callCount.get());
    }

    @Test
    void testClockRollbackDetection_ReconcilesWithoutBurstRequests() {
        scheduler.registerHandler(CheckType.ANNOUNCEMENTS, (type, etag) -> GitCheckExecutionResult.success("etag-1"));
        scheduler.triggerCheck(CheckType.ANNOUNCEMENTS, false);

        // Advance 5 hours, then clock jumps backwards by 2 hours
        testClock.advance(Duration.ofHours(5));
        Instant forwardTime = testClock.instant();
        scheduler.triggerCheck(CheckType.ANNOUNCEMENTS, true); // save state at forwardTime

        // Now roll clock back by 3 hours
        testClock.setInstant(forwardTime.minus(Duration.ofHours(3)));

        // isCheckDue should safely handle clock rollback without panic
        boolean due = scheduler.isCheckDue(CheckType.ANNOUNCEMENTS);
        assertFalse(due, "Clock rollback must not trigger false positive due status");
    }

    @Test
    void testInterProcessLockCoordination_BlocksConcurrentExecution() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        scheduler.registerHandler(CheckType.ANNOUNCEMENTS, (type, etag) -> {
            callCount.incrementAndGet();
            return GitCheckExecutionResult.success("etag-test");
        });

        // External process holds lock on lockFile
        try (RandomAccessFile extRaf = new RandomAccessFile(lockFile, "rw");
             FileChannel extChannel = extRaf.getChannel();
             FileLock extLock = extChannel.tryLock()) {

            assertNotNull(extLock, "External process acquired lock");

            // Attempt check while lock is held by another process
            GitCheckExecutionResult result = scheduler.triggerCheck(CheckType.ANNOUNCEMENTS, false);
            assertEquals("LOCK_HELD", result.getResultStatus());
            assertEquals(0, callCount.get(), "Handler must not run when lock is held by another process");
        }

        // Lock released: now check can execute
        GitCheckExecutionResult result2 = scheduler.triggerCheck(CheckType.ANNOUNCEMENTS, false);
        assertTrue(result2.isSuccess());
        assertEquals(1, callCount.get());
    }
}
