package com.billing.simple.billsoft.regression.inventory;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.dtos.InvoiceUpdateRequest;
import com.billing.simple.billsoft.dtos.SalesReturnRequest;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.billing.simple.billsoft.service.InvoiceService;
import com.billing.simple.billsoft.service.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@Tag("regression")
@Tag("integration")
@DisplayName("Phase 3: Invoice ↔ Inventory Reconciliation & Lifecycle Integrity Regression Tests")
public class InvoiceInventoryReconciliationRegressionTest {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private StockMovementRepository stockMovementRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private SalesReturnRepository salesReturnRepo;

    @Autowired
    private com.billing.simple.billsoft.repo.AppConfigRepository appConfigRepo;

    @Autowired
    private com.billing.simple.billsoft.service.BackupService backupService;

    private final Long testFirmId = 1L;
    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        backupService.factoryReset();
        TenantContext.setCurrentFirmId(testFirmId);

        testCustomer = customerRepo.save(Customer.builder()
                .name("Acme Corp")
                .phone("9876543210")
                .firmId(testFirmId)
                .build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Product createGoodsProduct(String name, BigDecimal stock) {
        return productService.create(Product.builder()
                .name(name)
                .price(BigDecimal.valueOf(100.00))
                .stockQuantity(stock)
                .minStockLevel(BigDecimal.valueOf(2.00))
                .itemType("GOODS")
                .unit("pcs")
                .firmId(testFirmId)
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

    // =========================================================================
    // Finding #1: updateFullInvoice() item quantity / product changes
    // =========================================================================

    @Test
    @DisplayName("1. updateFullInvoice: Quantity Increase (5 -> 8) must deduct delta of 3 from stock")
    void testUpdateInvoice_IncreaseQuantity_DeductsDeltaStockAndRecordsMovement() {
        Product p = createGoodsProduct("Widget Alpha", BigDecimal.valueOf(20.000));

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.PAID);
        req.setPaid(true);
        req.setItems(List.of(buildRequestItem(p.getId(), p.getName(), 5, BigDecimal.valueOf(100.00))));

        Invoice created = invoiceService.createInvoice(req);
        assertThat(productService.getById(p.getId()).getStockQuantity()).isEqualByComparingTo("15.000");

        // Update quantity to 8
        InvoiceUpdateRequest updateReq = new InvoiceUpdateRequest();
        updateReq.setCustomerId(testCustomer.getId());
        updateReq.setStatus(InvoiceStatus.PAID);
        updateReq.setPaid(true);
        updateReq.setItems(List.of(buildRequestItem(p.getId(), p.getName(), 8, BigDecimal.valueOf(100.00))));

        invoiceService.updateFullInvoice(created.getId(), updateReq);

        Product afterUpdate = productService.getById(p.getId());
        assertThat(afterUpdate.getStockQuantity()).isEqualByComparingTo("12.000");

        List<StockMovement> movements = stockMovementRepo.findByProductIdAndFirmIdOrderByCreatedAtDesc(p.getId(), testFirmId);
        // Expecting: INITIAL_STOCK (+20), INVOICE_SALE (-5), INVOICE_SALE (-3)
        assertThat(movements).hasSize(3);
        StockMovement latest = movements.get(0);
        assertThat(latest.getMovementType()).isEqualTo("INVOICE_SALE");
        assertThat(latest.getQuantityChange()).isEqualByComparingTo("-3.000");
        assertThat(latest.getNewStock()).isEqualByComparingTo("12.000");
    }

    @Test
    @DisplayName("2. updateFullInvoice: Quantity Decrease (8 -> 5) must restore delta of 3 to stock")
    void testUpdateInvoice_DecreaseQuantity_RestoresDeltaStockAndRecordsMovement() {
        Product p = createGoodsProduct("Widget Beta", BigDecimal.valueOf(20.000));

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.PAID);
        req.setPaid(true);
        req.setItems(List.of(buildRequestItem(p.getId(), p.getName(), 8, BigDecimal.valueOf(100.00))));

        Invoice created = invoiceService.createInvoice(req);
        assertThat(productService.getById(p.getId()).getStockQuantity()).isEqualByComparingTo("12.000");

        // Update quantity to 5
        InvoiceUpdateRequest updateReq = new InvoiceUpdateRequest();
        updateReq.setCustomerId(testCustomer.getId());
        updateReq.setStatus(InvoiceStatus.PAID);
        updateReq.setPaid(true);
        updateReq.setItems(List.of(buildRequestItem(p.getId(), p.getName(), 5, BigDecimal.valueOf(100.00))));

        invoiceService.updateFullInvoice(created.getId(), updateReq);

        Product afterUpdate = productService.getById(p.getId());
        assertThat(afterUpdate.getStockQuantity()).isEqualByComparingTo("15.000");

        List<StockMovement> movements = stockMovementRepo.findByProductIdAndFirmIdOrderByCreatedAtDesc(p.getId(), testFirmId);
        assertThat(movements).hasSize(3);
        StockMovement latest = movements.get(0);
        assertThat(latest.getMovementType()).isEqualTo("INVOICE_CANCELLED");
        assertThat(latest.getQuantityChange()).isEqualByComparingTo("3.000");
        assertThat(latest.getNewStock()).isEqualByComparingTo("15.000");
    }

    @Test
    @DisplayName("3. updateFullInvoice: Removing an item must restore its stock")
    void testUpdateInvoice_RemoveItem_RestoresStockAndRecordsMovement() {
        Product p1 = createGoodsProduct("Item One", BigDecimal.valueOf(10.000));
        Product p2 = createGoodsProduct("Item Two", BigDecimal.valueOf(10.000));

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.PAID);
        req.setPaid(true);
        req.setItems(List.of(
                buildRequestItem(p1.getId(), p1.getName(), 4, BigDecimal.valueOf(50.00)),
                buildRequestItem(p2.getId(), p2.getName(), 3, BigDecimal.valueOf(75.00))
        ));

        Invoice created = invoiceService.createInvoice(req);
        assertThat(productService.getById(p1.getId()).getStockQuantity()).isEqualByComparingTo("6.000");
        assertThat(productService.getById(p2.getId()).getStockQuantity()).isEqualByComparingTo("7.000");

        // Update: remove Item One, keep Item Two
        InvoiceUpdateRequest updateReq = new InvoiceUpdateRequest();
        updateReq.setCustomerId(testCustomer.getId());
        updateReq.setStatus(InvoiceStatus.PAID);
        updateReq.setPaid(true);
        updateReq.setItems(List.of(
                buildRequestItem(p2.getId(), p2.getName(), 3, BigDecimal.valueOf(75.00))
        ));

        invoiceService.updateFullInvoice(created.getId(), updateReq);

        assertThat(productService.getById(p1.getId()).getStockQuantity()).isEqualByComparingTo("10.000");
        assertThat(productService.getById(p2.getId()).getStockQuantity()).isEqualByComparingTo("7.000");
    }

    @Test
    @DisplayName("4. updateFullInvoice: Adding an item must deduct its stock")
    void testUpdateInvoice_AddNewItem_DeductsStockAndRecordsMovement() {
        Product p1 = createGoodsProduct("Item Primary", BigDecimal.valueOf(10.000));
        Product p2 = createGoodsProduct("Item Secondary", BigDecimal.valueOf(10.000));

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.PAID);
        req.setPaid(true);
        req.setItems(List.of(
                buildRequestItem(p1.getId(), p1.getName(), 2, BigDecimal.valueOf(50.00))
        ));

        Invoice created = invoiceService.createInvoice(req);
        assertThat(productService.getById(p1.getId()).getStockQuantity()).isEqualByComparingTo("8.000");
        assertThat(productService.getById(p2.getId()).getStockQuantity()).isEqualByComparingTo("10.000");

        // Update: add Item Secondary
        InvoiceUpdateRequest updateReq = new InvoiceUpdateRequest();
        updateReq.setCustomerId(testCustomer.getId());
        updateReq.setStatus(InvoiceStatus.PAID);
        updateReq.setPaid(true);
        updateReq.setItems(List.of(
                buildRequestItem(p1.getId(), p1.getName(), 2, BigDecimal.valueOf(50.00)),
                buildRequestItem(p2.getId(), p2.getName(), 4, BigDecimal.valueOf(75.00))
        ));

        invoiceService.updateFullInvoice(created.getId(), updateReq);

        assertThat(productService.getById(p1.getId()).getStockQuantity()).isEqualByComparingTo("8.000");
        assertThat(productService.getById(p2.getId()).getStockQuantity()).isEqualByComparingTo("6.000");
    }

    @Test
    @DisplayName("5. updateFullInvoice: Changing product must restore old product and deduct new product")
    void testUpdateInvoice_ChangeProduct_SwapsStockBetweenProducts() {
        Product p1 = createGoodsProduct("Old Product", BigDecimal.valueOf(10.000));
        Product p2 = createGoodsProduct("New Product", BigDecimal.valueOf(10.000));

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.PAID);
        req.setPaid(true);
        req.setItems(List.of(buildRequestItem(p1.getId(), p1.getName(), 3, BigDecimal.valueOf(100.00))));

        Invoice created = invoiceService.createInvoice(req);
        assertThat(productService.getById(p1.getId()).getStockQuantity()).isEqualByComparingTo("7.000");
        assertThat(productService.getById(p2.getId()).getStockQuantity()).isEqualByComparingTo("10.000");

        // Swap to p2
        InvoiceUpdateRequest updateReq = new InvoiceUpdateRequest();
        updateReq.setCustomerId(testCustomer.getId());
        updateReq.setStatus(InvoiceStatus.PAID);
        updateReq.setPaid(true);
        updateReq.setItems(List.of(buildRequestItem(p2.getId(), p2.getName(), 5, BigDecimal.valueOf(100.00))));

        invoiceService.updateFullInvoice(created.getId(), updateReq);

        assertThat(productService.getById(p1.getId()).getStockQuantity()).isEqualByComparingTo("10.000");
        assertThat(productService.getById(p2.getId()).getStockQuantity()).isEqualByComparingTo("5.000");
    }

    @Test
    @DisplayName("6. updateFullInvoice: Unchanged items must generate zero stock movements")
    void testUpdateInvoice_UnchangedItems_GeneratesZeroMovement() {
        Product p = createGoodsProduct("Widget Gamma", BigDecimal.valueOf(20.000));

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.PAID);
        req.setPaid(true);
        req.setItems(List.of(buildRequestItem(p.getId(), p.getName(), 5, BigDecimal.valueOf(100.00))));

        Invoice created = invoiceService.createInvoice(req);
        int movementsBefore = stockMovementRepo.findByProductIdAndFirmIdOrderByCreatedAtDesc(p.getId(), testFirmId).size();

        // Re-save with same items, only note changed
        InvoiceUpdateRequest updateReq = new InvoiceUpdateRequest();
        updateReq.setCustomerId(testCustomer.getId());
        updateReq.setStatus(InvoiceStatus.PAID);
        updateReq.setPaid(true);
        updateReq.setCustomerNote("Updated customer note only");
        updateReq.setItems(List.of(buildRequestItem(p.getId(), p.getName(), 5, BigDecimal.valueOf(100.00))));

        invoiceService.updateFullInvoice(created.getId(), updateReq);

        int movementsAfter = stockMovementRepo.findByProductIdAndFirmIdOrderByCreatedAtDesc(p.getId(), testFirmId).size();
        assertThat(movementsAfter).isEqualTo(movementsBefore);
        assertThat(productService.getById(p.getId()).getStockQuantity()).isEqualByComparingTo("15.000");
    }

    // =========================================================================
    // Finding #2: deleteInvoice() Draft vs Active
    // =========================================================================

    @Test
    @DisplayName("7. deleteInvoice: Deleting DRAFT invoice must NOT restore stock")
    void testDeleteDraftInvoice_DoesNotRestoreStockOrRecordMovement() {
        Product p = createGoodsProduct("Draft Item", BigDecimal.valueOf(10.000));

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.DRAFT);
        req.setPaid(false);
        req.setItems(List.of(buildRequestItem(p.getId(), p.getName(), 4, BigDecimal.valueOf(100.00))));

        Invoice draft = invoiceService.createInvoice(req);
        assertThat(draft.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(productService.getById(p.getId()).getStockQuantity()).isEqualByComparingTo("10.000");

        // Delete draft
        invoiceService.delete(draft.getId());

        // Stock MUST stay at 10, NOT become 14!
        Product afterDelete = productService.getById(p.getId());
        assertThat(afterDelete.getStockQuantity()).isEqualByComparingTo("10.000");

        List<StockMovement> movements = stockMovementRepo.findByProductIdAndFirmIdOrderByCreatedAtDesc(p.getId(), testFirmId);
        assertThat(movements).hasSize(1); // Only INITIAL_STOCK
        assertThat(movements.get(0).getMovementType()).isEqualTo("INITIAL_STOCK");
    }

    @Test
    @DisplayName("8. deleteInvoice: Deleting ACTIVE invoice must restore stock")
    void testDeleteActiveInvoice_RestoresStockAndRecordsMovement() {
        Product p = createGoodsProduct("Active Item", BigDecimal.valueOf(10.000));

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.PAID);
        req.setPaid(true);
        req.setItems(List.of(buildRequestItem(p.getId(), p.getName(), 4, BigDecimal.valueOf(100.00))));

        Invoice inv = invoiceService.createInvoice(req);
        assertThat(productService.getById(p.getId()).getStockQuantity()).isEqualByComparingTo("6.000");

        // Delete active invoice
        invoiceService.delete(inv.getId());

        Product afterDelete = productService.getById(p.getId());
        assertThat(afterDelete.getStockQuantity()).isEqualByComparingTo("10.000");

        List<StockMovement> movements = stockMovementRepo.findByProductIdAndFirmIdOrderByCreatedAtDesc(p.getId(), testFirmId);
        assertThat(movements.get(0).getMovementType()).isEqualTo("INVOICE_CANCELLED");
        assertThat(movements.get(0).getQuantityChange()).isEqualByComparingTo("4.000");
    }

    // =========================================================================
    // Finding #8: ACTIVE -> DRAFT -> ACTIVE
    // =========================================================================

    @Test
    @DisplayName("9. Status transition: ACTIVE -> DRAFT restores stock, and DRAFT -> ACTIVE deducts stock once")
    void testInvoiceStatusTransition_ActiveToDraft_RestoresStock_And_DraftToActive_DeductsStockOnce() {
        Product p = createGoodsProduct("Status Item", BigDecimal.valueOf(10.000));

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.PAID);
        req.setPaid(true);
        req.setItems(List.of(buildRequestItem(p.getId(), p.getName(), 4, BigDecimal.valueOf(100.00))));

        Invoice inv = invoiceService.createInvoice(req);
        assertThat(productService.getById(p.getId()).getStockQuantity()).isEqualByComparingTo("6.000");

        // Demote to DRAFT
        invoiceService.updateStatus(inv.getId(), InvoiceStatus.DRAFT);
        assertThat(productService.getById(p.getId()).getStockQuantity()).isEqualByComparingTo("10.000");

        // Promote back to PAID
        invoiceService.updateStatus(inv.getId(), InvoiceStatus.PAID);
        assertThat(productService.getById(p.getId()).getStockQuantity()).isEqualByComparingTo("6.000");
    }

    // =========================================================================
    // Finding #3: Insufficient Stock & Ledger Equation Invariant
    // =========================================================================

    @Test
    @DisplayName("10. Insufficient Stock (AUDIT-026): Rejects in strict mode; in negative-stock mode deducts full quantity and restores correctly")
    void testInsufficientStockSale_PreservesLedgerEquation_AndOnlyRestoresPhysicallyDeductedQuantityOnCancel() {
        Product p = createGoodsProduct("Scarce Item", BigDecimal.valueOf(5.000));

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.PAID);
        req.setPaid(true);
        req.setItems(List.of(buildRequestItem(p.getId(), p.getName(), 8, BigDecimal.valueOf(100.00))));

        // 1. In default strict mode, sale exceeding stock of 5 is rejected and rolled back
        org.junit.jupiter.api.Assertions.assertThrows(
                com.billing.simple.billsoft.exception.InsufficientStockException.class,
                () -> invoiceService.createInvoice(req)
        );
        assertThat(productService.getById(p.getId()).getStockQuantity()).isEqualByComparingTo("5.000");

        // 2. Enable negative stock for firm
        appConfigRepo.save(new com.billing.simple.billsoft.entities.AppConfig("INVENTORY_ALLOW_NEGATIVE_STOCK_" + testFirmId, "true"));

        Invoice inv = invoiceService.createInvoice(req);

        // Stock must be -3.000 (full requested 8 deducted, no clamping!)
        Product afterSale = productService.getById(p.getId());
        assertThat(afterSale.getStockQuantity()).isEqualByComparingTo("-3.000");

        // Check ledger movement represents full -8.000
        List<StockMovement> movements = stockMovementRepo.findByProductIdAndFirmIdOrderByCreatedAtDesc(p.getId(), testFirmId);
        StockMovement saleMov = movements.get(0);
        assertThat(saleMov.getMovementType()).isEqualTo("INVOICE_SALE");
        assertThat(saleMov.getPreviousStock()).isEqualByComparingTo("5.000");
        assertThat(saleMov.getQuantityChange()).isEqualByComparingTo("-8.000");
        assertThat(saleMov.getNewStock()).isEqualByComparingTo("-3.000");
        assertThat(saleMov.getPreviousStock().add(saleMov.getQuantityChange())).isEqualByComparingTo(saleMov.getNewStock());

        // Cancel the invoice -> MUST restore full 8.000 back to 5.000
        invoiceService.updateStatus(inv.getId(), InvoiceStatus.CANCELLED);

        Product afterCancel = productService.getById(p.getId());
        assertThat(afterCancel.getStockQuantity()).isEqualByComparingTo("5.000");

        StockMovement cancelMov = stockMovementRepo.findByProductIdAndFirmIdOrderByCreatedAtDesc(p.getId(), testFirmId).get(0);
        assertThat(cancelMov.getMovementType()).isEqualTo("INVOICE_CANCELLED");
        assertThat(cancelMov.getQuantityChange()).isEqualByComparingTo("8.000");
        assertThat(cancelMov.getPreviousStock()).isEqualByComparingTo("-3.000");
        assertThat(cancelMov.getNewStock()).isEqualByComparingTo("5.000");
        assertThat(cancelMov.getPreviousStock().add(cancelMov.getQuantityChange())).isEqualByComparingTo(cancelMov.getNewStock());
    }

    // =========================================================================
    // Finding #10: Sales Returns limits & validation
    // =========================================================================

    @Test
    @DisplayName("11. SalesReturn: Exceeding invoice quantity must be rejected")
    void testSalesReturn_ExceedingInvoiceQty_RejectedWithBadRequest() {
        Product p = createGoodsProduct("Returnable Item", BigDecimal.valueOf(10.000));

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.PAID);
        req.setPaid(true);
        req.setItems(List.of(buildRequestItem(p.getId(), p.getName(), 3, BigDecimal.valueOf(100.00))));

        Invoice inv = invoiceService.createInvoice(req);

        SalesReturnRequest returnReq = new SalesReturnRequest();
        SalesReturnRequest.SalesReturnItemRequest itemReq = new SalesReturnRequest.SalesReturnItemRequest();
        itemReq.setInvoiceItemId(inv.getItems().get(0).getId());
        itemReq.setProductId(p.getId());
        itemReq.setReturnQty(5); // Sold 3, requesting return of 5
        returnReq.setItems(List.of(itemReq));

        assertThatThrownBy(() -> invoiceService.createSalesReturn(inv.getId(), returnReq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    @DisplayName("12. SalesReturn: Repeated returns exceeding remaining quantity must be rejected")
    void testSalesReturn_RepeatedReturnsExceedingRemaining_RejectedWithBadRequest() {
        Product p = createGoodsProduct("Returnable Item 2", BigDecimal.valueOf(10.000));

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.PAID);
        req.setPaid(true);
        req.setItems(List.of(buildRequestItem(p.getId(), p.getName(), 5, BigDecimal.valueOf(100.00))));

        Invoice inv = invoiceService.createInvoice(req);

        // First return 3
        SalesReturnRequest ret1 = new SalesReturnRequest();
        SalesReturnRequest.SalesReturnItemRequest itemReq1 = new SalesReturnRequest.SalesReturnItemRequest();
        itemReq1.setInvoiceItemId(inv.getItems().get(0).getId());
        itemReq1.setProductId(p.getId());
        itemReq1.setReturnQty(3);
        ret1.setItems(List.of(itemReq1));
        invoiceService.createSalesReturn(inv.getId(), ret1);

        // Second return attempt 3 (only 2 remaining)
        SalesReturnRequest ret2 = new SalesReturnRequest();
        SalesReturnRequest.SalesReturnItemRequest itemReq2 = new SalesReturnRequest.SalesReturnItemRequest();
        itemReq2.setInvoiceItemId(inv.getItems().get(0).getId());
        itemReq2.setProductId(p.getId());
        itemReq2.setReturnQty(3);
        ret2.setItems(List.of(itemReq2));

        assertThatThrownBy(() -> invoiceService.createSalesReturn(inv.getId(), ret2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds");
    }

    // =========================================================================
    // Finding #6: Tenant Security in Sales Return
    // =========================================================================

    @Test
    @DisplayName("13. SalesReturn: Missing tenant context must fail closed")
    void testSalesReturn_WithoutTenantContext_ThrowsTenantSecurityException() {
        TenantContext.clear();
        SalesReturnRequest ret = new SalesReturnRequest();
        ret.setItems(List.of(new SalesReturnRequest.SalesReturnItemRequest()));

        assertThatThrownBy(() -> invoiceService.createSalesReturn(999L, ret))
                .isInstanceOf(TenantSecurityException.class);
    }

    // =========================================================================
    // Finding #9 & Interaction: Sales Returns + updateFullInvoice + Cancel
    // =========================================================================

    @Test
    @DisplayName("14. SalesReturn + Invoice Update + Cancel: Prevents reducing below returned and avoids double restoration")
    void testInvoiceUpdate_WithPriorSalesReturns_PreservesReturnLedgerAndLimitsReduction() {
        Product p = createGoodsProduct("Interacted Item", BigDecimal.valueOf(20.000));

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.PAID);
        req.setPaid(true);
        req.setItems(List.of(buildRequestItem(p.getId(), p.getName(), 10, BigDecimal.valueOf(100.00))));

        Invoice inv = invoiceService.createInvoice(req);
        assertThat(productService.getById(p.getId()).getStockQuantity()).isEqualByComparingTo("10.000");

        // Customer returns 4 items
        SalesReturnRequest returnReq = new SalesReturnRequest();
        SalesReturnRequest.SalesReturnItemRequest itemReq = new SalesReturnRequest.SalesReturnItemRequest();
        itemReq.setInvoiceItemId(inv.getItems().get(0).getId());
        itemReq.setProductId(p.getId());
        itemReq.setReturnQty(4);
        returnReq.setItems(List.of(itemReq));
        invoiceService.createSalesReturn(inv.getId(), returnReq);

        // Stock restored +4 -> now 14
        assertThat(productService.getById(p.getId()).getStockQuantity()).isEqualByComparingTo("14.000");

        // Attempt to update invoice with quantity = 2 (less than 4 returned) -> must be rejected
        InvoiceUpdateRequest invalidUpdate = new InvoiceUpdateRequest();
        invalidUpdate.setCustomerId(testCustomer.getId());
        invalidUpdate.setStatus(InvoiceStatus.PAID);
        invalidUpdate.setPaid(true);
        invalidUpdate.setItems(List.of(buildRequestItem(p.getId(), p.getName(), 2, BigDecimal.valueOf(100.00))));

        assertThatThrownBy(() -> invoiceService.updateFullInvoice(inv.getId(), invalidUpdate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("returned");

        // Valid update: reduce quantity from 10 to 7
        InvoiceUpdateRequest validUpdate = new InvoiceUpdateRequest();
        validUpdate.setCustomerId(testCustomer.getId());
        validUpdate.setStatus(InvoiceStatus.PAID);
        validUpdate.setPaid(true);
        validUpdate.setItems(List.of(buildRequestItem(p.getId(), p.getName(), 7, BigDecimal.valueOf(100.00))));

        invoiceService.updateFullInvoice(inv.getId(), validUpdate);

        // Invoiced qty is now 7. Previously 10 was deducted. Delta is 3 restored to stock -> 14 + 3 = 17
        assertThat(productService.getById(p.getId()).getStockQuantity()).isEqualByComparingTo("17.000");

        // Now cancel invoice: net unreturned is 7 - 4 = 3.
        // It must restore ONLY 3, so stock becomes 17 + 3 = 20 (the initial stock)!
        invoiceService.updateStatus(inv.getId(), InvoiceStatus.CANCELLED);
        assertThat(productService.getById(p.getId()).getStockQuantity()).isEqualByComparingTo("20.000");
    }

    // =========================================================================
    // Finding #7: Product Deletion Safeguard
    // =========================================================================

    @Test
    @DisplayName("15. Product Delete: Product with existing invoice history is archived instead of crashing with FK violation")
    void testProductDelete_WithExistingInvoices_GracefullyArchivesInsteadOfCrashing() {
        Product p = createGoodsProduct("Historic Item", BigDecimal.valueOf(10.000));

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.PAID);
        req.setPaid(true);
        req.setItems(List.of(buildRequestItem(p.getId(), p.getName(), 2, BigDecimal.valueOf(100.00))));

        invoiceService.createInvoice(req);

        // Delete product
        boolean deleted = productService.delete(p.getId());
        assertThat(deleted).isTrue();

        Product reloaded = productRepo.findById(p.getId()).orElse(null);
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getIsArchived()).isTrue();
    }

    // =========================================================================
    // Finding #4: Concurrency Protection
    // =========================================================================

    @Test
    @DisplayName("16. Concurrency: 10 concurrent threads deducting stock never suffer lost updates")
    void testConcurrentStockDeductions_NoLostUpdates() throws Exception {
        Product p = createGoodsProduct("Concurrent Item", BigDecimal.valueOf(100.000));

        int threadCount = 10;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.atomic.AtomicInteger successes = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadIdx = i;
            executor.submit(() -> {
                try {
                    TenantContext.setCurrentFirmId(testFirmId);
                    InvoiceRequest req = new InvoiceRequest();
                    req.setInvoiceNumber("CONC-INV-" + threadIdx);
                    req.setCustomerId(testCustomer.getId());
                    req.setStatus(InvoiceStatus.PAID);
                    req.setPaid(true);
                    req.setItems(List.of(buildRequestItem(p.getId(), p.getName(), 5, BigDecimal.valueOf(10.00))));
                    invoiceService.createInvoice(req);
                    successes.incrementAndGet();
                } catch (Throwable t) {
                    System.err.println("CONCURRENT TEST EXCEPTION: " + t.getClass().getName() + " -> " + t.getMessage());
                    if (t.getCause() != null) {
                        System.err.println("  CAUSE: " + t.getCause().getClass().getName() + " -> " + t.getCause().getMessage());
                    }
                } finally {
                    TenantContext.clear();
                    latch.countDown();
                }
            });
        }

        latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successes.get()).isEqualTo(threadCount);
        // Initial stock 100 - (10 threads * 5 units) = 50.000
        Product reloaded = productService.getById(p.getId());
        assertThat(reloaded.getStockQuantity()).isEqualByComparingTo("50.000");

        // Verify ledger total movements
        List<StockMovement> movements = stockMovementRepo.findByProductIdAndFirmIdOrderByCreatedAtDesc(p.getId(), testFirmId);
        BigDecimal totalDeducted = movements.stream()
                .filter(m -> "INVOICE_SALE".equals(m.getMovementType()))
                .map(StockMovement::getQuantityChange)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalDeducted).isEqualByComparingTo("-50.000");
    }
}
