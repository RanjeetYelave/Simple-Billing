package com.billing.simple.billsoft.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Centralized in-process lock coordinator to prevent concurrent execution between
 * automated backup jobs, administrative factory resets, and database restore transactions.
 */
@Component
public class BackupRecoveryCoordinator {

    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Tries to acquire the recovery lock without blocking.
     * @return true if acquired, false if another backup/recovery operation is already active.
     */
    public boolean tryLock() {
        return lock.tryLock();
    }

    /**
     * Tries to acquire the recovery lock with timeout.
     */
    public boolean tryLock(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
        return lock.tryLock(timeout, unit);
    }

    /**
     * Acquires the recovery lock, blocking until available.
     */
    public void lock() {
        lock.lock();
    }

    /**
     * Releases the recovery lock if held by the current thread.
     */
    public void unlock() {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /**
     * Checks if any thread currently holds the recovery lock.
     */
    public boolean isRecoveryInProgress() {
        return lock.isLocked();
    }
}
