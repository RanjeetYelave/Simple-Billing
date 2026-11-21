package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Tag("regression")
@Tag("integration")
@DisplayName("AUDIT-P0: Multi-Thread & Multi-Connection Document Sequence Concurrency & Rollback Test")
public class MultiConnectionDocumentSequenceConcurrencyTest {

    @Autowired
    private DocumentSequenceService sequenceService;

    @Autowired
    private AppConfigRepository appConfigRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("Simultaneous first-time partition initialization across 16 concurrent threads")
    void testSimultaneousFirstTimePartitionInitialization() throws Exception {
        Long testFirmId = 8888L;
        LocalDate testDate = LocalDate.of(2035, 6, 15); // Uninitialized future FY 35-36
        int threadCount = 16;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        Set<String> generatedNumbers = ConcurrentHashMap.newKeySet();
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await(); // Synchronize all threads to start at the exact same instant
                    String num = sequenceService.generateNextNumber(DocumentType.INVOICE, testFirmId, testDate);
                    generatedNumbers.add(num);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown(); // Release threads
        boolean finished = endGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(errors).isEmpty();
        assertThat(generatedNumbers).hasSize(threadCount);

        // Verify sequential allocation INV-0001 through INV-0016
        for (int i = 1; i <= threadCount; i++) {
            String expected = String.format("INV-%04d", i);
            assertThat(generatedNumbers).contains(expected);
        }
    }

    @Test
    @DisplayName("Independent document types do not collide or block each other")
    void testConcurrentIndependentDocumentTypes() throws Exception {
        Long testFirmId = 7777L;
        LocalDate testDate = LocalDate.of(2036, 7, 20);
        int threadCount = 10;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount * 3);
        CountDownLatch endGate = new CountDownLatch(threadCount * 3);
        Set<String> invoices = ConcurrentHashMap.newKeySet();
        Set<String> quotations = ConcurrentHashMap.newKeySet();
        Set<String> pos = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    invoices.add(sequenceService.generateNextNumber(DocumentType.INVOICE, testFirmId, testDate));
                } finally {
                    endGate.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    quotations.add(sequenceService.generateNextNumber(DocumentType.ESTIMATE, testFirmId, testDate));
                } finally {
                    endGate.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    pos.add(sequenceService.generateNextNumber(DocumentType.PURCHASE_ORDER, testFirmId, testDate));
                } finally {
                    endGate.countDown();
                }
            });
        }

        boolean finished = endGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(invoices).hasSize(threadCount);
        assertThat(quotations).hasSize(threadCount);
        assertThat(pos).hasSize(threadCount);
    }

    @Test
    @DisplayName("Transaction rollback restores sequence state cleanly")
    void testSequenceRollbackBehavior() {
        Long testFirmId = 6666L;
        LocalDate testDate = LocalDate.of(2037, 8, 10);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // Step 1: Initial generation in committed tx
        String num1 = tx.execute(status -> sequenceService.generateNextNumber(DocumentType.INVOICE, testFirmId, testDate));
        assertThat(num1).isEqualTo("INV-0001");

        // Step 2: Generation in a rolling back tx
        try {
            tx.execute(status -> {
                String num2 = sequenceService.generateNextNumber(DocumentType.INVOICE, testFirmId, testDate);
                assertThat(num2).isEqualTo("INV-0002");
                throw new RuntimeException("Simulated business validation failure rollback");
            });
        } catch (RuntimeException ignored) {
        }

        // Step 3: Next generation after rollback
        String num3 = tx.execute(status -> sequenceService.generateNextNumber(DocumentType.INVOICE, testFirmId, testDate));
        // Sequence rolled back cleanly with transaction, reusing sequence 2
        assertThat(num3).isEqualTo("INV-0002");
    }

    @Test
    @DisplayName("syncSequenceIfApplicable correctly advances sequence counter without regressing")
    void testSyncSequenceAdvancement() {
        Long testFirmId = 5555L;
        LocalDate testDate = LocalDate.of(2038, 9, 1);

        // Sync with manual number INV-0042
        sequenceService.syncSequenceIfApplicable(DocumentType.INVOICE, testFirmId, "INV-0042", testDate);

        // Next generated should be INV-0043
        String next = sequenceService.generateNextNumber(DocumentType.INVOICE, testFirmId, testDate);
        assertThat(next).isEqualTo("INV-0043");

        // Lower sync with INV-0010 must not regress sequence
        sequenceService.syncSequenceIfApplicable(DocumentType.INVOICE, testFirmId, "INV-0010", testDate);
        String afterLower = sequenceService.generateNextNumber(DocumentType.INVOICE, testFirmId, testDate);
        assertThat(afterLower).isEqualTo("INV-0044");
    }
}
