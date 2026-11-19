package com.billing.simple.billsoft.service.scheduler;

import com.billing.simple.billsoft.licensing.LicenseCoordinator;
import com.billing.simple.billsoft.licensing.model.LicensePayload;
import com.billing.simple.billsoft.licensing.model.MembershipPlan;
import com.billing.simple.billsoft.licensing.model.ValidationResult;
import com.billing.simple.billsoft.util.DataDirectoryResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Enterprise centralized persistent Git-check scheduler for RupeeCRM.
 * Coordinates all GitHub/Git remote checks (License, Announcements, Data Protection, Updates)
 * into a single unified persistent scheduler that survives application restarts and OS reboots.
 *
 * Enforces strict checking intervals:
 * - Standard subscription license: Every 15 days (daily when expiry or EMI is approaching/due <= 7 days)
 * - Lifetime license (GOLD): Every 30 days
 * - Announcements: Once every 24 hours
 * - Data Protection: Once every 24 hours
 * - Application updates: Once every 72 hours
 *
 * Guarantees:
 * - Zero redundant GitHub API calls on application launch, page reload, or navigation
 * - Atomic persistence in git_scheduler_state.json
 * - File-level locking via git_scheduler.lock to coordinate across multiple Electron windows
 * - In-flight mutex per check type and manual refresh debouncing
 * - Clock rollback detection and resilience
 * - Bounded exponential backoff on network failures (1h, 2h, 4h... max 24h)
 * - ETag / If-None-Match conditional request tracking and 304 Not Modified handling
 */
@Service
public class GitCheckSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(GitCheckSchedulerService.class);

    public static final String STATE_FILE_NAME = "git_scheduler_state.json";
    public static final String LOCK_FILE_NAME = "git_scheduler.lock";

    // Standard intervals
    public static final Duration INTERVAL_LIFETIME_LICENSE = Duration.ofDays(30);
    public static final Duration INTERVAL_SUBSCRIPTION_LICENSE = Duration.ofDays(15);
    public static final Duration INTERVAL_APPROACHING_OR_OVERDUE = Duration.ofDays(1);
    public static final Duration INTERVAL_ANNOUNCEMENTS = Duration.ofHours(24);
    public static final Duration INTERVAL_DATA_PROTECTION = Duration.ofHours(24);
    public static final Duration INTERVAL_APP_UPDATES = Duration.ofHours(72);

    // Debounce duration for manual refresh
    public static final Duration MANUAL_REFRESH_DEBOUNCE = Duration.ofSeconds(30);

    private final Clock clock;
    private final File stateFile;
    private final File lockFile;
    private final ObjectMapper mapper;
    private final ScheduledExecutorService scheduler;

    private final Map<CheckType, GitCheckHandler> handlers = new ConcurrentHashMap<>();
    private final Map<CheckType, AtomicBoolean> inFlightLocks = new EnumMap<>(CheckType.class);
    private final Map<CheckType, Instant> lastManualRefreshTimes = new ConcurrentHashMap<>();

    private GitSchedulerState currentState;
    private java.util.function.Supplier<LicensePayload> licenseSupplier;

    @Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private LicenseCoordinator licenseCoordinator;

    @Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private com.billing.simple.billsoft.service.AnnouncementService announcementService;

    @Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private com.billing.simple.billsoft.dataprotection.DataProtectionService dataProtectionService;

    @Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private com.billing.simple.billsoft.service.UpdateService updateService;

    public GitCheckSchedulerService() {
        this(Clock.systemUTC(),
             new File(DataDirectoryResolver.resolveDataDirectory(), STATE_FILE_NAME),
             new File(DataDirectoryResolver.resolveDataDirectory(), LOCK_FILE_NAME));
    }

    public GitCheckSchedulerService(Clock clock, File stateFile, File lockFile) {
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.stateFile = stateFile;
        this.lockFile = lockFile;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);

        for (CheckType type : CheckType.values()) {
            inFlightLocks.put(type, new AtomicBoolean(false));
        }

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rupeecrm-git-scheduler");
            t.setDaemon(true);
            return t;
        });

        loadState();
    }

    @PostConstruct
    public void start() {
        // Register default LicenseCoordinator handler if available
        if (licenseCoordinator != null && !handlers.containsKey(CheckType.LICENSE)) {
            registerHandler(CheckType.LICENSE, (type, currentEtag) -> {
                var fetchResult = licenseCoordinator.syncWithRegistryDetailed(true, currentEtag);
                if (fetchResult == null) {
                    return GitCheckExecutionResult.networkError("Null response from license coordinator");
                }
                if (fetchResult.isNotModified()) {
                    return GitCheckExecutionResult.notModified(fetchResult.getEtag());
                }
                if (fetchResult.isSuccess()) {
                    return GitCheckExecutionResult.success(fetchResult.getEtag());
                }
                if (fetchResult.getHttpStatusCode() == 403 || fetchResult.getHttpStatusCode() == 429) {
                    return GitCheckExecutionResult.rateLimited("GitHub API rate limited", Instant.now(clock).plus(Duration.ofHours(1)));
                }
                return GitCheckExecutionResult.networkError("Fetch failed: " + fetchResult.getClassification());
            });
        }

        if (announcementService != null && !handlers.containsKey(CheckType.ANNOUNCEMENTS)) {
            announcementService.setSchedulerService(this);
            registerHandler(CheckType.ANNOUNCEMENTS, (type, currentEtag) -> announcementService.refreshFromRemote(currentEtag));
        }

        if (dataProtectionService != null && !handlers.containsKey(CheckType.DATA_PROTECTION)) {
            dataProtectionService.setSchedulerService(this);
            registerHandler(CheckType.DATA_PROTECTION, (type, currentEtag) -> {
                dataProtectionService.performScheduledBackupCheck();
                return GitCheckExecutionResult.success(null);
            });
        }

        if (updateService != null && !handlers.containsKey(CheckType.APP_UPDATES)) {
            updateService.setSchedulerService(this);
            registerHandler(CheckType.APP_UPDATES, (type, currentEtag) -> {
                var resp = updateService.checkUpdate(true);
                if (resp != null) {
                    return GitCheckExecutionResult.success(null);
                }
                return GitCheckExecutionResult.networkError("Update check returned empty");
            });
        }

        // Schedule periodic evaluation every 15 minutes (initial delay 5 seconds)
        scheduler.scheduleWithFixedDelay(this::evaluateDueChecks, 5, 900, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
    }

    public void registerHandler(CheckType type, GitCheckHandler handler) {
        if (handler != null) {
            handlers.put(type, handler);
        }
    }

    public Clock getClock() {
        return clock;
    }

    public synchronized GitSchedulerState getCurrentState() {
        return currentState;
    }

    public synchronized GitCheckState getCheckState(CheckType type) {
        return currentState.getCheckState(type);
    }

    public Map<CheckType, GitCheckState> getAllStates() {
        synchronized (this) {
            return Collections.unmodifiableMap(new EnumMap<>(currentState.getChecks()));
        }
    }

    /**
     * Determines whether a remote check of the given type is currently due.
     * Takes zero network latency and consults only durable local schedule state.
     */
    public boolean isCheckDue(CheckType type) {
        Instant now = Instant.now(clock);
        synchronized (this) {
            checkAndHandleClockRollback(now);
            GitCheckState state = currentState.getCheckState(type);
            return state.isDue(now);
        }
    }

    /**
     * Evaluates all check types and triggers execution for any checks that are currently due.
     */
    public void evaluateDueChecks() {
        for (CheckType type : CheckType.values()) {
            if (isCheckDue(type)) {
                triggerCheck(type, false);
            }
        }
    }

    /**
     * Triggers a check for the given type.
     * If force is false and check is not due, returns cached success immediately without network activity.
     * If force is true, applies debounce protection (minimum 30 seconds between manual refreshes).
     */
    public GitCheckExecutionResult triggerCheck(CheckType type, boolean force) {
        Instant now = Instant.now(clock);

        if (!force && !isCheckDue(type)) {
            // Not due yet: return cached status
            synchronized (this) {
                GitCheckState state = currentState.getCheckState(type);
                return new GitCheckExecutionResult(true, true, state.getEtag(), state.getLastResult(), null, null);
            }
        }

        // Debounce manual forced refresh
        if (force) {
            Instant lastManual = lastManualRefreshTimes.get(type);
            if (lastManual != null && Duration.between(lastManual, now).compareTo(MANUAL_REFRESH_DEBOUNCE) < 0) {
                log.debug("Manual refresh debounced for {}. Too soon since last refresh attempt.", type);
                synchronized (this) {
                    GitCheckState state = currentState.getCheckState(type);
                    return new GitCheckExecutionResult(true, true, state.getEtag(), "DEBOUNCED", null, null);
                }
            }
            lastManualRefreshTimes.put(type, now);
        }

        // In-flight process-level lock
        AtomicBoolean inFlight = inFlightLocks.get(type);
        if (inFlight != null && !inFlight.compareAndSet(false, true)) {
            log.debug("Check {} already executing in-flight. Skipping duplicate concurrent request.", type);
            synchronized (this) {
                GitCheckState state = currentState.getCheckState(type);
                return new GitCheckExecutionResult(true, true, state.getEtag(), "IN_FLIGHT", null, null);
            }
        }

        try {
            // OS-level inter-process lock (for multi-window Electron synchronization)
            return executeWithInterProcessLock(type, force, now);
        } finally {
            if (inFlight != null) {
                inFlight.set(false);
            }
        }
    }

    private GitCheckExecutionResult executeWithInterProcessLock(CheckType type, boolean force, Instant now) {
        if (lockFile == null) {
            // Direct execution fallback if lockFile is null (e.g. lightweight unit tests)
            GitCheckHandler handler = handlers.get(type);
            if (handler != null) {
                try {
                    String currentEtag = currentState.getCheckState(type).getEtag();
                    GitCheckExecutionResult res = handler.execute(type, currentEtag);
                    applyCheckResult(type, res, now);
                    return res;
                } catch (Exception ex) {
                    GitCheckExecutionResult res = GitCheckExecutionResult.networkError(ex.getMessage());
                    applyCheckResult(type, res, now);
                    return res;
                }
            }
            return GitCheckExecutionResult.failure("Handler missing: " + type);
        }

        File parent = lockFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (RandomAccessFile raf = new RandomAccessFile(lockFile, "rw");
             FileChannel channel = raf.getChannel()) {

            FileLock lock = null;
            try {
                lock = channel.tryLock();
            } catch (java.nio.channels.OverlappingFileLockException ofle) {
                log.debug("Scheduler lock is held by another thread/process. Skipping execution of {}.", type);
                synchronized (this) {
                    GitCheckState state = currentState.getCheckState(type);
                    return new GitCheckExecutionResult(true, true, state.getEtag(), "LOCK_HELD", null, null);
                }
            }

            if (lock == null) {
                log.debug("Another process or Electron window holds scheduler lock. Skipping execution of {}.", type);
                synchronized (this) {
                    GitCheckState state = currentState.getCheckState(type);
                    return new GitCheckExecutionResult(true, true, state.getEtag(), "LOCK_HELD", null, null);
                }
            }

            try {
                // Reload state inside lock in case another process just updated it
                loadState();

                if (!force && !currentState.getCheckState(type).isDue(now)) {
                    log.debug("Check {} was already refreshed by another process.", type);
                    GitCheckState state = currentState.getCheckState(type);
                    return new GitCheckExecutionResult(true, true, state.getEtag(), state.getLastResult(), null, null);
                }

                GitCheckHandler handler = handlers.get(type);
                if (handler == null) {
                    log.warn("No handler registered for check type {}. Marking as deferred.", type);
                    return GitCheckExecutionResult.failure("No handler registered for " + type);
                }

                String currentEtag = currentState.getCheckState(type).getEtag();

                // Update attempt timestamp
                synchronized (this) {
                    GitCheckState state = currentState.getCheckState(type);
                    state.setLastAttemptAt(now);
                    saveState();
                }

                GitCheckExecutionResult result;
                try {
                    result = handler.execute(type, currentEtag);
                } catch (Exception ex) {
                    log.warn("Error executing check for {}: {}", type, ex.getMessage());
                    result = GitCheckExecutionResult.networkError(ex.getMessage());
                }

                // Process result and calculate next interval
                applyCheckResult(type, result, now);
                return result;
            } finally {
                try {
                    lock.release();
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.warn("Could not acquire file lock for scheduler: {}", e.getMessage());
            // Proceed with execution even if file lock fails (fallback for restricted environments)
            GitCheckHandler handler = handlers.get(type);
            if (handler != null) {
                try {
                    String currentEtag = currentState.getCheckState(type).getEtag();
                    GitCheckExecutionResult res = handler.execute(type, currentEtag);
                    applyCheckResult(type, res, now);
                    return res;
                } catch (Exception ex) {
                    GitCheckExecutionResult res = GitCheckExecutionResult.networkError(ex.getMessage());
                    applyCheckResult(type, res, now);
                    return res;
                }
            }
            return GitCheckExecutionResult.failure("Handler missing: " + type);
        }
    }

    private synchronized void applyCheckResult(CheckType type, GitCheckExecutionResult result, Instant now) {
        GitCheckState state = currentState.getCheckState(type);
        state.setLastAttemptAt(now);

        if (result.isSuccess()) {
            state.setLastSuccessAt(now);
            state.setConsecutiveFailures(0);
            state.setLastErrorMessage(null);
            state.setLastResult(result.getResultStatus());

            if (result.getEtag() != null && !result.getEtag().isBlank()) {
                state.setEtag(result.getEtag());
            }

            Duration interval = calculateInterval(type);
            state.setNextScheduledAt(now.plus(interval));
            log.info("Check {} succeeded ({}). Next scheduled check in {} hours at {}",
                    type, result.getResultStatus(), interval.toHours(), state.getNextScheduledAt());
        } else {
            int failures = state.getConsecutiveFailures() + 1;
            state.setConsecutiveFailures(failures);
            state.setLastResult(result.getResultStatus());
            state.setLastErrorMessage(result.getErrorMessage());

            if (result.getRateLimitResetAt() != null) {
                state.setNextScheduledAt(result.getRateLimitResetAt());
                log.warn("Check {} rate-limited by GitHub. Rescheduled at {}", type, state.getNextScheduledAt());
            } else {
                // Bounded exponential backoff: 1h, 2h, 4h, 8h, 16h up to max 24h
                long backoffHours = Math.min(24L, 1L << Math.min(failures - 1, 4));
                Duration backoff = Duration.ofHours(backoffHours);
                state.setNextScheduledAt(now.plus(backoff));
                log.warn("Check {} failed ({}: {}). Backing off for {} hours (consecutive failures: {}).",
                        type, result.getResultStatus(), result.getErrorMessage(), backoffHours, failures);
            }
        }

        saveState();
    }

    public void setLicenseSupplier(java.util.function.Supplier<LicensePayload> licenseSupplier) {
        this.licenseSupplier = licenseSupplier;
    }

    /**
     * Calculates the strict interval for a given check type based on active license plan and lifecycle state.
     */
    public Duration calculateInterval(CheckType type) {
        switch (type) {
            case LICENSE:
                LicensePayload lic = licenseSupplier != null ? licenseSupplier.get() : (licenseCoordinator != null ? licenseCoordinator.getActiveLicense() : null);
                return calculateLicenseInterval(lic);
            case ANNOUNCEMENTS:
                return INTERVAL_ANNOUNCEMENTS;
            case DATA_PROTECTION:
                return INTERVAL_DATA_PROTECTION;
            case APP_UPDATES:
                return INTERVAL_APP_UPDATES;
            default:
                return Duration.ofHours(24);
        }
    }

    /**
     * Calculates the dynamic license check interval:
     * - Lifetime license (GOLD): 30 days
     * - Subscription license: 15 days
     * - Approaching expiry (<= 7 days) or EMI due / overdue: 1 day (daily)
     */
    public Duration calculateLicenseInterval(LicensePayload license) {
        if (license == null) {
            return INTERVAL_APPROACHING_OR_OVERDUE;
        }

        // Lifetime plan check
        if (license.getPlan() == MembershipPlan.GOLD) {
            return INTERVAL_LIFETIME_LICENSE;
        }

        // Check if approaching expiry (<= 7 days)
        Instant now = Instant.now(clock);
        if (license.getExpiresAt() != null) {
            long daysToExpiry = Duration.between(now, license.getExpiresAt()).toDays();
            if (daysToExpiry <= 7) {
                return INTERVAL_APPROACHING_OR_OVERDUE;
            }
        }

        // Check if EMI is enabled and active/due
        if (license.getEmi() != null && Boolean.TRUE.equals(license.getEmi().getEnabled())) {
            var emiState = license.getEmi().getState();
            if (emiState != null) {
                String emiStatus = emiState.getEmiStatus();
                String accessStatus = emiState.getAccessStatus();

                if ("OVERDUE".equalsIgnoreCase(emiStatus) || "RESTRICTED".equalsIgnoreCase(accessStatus)) {
                    return INTERVAL_APPROACHING_OR_OVERDUE;
                }

                if (emiState.getNextDueDate() != null) {
                    try {
                        java.time.LocalDate dueDate = java.time.LocalDate.parse(emiState.getNextDueDate());
                        java.time.LocalDate today = java.time.LocalDate.ofInstant(now, java.time.ZoneOffset.UTC);
                        long daysToDue = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate);
                        if (daysToDue <= 7) {
                            return INTERVAL_APPROACHING_OR_OVERDUE;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        // Normal subscription
        return INTERVAL_SUBSCRIPTION_LICENSE;
    }

    private synchronized void checkAndHandleClockRollback(Instant now) {
        if (currentState == null || currentState.getLastSavedAt() == null) {
            return;
        }

        // If clock is earlier than lastSavedAt by more than 5 minutes, clock rollback occurred
        if (now.isBefore(currentState.getLastSavedAt().minus(Duration.ofMinutes(5)))) {
            log.warn("Clock rollback detected! Current time {} is earlier than last saved scheduler time {}. Reconciling schedule without burst calls.",
                    now, currentState.getLastSavedAt());

            for (GitCheckState checkState : currentState.getChecks().values()) {
                if (checkState.getNextScheduledAt() != null && checkState.getNextScheduledAt().isAfter(now.plus(Duration.ofDays(35)))) {
                    // Reset distorted far-future nextScheduledAt to standard interval
                    Duration interval = calculateInterval(checkState.getType());
                    checkState.setNextScheduledAt(now.plus(interval));
                }
            }
            saveState();
        }
    }

    private synchronized void loadState() {
        if (stateFile != null && stateFile.exists() && stateFile.isFile()) {
            try {
                this.currentState = mapper.readValue(stateFile, GitSchedulerState.class);
                return;
            } catch (Exception e) {
                log.warn("Could not load existing scheduler state from {}: {}. Initializing fresh state.",
                        stateFile.getAbsolutePath(), e.getMessage());
            }
        }
        this.currentState = new GitSchedulerState();
    }

    private synchronized void saveState() {
        if (stateFile == null) {
            return;
        }
        try {
            File parent = stateFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            currentState.setLastSavedAt(Instant.now(clock));

            File tempFile = new File(stateFile.getAbsolutePath() + ".tmp");
            mapper.writeValue(tempFile, currentState);
            Files.move(tempFile.toPath(), stateFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            log.warn("Failed to persist scheduler state to {}: {}", stateFile.getAbsolutePath(), e.getMessage());
        }
    }
}
