package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.dtos.SalesReturnRequest;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.security.TenantContext;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class SalesReturnConcurrencyTest {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private SalesReturnRepository salesReturnRepo;

    @Autowired
    private SalesReturnItemRepository salesReturnItemRepo;

    private Long testFirmId = 999L;
    private Customer testCustomer;
    private Product testProduct;
    private Invoice testInvoice;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentFirmId(testFirmId);

        testCustomer = new Customer();
        testCustomer.setName("Concurrent Test Customer");
        testCustomer.setFirmId(testFirmId);
        testCustomer = customerRepo.save(testCustomer);

        testProduct = new Product();
        testProduct.setName("Concurrent Test Widget");
        testProduct.setPrice(BigDecimal.valueOf(100.00));
        testProduct.setStockQuantity(BigDecimal.valueOf(50.00));
        testProduct.setGstPercentage(BigDecimal.valueOf(18.00));
        testProduct.setFirmId(testFirmId);
        testProduct = productRepo.save(testProduct);

        // Create an invoice with 5 units of testProduct
        InvoiceRequest invReq = new InvoiceRequest();
        invReq.setCustomerId(testCustomer.getId());
        invReq.setFirmId(testFirmId);
        invReq.setStatus(InvoiceStatus.FINAL);
        invReq.setPaid(true);

        InvoiceRequestItem itemReq = new InvoiceRequestItem();
        itemReq.setProductId(testProduct.getId());
        itemReq.setProductName(testProduct.getName());
        itemReq.setQty(5);
        itemReq.setPricePerUnit(BigDecimal.valueOf(100.00));
        itemReq.setGstPercent(BigDecimal.valueOf(18.00));
        invReq.setItems(Collections.singletonList(itemReq));

        testInvoice = invoiceService.createInvoice(invReq);
        assertNotNull(testInvoice.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testPessimisticWriteLockAnnotationPresentOnRepositoryMethods() throws Exception {
        Method findWithLockByIdAndFirmId = InvoiceRepository.class.getMethod("findWithLockByIdAndFirmId", Long.class, Long.class);
        Lock lockAnno1 = findWithLockByIdAndFirmId.getAnnotation(Lock.class);
        assertNotNull(lockAnno1, "findWithLockByIdAndFirmId must have @Lock annotation");
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lockAnno1.value(), "Lock mode must be PESSIMISTIC_WRITE");

        Method findWithLockById = InvoiceRepository.class.getMethod("findWithLockById", Long.class);
        Lock lockAnno2 = findWithLockById.getAnnotation(Lock.class);
        assertNotNull(lockAnno2, "findWithLockById must have @Lock annotation");
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lockAnno2.value(), "Lock mode must be PESSIMISTIC_WRITE");
    }

    @Test
    void testConcurrentReturnsForSameInvoiceQuantityCannotBothSucceed() throws Exception {
        Long invoiceId = testInvoice.getId();
        Long invoiceItemId = testInvoice.getItems().get(0).getId();

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger overReturnErrorCount = new AtomicInteger(0);
        List<Throwable> unexpectedExceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int threadIdx = i;
            executor.submit(() -> {
                try {
                    TenantContext.setCurrentFirmId(testFirmId);
                    startLatch.await(); // ensure simultaneous execution

                    SalesReturnRequest returnReq = SalesReturnRequest.builder()
                            .returnNumber("CN-CONC-" + threadIdx + "-" + System.currentTimeMillis())
                            .returnDate(LocalDate.now())
                            .reason("Concurrent Return Test")
                            .refundMode("CASH")
                            .includeOriginalDiscounts(true)
                            .items(Collections.singletonList(
                                    SalesReturnRequest.SalesReturnItemRequest.builder()
                                            .invoiceItemId(invoiceItemId)
                                            .productId(testProduct.getId())
                                            .returnQty(5) // Attempt to return ALL 5 remaining units
                                            .unitPrice(BigDecimal.valueOf(100.00))
                                            .gstPercent(BigDecimal.valueOf(18.00))
                                            .build()))
                            .build();

                    invoiceService.createSalesReturn(invoiceId, returnReq);
                    successCount.incrementAndGet();
                } catch (IllegalArgumentException ex) {
                    if (ex.getMessage() != null && ex.getMessage().contains("exceeds remaining returnable quantity")) {
                        overReturnErrorCount.incrementAndGet();
                    } else {
                        unexpectedExceptions.add(ex);
                    }
                } catch (Throwable t) {
                    // Check if caused by lock timeout or validation
                    Throwable root = t;
                    while (root.getCause() != null && root != root.getCause()) {
                        root = root.getCause();
                    }
                    if (root.getMessage() != null && root.getMessage().contains("exceeds remaining returnable quantity")) {
                        overReturnErrorCount.incrementAndGet();
                    } else {
                        unexpectedExceptions.add(t);
                    }
                } finally {
                    TenantContext.clear();
                    finishLatch.countDown();
                }
            });
        }

        // Fire both threads simultaneously
        startLatch.countDown();
        boolean completed = finishLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Threads should finish within 15 seconds");
        assertEquals(0, unexpectedExceptions.size(), "No unexpected exceptions should occur: " + unexpectedExceptions);

        // Verification:
        // Exactly ONE thread succeeded in returning 5 units.
        // The other thread received the validation failure for over-return.
        assertEquals(1, successCount.get(), "Exactly one concurrent return must succeed");
        assertEquals(1, overReturnErrorCount.get(), "The other concurrent return must be rejected for exceeding remaining quantity");

        // Verify database state: total returned quantity for this item is exactly 5, NOT 10!
        List<Object[]> returnedAggs = salesReturnItemRepo.sumReturnedQtyByInvoiceItemIdForInvoice(invoiceId);
        assertNotNull(returnedAggs);
        assertEquals(1, returnedAggs.size());
        assertEquals(5, ((Number) returnedAggs.get(0)[1]).intValue(), "Total returned quantity must strictly equal 5");
    }
}
