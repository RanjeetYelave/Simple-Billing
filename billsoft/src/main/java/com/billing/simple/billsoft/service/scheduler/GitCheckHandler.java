package com.billing.simple.billsoft.service.scheduler;

/**
 * Functional interface for executing a specific Git-based check.
 */
@FunctionalInterface
public interface GitCheckHandler {
    GitCheckExecutionResult execute(CheckType type, String currentEtag) throws Exception;
}

