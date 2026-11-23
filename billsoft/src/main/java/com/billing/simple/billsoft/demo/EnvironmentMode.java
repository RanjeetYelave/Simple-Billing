package com.billing.simple.billsoft.demo;

/**
 * Authoritative environment mode for RupeeCRM runtime.
 * - NORMAL: Production or test execution against the primary database.
 * - DEMO: Isolated demonstration environment against the dedicated demo database.
 */
public enum EnvironmentMode {
    NORMAL,
    DEMO
}
