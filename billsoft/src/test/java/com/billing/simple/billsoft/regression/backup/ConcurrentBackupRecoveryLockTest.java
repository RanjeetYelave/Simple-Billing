package com.billing.simple.billsoft.regression.backup;

import com.billing.simple.billsoft.service.AutoBackupService;
import com.billing.simple.billsoft.service.BackupRecoveryCoordinator;
import com.billing.simple.billsoft.service.BackupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Tag("regression")
@Tag("integration")
@DisplayName("Group 2 Cross-Flow: Concurrent Backup Recovery Lock Test")
public class ConcurrentBackupRecoveryLockTest {

    @Autowired
    private BackupRecoveryCoordinator coordinator;

    @Autowired
    private AutoBackupService autoBackupService;

    @Autowired
    private BackupService backupService;

    @Test
    @DisplayName("AutoBackup skips cleanly when coordinator lock is held by active recovery operation, and concurrent reset is rejected")
    void testConcurrencyCoordination() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch lockAcquiredLatch = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);

        Future<Void> lockHolder = executor.submit(() -> {
            boolean acquired = coordinator.tryLock(5, TimeUnit.SECONDS);
            assertThat(acquired).isTrue();
            lockAcquiredLatch.countDown();
            try {
                releaseLatch.await(10, TimeUnit.SECONDS);
            } finally {
                coordinator.unlock();
            }
            return null;
        });

        // Wait until background thread holds the lock
        assertThat(lockAcquiredLatch.await(5, TimeUnit.SECONDS)).isTrue();

        try {
            // 1. AutoBackupService should detect lock and skip cleanly
            Map<String, Object> autoStatus = autoBackupService.runAutoBackup();
            assertThat(autoStatus.get("status")).isEqualTo("SKIPPED_RECOVERY_IN_PROGRESS");
            assertThat(autoStatus.get("message")).toString().contains("in progress");

            // 2. Another thread attempting factory reset must fail fast
            assertThatThrownBy(() -> backupService.factoryReset())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("in progress");
        } finally {
            // Release lock
            releaseLatch.countDown();
            lockHolder.get(5, TimeUnit.SECONDS);
            executor.shutdown();
        }

        // 3. Now that lock is released, factory reset succeeds
        backupService.factoryReset();
    }
}
