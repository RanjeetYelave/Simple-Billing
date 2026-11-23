package com.billing.simple.billsoft.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe context holder for the active environment mode (NORMAL vs DEMO).
 * Supports both global mode (affecting all web requests and background threads)
 * and thread-local overrides (used during isolated seed restoration or verification).
 */
public final class EnvironmentModeContext {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentModeContext.class);

    private static final AtomicReference<EnvironmentMode> GLOBAL_MODE = new AtomicReference<>(EnvironmentMode.NORMAL);
    private static final ThreadLocal<EnvironmentMode> THREAD_OVERRIDE = new ThreadLocal<>();

    private EnvironmentModeContext() {
    }

    public static EnvironmentMode getCurrentMode() {
        EnvironmentMode override = THREAD_OVERRIDE.get();
        if (override != null) {
            return override;
        }
        return GLOBAL_MODE.get();
    }

    public static void setGlobalMode(EnvironmentMode mode) {
        if (mode == null) {
            mode = EnvironmentMode.NORMAL;
        }
        EnvironmentMode prev = GLOBAL_MODE.getAndSet(mode);
        log.info("GLOBAL ENVIRONMENT MODE TRANSITION: {} -> {}", prev, mode);
    }

    public static void setThreadOverride(EnvironmentMode mode) {
        if (mode != null) {
            THREAD_OVERRIDE.set(mode);
        } else {
            THREAD_OVERRIDE.remove();
        }
    }

    public static void clearThreadOverride() {
        THREAD_OVERRIDE.remove();
    }

    public static boolean isDemoMode() {
        return getCurrentMode() == EnvironmentMode.DEMO;
    }

    public static boolean isNormalMode() {
        return getCurrentMode() == EnvironmentMode.NORMAL;
    }
}
