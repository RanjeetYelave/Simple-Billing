package com.billing.simple.billsoft.update;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Cross-process and cross-thread update lock manager using OS-level FileChannel locks.
 * Prevents concurrent download or staging operations across multiple processes or browser tabs.
 */
public final class UpdateLockManager {

    private UpdateLockManager() {}

    public static final class LockHandle implements Closeable {
        private final FileChannel channel;
        private final FileLock lock;
        private final Path path;

        private LockHandle(FileChannel channel, FileLock lock, Path path) {
            this.channel = channel;
            this.lock = lock;
            this.path = path;
        }

        @Override
        public void close() {
            try {
                if (lock != null && lock.isValid()) {
                    lock.release();
                }
            } catch (Exception ignored) {}
            try {
                if (channel != null && channel.isOpen()) {
                    channel.close();
                }
            } catch (Exception ignored) {}
            try {
                Files.deleteIfExists(path);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Attempts to acquire an exclusive OS-level file lock on the specified lock file.
     * Returns a non-null LockHandle if acquired, or null if already held by another process/thread.
     */
    public static LockHandle tryAcquire(Path lockFile) {
        try {
            Path parent = lockFile.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            FileChannel channel = FileChannel.open(
                    lockFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE
            );
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                return null;
            }
            return new LockHandle(channel, lock, lockFile);
        } catch (Exception e) {
            return null;
        }
    }
}
