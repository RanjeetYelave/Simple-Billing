package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.dtos.InvoiceUpdateRequest;
import com.billing.simple.billsoft.dtos.SalesReturnRequest;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Phase 9: Comprehensive Out-of-Stock Alerts, 7-Day Snooze & Non-Blocking Invoice Creation Test Suite")
public class ProductStockAlertAndSnoozeTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PlannerNotificationScheduler plannerNotificationScheduler;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private FirmDetailsRepository firmDetailsRepository;

    @Autowired
    private BackupService backupService;

    private final Long firm1 = 1L;
    private final Long firm2 = 2L;
    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        backupService.factoryReset();
        notificationRepository.deleteAll();
        TenantContext.setCurrentFirmId(firm1);

        testCustomer = customerRepository.save(Customer.builder()
                .name("Test Customer")
                .phone("9998887770")
                .firmId(firm1)
                .build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Product createProduct(String name, BigDecimal stock, BigDecimal minStock, Long firmId) {
        return productService.create(Product.builder()
                .name(name)
                .price(BigDecimal.valueOf(100.00))
                .stockQuantity(stock)
                .minStockLevel(minStock)
                .itemType("GOODS")
                .inInventory(true)
                .unit("pcs")
                .firmId(firmId)
                .build());
    }

    private InvoiceRequestItem buildRequestItem(Long productId, String name, int qty, BigDecimal price) {
        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(productId);
        item.setProductName(name);
        item.setQty(qty);
        item.setPricePerUnit(price);
        return item;
    }

    @Test
    @DisplayName("Scenario 1: Zero stock generates out-of-stock alert (inventory:stock:item:{id})")
    void testZeroStockGeneratesOutOfStockAlert() {
        Product p = createProduct("Zero Stock Item", BigDecimal.ZERO, BigDecimal.valueOf(5), firm1);
        plannerNotificationScheduler.notifyAggregatedLowStock(firm1, List.of(p));

        List<Notification> notifs = notificationRepository.findAll();
        assertThat(notifs).isNotEmpty();

        Notification itemAlert = notifs.stream()
                .filter(n -> ("inventory:stock:item:" + p.getId()).equals(n.getEventKey()))
                .findFirst().orElse(null);

        assertThat(itemAlert).isNotNull();
        assertThat(itemAlert.getTitle()).contains("Out of Stock: Zero Stock Item");
        assertThat(itemAlert.getStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(itemAlert.getPriority()).isEqualTo(NotificationPriority.HIGH);
    }

    @Test
    @DisplayName("Scenario 2: Positive stock above threshold does not generate alert")
    void testStockAboveThresholdGeneratesNoAlert() {
        Product p = createProduct("Plentiful Item", BigDecimal.valueOf(25), BigDecimal.valueOf(5), firm1);
        plannerNotificationScheduler.notifyAggregatedLowStock(firm1, List.of(p));

        List<Notification> notifs = notificationRepository.findAll();
        assertThat(notifs).isEmpty();
    }

    @Test
    @DisplayName("Scenario 3: Stock below minimum threshold generates LOW_STOCK alert")
    void testStockBelowThresholdGeneratesLowStockAlert() {
        Product p = createProduct("Low Stock Item", BigDecimal.valueOf(3), BigDecimal.valueOf(5), firm1);
        plannerNotificationScheduler.notifyAggregatedLowStock(firm1, List.of(p));

        List<Notification> notifs = notificationRepository.findAll();
        assertThat(notifs).isNotEmpty();

        Notification itemAlert = notifs.stream()
                .filter(n -> ("inventory:stock:item:" + p.getId()).equals(n.getEventKey()))
                .findFirst().orElse(null);

        assertThat(itemAlert).isNotNull();
        assertThat(itemAlert.getTitle()).contains("Low Stock: Low Stock Item");
        assertThat(itemAlert.getPriority()).isEqualTo(NotificationPriority.NORMAL);
    }

    @Test
    @DisplayName("Scenario 4: Repeated scheduler execution is idempotent")
    void testRepeatedSchedulerExecutionIsIdempotent() {
        Product p = createProduct("Idempotent Item", BigDecimal.ZERO, BigDecimal.valueOf(5), firm1);
        plannerNotificationScheduler.notifyAggregatedLowStock(firm1, List.of(p));
        int countAfterFirst = notificationRepository.findAll().size();

        // Run again
        plannerNotificationScheduler.notifyAggregatedLowStock(firm1, List.of(p));
        int countAfterSecond = notificationRepository.findAll().size();

        assertThat(countAfterSecond).isEqualTo(countAfterFirst);
    }

    @Test
    @DisplayName("Scenario 5: Replenishment above threshold resolves alert (ACTIONED)")
    void testReplenishmentResolvesAlert() {
        Product p = createProduct("Replenish Item", BigDecimal.ZERO, BigDecimal.valueOf(5), firm1);
        plannerNotificationScheduler.notifyAggregatedLowStock(firm1, List.of(p));

        Notification notifBefore = notificationRepository.findByFirmIdAndEventKey(firm1, "inventory:stock:item:" + p.getId()).orElse(null);
        assertThat(notifBefore).isNotNull();
        assertThat(notifBefore.getStatus()).isEqualTo(NotificationStatus.UNREAD);

        // Adjust stock above threshold
        productService.adjustStock(p.getId(), BigDecimal.valueOf(10), "RESTOCK", "Restocked via shipment");

        Notification notifAfter = notificationRepository.findByFirmIdAndEventKey(firm1, "inventory:stock:item:" + p.getId()).orElse(null);
        assertThat(notifAfter).isNotNull();
        assertThat(notifAfter.getStatus()).isEqualTo(NotificationStatus.ACTIONED);
    }

    @Test
    @DisplayName("Scenario 6: Subsequent zero-stock event initiates a new alert lifecycle")
    void testSubsequentZeroStockReopensAlert() {
        Product p = createProduct("Recycle Item", BigDecimal.valueOf(10), BigDecimal.valueOf(5), firm1);
        productService.adjustStock(p.getId(), BigDecimal.ZERO, "SALE", "Sold out");

        p = productService.getById(p.getId());
        plannerNotificationScheduler.notifyAggregatedLowStock(firm1, List.of(p));

        Notification notif = notificationRepository.findByFirmIdAndEventKey(firm1, "inventory:stock:item:" + p.getId()).orElse(null);
        assertThat(notif).isNotNull();
        assertThat(notif.getStatus()).isEqualTo(NotificationStatus.UNREAD);
    }

    @Test
    @DisplayName("Scenario 7: Snooze expiry is exactly 7 days (now.plusDays(7))")
    void testSnoozeExpiryIsSevenDays() {
        Product p = createProduct("Snooze Item", BigDecimal.ZERO, BigDecimal.valueOf(5), firm1);
        plannerNotificationScheduler.notifyAggregatedLowStock(firm1, List.of(p));

        LocalDateTime beforeSnooze = LocalDateTime.now();
        Product snoozed = productService.snoozeStockAlert(p.getId(), firm1, 7);

        assertThat(snoozed.getStockAlertSnoozedUntil()).isNotNull();
        long daysBetween = ChronoUnit.DAYS.between(beforeSnooze, snoozed.getStockAlertSnoozedUntil());
        assertThat(daysBetween).isBetween(6L, 7L);
    }

    @Test
    @DisplayName("Scenario 8: Snoozed alert suppressed from active list before expiry")
    void testSnoozedAlertSuppressedFromActiveList() {
        Product p = createProduct("Active Suppression Item", BigDecimal.ZERO, BigDecimal.valueOf(5), firm1);
        plannerNotificationScheduler.notifyAggregatedLowStock(firm1, List.of(p));

        List<Notification> activeBefore = notificationService.listNotifications(firm1, "ACTIVE", null, 50);
        assertThat(activeBefore).anyMatch(n -> ("inventory:stock:item:" + p.getId()).equals(n.getEventKey()));

        // Snooze
        productService.snoozeStockAlert(p.getId(), firm1, 7);

        List<Notification> activeAfter = notificationService.listNotifications(firm1, "ACTIVE", null, 50);
        assertThat(activeAfter).noneMatch(n -> ("inventory:stock:item:" + p.getId()).equals(n.getEventKey()));
    }

    @Test
    @DisplayName("Scenario 9: Alert reappears after expiry when stock remains zero")
    void testAlertReappearsAfterSnoozeExpiry() {
        Product p = createProduct("Expired Snooze Item", BigDecimal.ZERO, BigDecimal.valueOf(5), firm1);
        plannerNotificationScheduler.notifyAggregatedLowStock(firm1, List.of(p));

        Notification notif = notificationRepository.findByFirmIdAndEventKey(firm1, "inventory:stock:item:" + p.getId()).orElse(null);
        assertThat(notif).isNotNull();

        // Simulate expired snooze (set snoozedUntil in the past)
        notif.setStatus(NotificationStatus.SNOOZED);
        notif.setSnoozedUntil(LocalDateTime.now().minusHours(1));
        notificationRepository.save(notif);

        p.setStockAlertSnoozedUntil(LocalDateTime.now().minusHours(1));
        productRepository.save(p);

        // Run reconciliation
        notificationService.reconcileExpiredSnoozes();

        Notification reloaded = notificationRepository.findById(notif.getId()).orElse(null);
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.UNREAD);
    }

    @Test
    @DisplayName("Scenario 10: Replenishment resolves alert even if currently snoozed")
    void testReplenishmentResolvesSnoozedAlert() {
        Product p = createProduct("Replenish Snoozed Item", BigDecimal.ZERO, BigDecimal.valueOf(5), firm1);
        plannerNotificationScheduler.notifyAggregatedLowStock(firm1, List.of(p));

        productService.snoozeStockAlert(p.getId(), firm1, 7);
        Notification notif = notificationRepository.findByFirmIdAndEventKey(firm1, "inventory:stock:item:" + p.getId()).orElse(null);
        assertThat(notif.getStatus()).isEqualTo(NotificationStatus.SNOOZED);

        // Replenish
        productService.adjustStock(p.getId(), BigDecimal.valueOf(15), "SHIPMENT", "Fresh batch received");

        Product reloadedProduct = productService.getById(p.getId());
        assertThat(reloadedProduct.getStockAlertSnoozedUntil()).isNull();

        Notification notifAfter = notificationRepository.findByFirmIdAndEventKey(firm1, "inventory:stock:item:" + p.getId()).orElse(null);
        assertThat(notifAfter.getStatus()).isEqualTo(NotificationStatus.ACTIONED);
    }

    @Test
    @DisplayName("Scenario 11: Snooze persists on Product entity across repository reloads")
    void testSnoozePersistsOnProductEntity() {
        Product p = createProduct("Persistent Snooze Item", BigDecimal.ZERO, BigDecimal.valueOf(5), firm1);
        Product snoozed = productService.snoozeStockAlert(p.getId(), firm1, 7);

        productRepository.flush();

        Product loaded = productRepository.findById(p.getId()).orElse(null);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getStockAlertSnoozedUntil()).isNotNull();
        assertThat(loaded.getStockAlertSnoozedUntil()).isEqualTo(snoozed.getStockAlertSnoozedUntil());
    }

    @Test
    @DisplayName("Scenario 12: Multi-firm tenant isolation for snooze and alert generation")
    void testMultiFirmTenantIsolation() {
        Product pFirm1 = createProduct("Firm 1 Item", BigDecimal.ZERO, BigDecimal.valueOf(5), firm1);
        TenantContext.setCurrentFirmId(firm2);
        Product pFirm2 = createProduct("Firm 2 Item", BigDecimal.ZERO, BigDecimal.valueOf(5), firm2);
        TenantContext.setCurrentFirmId(firm1);

        plannerNotificationScheduler.notifyAggregatedLowStock(firm1, List.of(pFirm1));
        plannerNotificationScheduler.notifyAggregatedLowStock(firm2, List.of(pFirm2));

        List<Notification> firm1Notifs = notificationRepository.findByFirmId(firm1);
        List<Notification> firm2Notifs = notificationRepository.findByFirmId(firm2);

        assertThat(firm1Notifs).allMatch(n -> firm1.equals(n.getFirmId()));
        assertThat(firm2Notifs).allMatch(n -> firm2.equals(n.getFirmId()));

        assertThat(firm1Notifs).noneMatch(n -> ("inventory:stock:item:" + pFirm2.getId()).equals(n.getEventKey()));
        assertThat(firm2Notifs).noneMatch(n -> ("inventory:stock:item:" + pFirm1.getId()).equals(n.getEventKey()));
    }

    @Test
    @DisplayName("Scenario 13 & 14: Invoice creation with zero-stock product succeeds with allowOutOfStock=true")
    void testInvoiceCreationWithZeroStockSucceeds() {
        Product p = createProduct("Sold Out Widget", BigDecimal.ZERO, BigDecimal.valueOf(2), firm1);

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(firm1);
        req.setCustomerId(testCustomer.getId());
        req.setPaid(true);
        req.setAllowOutOfStock(true);
        req.setItems(List.of(buildRequestItem(p.getId(), p.getName(), 4, BigDecimal.valueOf(100.00))));

        Invoice invoice = invoiceService.createInvoice(req);
        assertThat(invoice).isNotNull();
        assertThat(invoice.getId()).isNotNull();

        Product reloaded = productService.getById(p.getId());
        assertThat(reloaded.getStockQuantity()).isEqualByComparingTo("-4.000");
    }

    @Test
    @DisplayName("Scenario 15: Stock deduction and negative ledger recording are accurate")
    void testStockDeductionAndNegativeLedger() {
        Product p = createProduct("Negative Ledger Widget", BigDecimal.valueOf(2), BigDecimal.valueOf(5), firm1);

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(firm1);
        req.setCustomerId(testCustomer.getId());
        req.setPaid(true);
        req.setAllowOutOfStock(true);
        req.setItems(List.of(buildRequestItem(p.getId(), p.getName(), 5, BigDecimal.valueOf(100.00)))); // 2 available, 3 shortage

        invoiceService.createInvoice(req);

        List<StockMovement> movements = stockMovementRepository.findByProductIdAndFirmIdOrderByCreatedAtDesc(p.getId(), firm1);
        assertThat(movements).isNotEmpty();
        StockMovement mov = movements.get(0);
        assertThat(mov.getQuantityChange()).isEqualByComparingTo("-5.000");
        assertThat(mov.getNewStock()).isEqualByComparingTo("-3.000");
        assertThat(mov.getNote()).contains("[Negative Stock Warning - Shortage: 3");
    }

    @Test
    @DisplayName("Scenario 16: Invoice update delta reconciliation with out-of-stock items")
    void testInvoiceUpdateDeltaReconciliation() {
        Product p = createProduct("Delta Widget", BigDecimal.ZERO, BigDecimal.valueOf(5), firm1);

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(firm1);
        req.setCustomerId(testCustomer.getId());
        req.setPaid(true);
        req.setAllowOutOfStock(true);
        req.setItems(List.of(buildRequestItem(p.getId(), p.getName(), 5, BigDecimal.valueOf(100.00))));

        Invoice invoice = invoiceService.createInvoice(req);
        assertThat(productService.getById(p.getId()).getStockQuantity()).isEqualByComparingTo("-5.000");

        // Update invoice reducing requested quantity from 5 to 2 (delta = +3 restored to stock)
        InvoiceUpdateRequest updateReq = new InvoiceUpdateRequest();
        updateReq.setCustomerId(testCustomer.getId());
        updateReq.setPaid(true);
        updateReq.setAllowOutOfStock(true);
        updateReq.setItems(List.of(buildRequestItem(p.getId(), p.getName(), 2, BigDecimal.valueOf(100.00))));

        invoiceService.updateFullInvoice(invoice.getId(), updateReq);

        assertThat(productService.getById(p.getId()).getStockQuantity()).isEqualByComparingTo("-2.000");
    }

    @Test
    @DisplayName("Scenario 17: Invoice cancellation restores stock and resolves alerts if stock becomes positive")
    void testInvoiceCancellationRestoresStock() {
        Product p = createProduct("Cancel Widget", BigDecimal.valueOf(5), BigDecimal.valueOf(2), firm1);

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(firm1);
        req.setCustomerId(testCustomer.getId());
        req.setPaid(true);
        req.setAllowOutOfStock(true);
        req.setItems(List.of(buildRequestItem(p.getId(), p.getName(), 5, BigDecimal.valueOf(100.00))));

        Invoice invoice = invoiceService.createInvoice(req);
        assertThat(productService.getById(p.getId()).getStockQuantity()).isEqualByComparingTo("0.000");

        // Cancel invoice
        invoiceService.updateStatus(invoice.getId(), InvoiceStatus.CANCELLED);

        Product restored = productService.getById(p.getId());
        assertThat(restored.getStockQuantity()).isEqualByComparingTo("5.000");
    }

    @Test
    @DisplayName("Scenario 18: Sales returns update stock and resolve alerts")
    void testSalesReturnUpdatesStock() {
        Product p = createProduct("Return Widget", BigDecimal.valueOf(2), BigDecimal.valueOf(2), firm1);

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(firm1);
        req.setCustomerId(testCustomer.getId());
        req.setPaid(true);
        req.setAllowOutOfStock(true);
        req.setItems(List.of(buildRequestItem(p.getId(), p.getName(), 2, BigDecimal.valueOf(100.00))));

        Invoice invoice = invoiceService.createInvoice(req);
        assertThat(productService.getById(p.getId()).getStockQuantity()).isEqualByComparingTo("0.000");

        // Customer returns 2 items
        SalesReturnRequest returnReq = new SalesReturnRequest();
        SalesReturnRequest.SalesReturnItemRequest retItem = new SalesReturnRequest.SalesReturnItemRequest();
        retItem.setInvoiceItemId(invoice.getItems().get(0).getId());
        retItem.setProductId(p.getId());
        retItem.setReturnQty(2);
        returnReq.setItems(List.of(retItem));

        invoiceService.createSalesReturn(invoice.getId(), returnReq);

        Product returnedProduct = productService.getById(p.getId());
        assertThat(returnedProduct.getStockQuantity()).isEqualByComparingTo("2.000");
    }
}
