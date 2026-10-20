package com.billing.simple.billsoft.regression.billing;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.dtos.InvoiceUpdateRequest;
import com.billing.simple.billsoft.dtos.migration.InvoiceMigrationDTOs.*;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.billing.simple.billsoft.service.InvoiceMigrationService;
import com.billing.simple.billsoft.service.InvoiceService;
import com.billing.simple.billsoft.service.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class CrossFirmInvoiceMigrationIntegrationTest {

    @Autowired
    private InvoiceMigrationService migrationService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private ProductService productService;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private StockMovementRepository stockMovementRepo;

    @Autowired
    private InvoicePaymentRepository invoicePaymentRepo;

    @Autowired
    private SalesReturnRepository salesReturnRepo;

    @Autowired
    private FirmDetailsRepository firmRepo;

    @Autowired
    private InvoiceMigrationRecordRepository migrationRecordRepo;

    private Long firmAId;
    private Long firmBId;
    private Long firmCId;

    private Customer customerA;
    private Product productA;

    @BeforeEach
    void setUp() {
        FirmDetails firmA = new FirmDetails();
        firmA.setFirmName("Firm Alpha");
        firmA.setGstin("27AAAAA1111A1Z1");
        firmA = firmRepo.save(firmA);

        FirmDetails firmB = new FirmDetails();
        firmB.setFirmName("Firm Beta");
        firmB.setGstin("27BBBBB2222B1Z2");
        firmB = firmRepo.save(firmB);

        FirmDetails firmC = new FirmDetails();
        firmC.setFirmName("Firm Gamma");
        firmC.setGstin("27CCCCC3333C1Z3");
        firmC = firmRepo.save(firmC);

        firmAId = firmA.getId();
        firmBId = firmB.getId();
        firmCId = firmC.getId();

        TenantContext.setCurrentFirmId(firmAId);

        customerA = customerRepo.save(Customer.builder()
                .name("Acme Retailers")
                .phone("9876543210")
                .gstin("27ACME1234F1Z9")
                .address("Mumbai, MH")
                .firmId(firmAId)
                .build());

        productA = productRepo.save(Product.builder()
                .name("Widget Pro")
                .sku("WID-PRO-01")
                .price(new BigDecimal("500.00"))
                .stockQuantity(new BigDecimal("100.000"))
                .firmId(firmAId)
                .itemType("GOODS")
                .build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Invoice createTestInvoiceInFirmA() {
        TenantContext.setCurrentFirmId(firmAId);
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(customerA.getId());
        req.setInvoiceDate(LocalDateTime.now().toString());
        req.setStatus(InvoiceStatus.UNPAID);
        req.setPaid(false);

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(productA.getId());
        item.setProductName(productA.getName());
        item.setQty(10);
        item.setPricePerUnit(new BigDecimal("500.00"));
        item.setGstPercent(new BigDecimal("18.00"));
        req.setItems(Collections.singletonList(item));

        return invoiceService.createInvoice(req);
    }

    @Test
    void testForwardMigration_Success_InventoryReversedInSource_DeductedInTarget_CustomerResolved() {
        Invoice invoice = createTestInvoiceInFirmA();
        Long invId = invoice.getId();
        String originalInvNo = invoice.getInvoiceNumber();

        // Verify Firm A initial stock deduction (100 - 10 = 90)
        Product pAAfterSale = productRepo.findById(productA.getId()).orElseThrow();
        assertEquals(new BigDecimal("90.000"), pAAfterSale.getStockQuantity());

        // Create matching product and customer in Firm B
        Product productB = productRepo.save(Product.builder()
                .name("Widget Pro")
                .sku("WID-PRO-01")
                .price(new BigDecimal("550.00"))
                .stockQuantity(new BigDecimal("50.000"))
                .firmId(firmBId)
                .itemType("GOODS")
                .build());

        Customer customerB = customerRepo.save(Customer.builder()
                .name("Acme Retailers")
                .phone("9876543210")
                .gstin("27ACME1234F1Z9")
                .firmId(firmBId)
                .build());

        // Preview Migration
        InvoiceMigrationPreviewResponse preview = migrationService.previewMigration(invId, firmBId);
        assertTrue(preview.isEligible());
        assertEquals("EXACT_MATCH", preview.getCustomerResolution().getMatchType());
        assertEquals(customerB.getId(), preview.getCustomerResolution().getMatchedCustomer().getCustomerId());

        // Execute Migration A -> B
        InvoiceMigrationRequest migReq = InvoiceMigrationRequest.builder()
                .targetFirmId(firmBId)
                .resolvedCustomerId(customerB.getId())
                .createCustomerIfMissing(false)
                .build();

        InvoiceMigrationResponse resp = migrationService.migrateInvoice(invId, migReq);
        assertNotNull(resp);
        assertEquals(firmBId, resp.getTargetFirmId());
        assertNotNull(resp.getTargetInvoiceNumber());

        // Verify Firm A stock was restored (+10 back to 100)
        Product pARestored = productRepo.findById(productA.getId()).orElseThrow();
        assertEquals(new BigDecimal("100.000"), pARestored.getStockQuantity());

        // Verify Firm B stock was deducted (50 - 10 = 40)
        Product pBDeducted = productRepo.findById(productB.getId()).orElseThrow();
        assertEquals(new BigDecimal("40.000"), pBDeducted.getStockQuantity());

        // Verify migrated invoice properties in Firm B
        Invoice migratedInv = invoiceRepo.findById(invId).orElseThrow();
        assertEquals(firmBId, migratedInv.getFirmId());
        assertEquals(customerB.getId(), migratedInv.getCustomer().getId());
        assertEquals(new BigDecimal("500.00"), migratedInv.getItems().get(0).getPricePerUnit()); // Frozen price preserved

        // Verify migration history record
        List<InvoiceMigrationRecord> history = migrationRecordRepo.findByInvoiceIdOrderByCreatedAtDesc(invId);
        assertEquals(1, history.size());
        assertEquals("ACTIVE", history.get(0).getStatus());
        assertEquals(originalInvNo, history.get(0).getSourceInvoiceNumber());
        assertEquals(resp.getTargetInvoiceNumber(), history.get(0).getTargetInvoiceNumber());
    }

    @Test
    void testForwardMigration_AutoCreatesUncategorizedProduct_WhenNoTargetProductExists() {
        Invoice invoice = createTestInvoiceInFirmA();
        Long invId = invoice.getId();

        // No product exists in Firm B
        InvoiceMigrationRequest migReq = InvoiceMigrationRequest.builder()
                .targetFirmId(firmBId)
                .createCustomerIfMissing(true)
                .build();

        InvoiceMigrationResponse resp = migrationService.migrateInvoice(invId, migReq);
        assertNotNull(resp);

        // Verify invoice in Firm B references the newly auto-created Product with category UNCATEGORIZED
        Invoice migratedInv = invoiceRepo.findById(invId).orElseThrow();
        assertEquals(firmBId, migratedInv.getFirmId());
        assertNotNull(migratedInv.getItems().get(0).getProduct());
        assertEquals("Widget Pro", migratedInv.getItems().get(0).getProductName());

        Product createdInB = migratedInv.getItems().get(0).getProduct();
        assertEquals(firmBId, createdInB.getFirmId());
        assertEquals("UNCATEGORIZED", createdInB.getCategory());
        assertEquals("Widget Pro", createdInB.getName());

        // Source Firm A inventory was still restored (+10 to 100)
        Product pARestored = productRepo.findById(productA.getId()).orElseThrow();
        assertEquals(new BigDecimal("100.000"), pARestored.getStockQuantity());
    }

    @Test
    void testForwardMigration_BlockedWhenInvoiceHasPayments() {
        Invoice invoice = createTestInvoiceInFirmA();
        Long invId = invoice.getId();

        // Add payment to invoice
        invoicePaymentRepo.save(InvoicePayment.builder()
                .invoiceId(invId)
                .firmId(firmAId)
                .amount(new BigDecimal("100.00"))
                .paymentMode("CASH")
                .paymentDate(java.time.LocalDate.now())
                .build());

        InvoiceMigrationRequest migReq = InvoiceMigrationRequest.builder()
                .targetFirmId(firmBId)
                .build();

        assertThrows(IllegalStateException.class, () -> migrationService.migrateInvoice(invId, migReq));
    }

    @Test
    void testForwardMigration_BlockedWhenInvoiceHasReturns() {
        Invoice invoice = createTestInvoiceInFirmA();
        Long invId = invoice.getId();

        // Add sales return to invoice
        salesReturnRepo.save(SalesReturn.builder()
                .invoice(invoice)
                .firmId(firmAId)
                .returnNumber("RET-001")
                .totalRefundAmount(new BigDecimal("500.00"))
                .build());

        InvoiceMigrationRequest migReq = InvoiceMigrationRequest.builder()
                .targetFirmId(firmBId)
                .build();

        assertThrows(IllegalStateException.class, () -> migrationService.migrateInvoice(invId, migReq));
    }

    @Test
    void testForwardMigration_BlockedWhenTargetFirmIsSame() {
        Invoice invoice = createTestInvoiceInFirmA();
        Long invId = invoice.getId();

        InvoiceMigrationRequest migReq = InvoiceMigrationRequest.builder()
                .targetFirmId(firmAId)
                .build();

        assertThrows(IllegalStateException.class, () -> migrationService.migrateInvoice(invId, migReq));
    }

    @Test
    void testRevertMigration_RestoresExactSnapshot_AllocatesNewMonotonicNumber() {
        Invoice invoice = createTestInvoiceInFirmA();
        Long invId = invoice.getId();
        String originalInvNo = invoice.getInvoiceNumber();

        // Migrate A -> B
        InvoiceMigrationRequest migReq = InvoiceMigrationRequest.builder()
                .targetFirmId(firmBId)
                .createCustomerIfMissing(true)
                .build();
        InvoiceMigrationResponse migResp = migrationService.migrateInvoice(invId, migReq);
        String firmBInvNo = migResp.getTargetInvoiceNumber();

        // Now Revert B -> A
        InvoiceMigrationResponse revertResp = migrationService.revertMigration(invId);
        assertNotNull(revertResp);
        assertEquals(firmAId, revertResp.getTargetFirmId());
        assertTrue(revertResp.getIsRevert());

        // New sequence allocated in Firm A (neither originalInvNo nor firmBInvNo reused)
        String restoredInvNo = revertResp.getTargetInvoiceNumber();
        assertNotEquals(originalInvNo, restoredInvNo);
        assertNotEquals(firmBInvNo, restoredInvNo);

        // Verify Invoice in Firm A
        Invoice restoredInv = invoiceRepo.findById(invId).orElseThrow();
        assertEquals(firmAId, restoredInv.getFirmId());
        assertEquals(customerA.getId(), restoredInv.getCustomer().getId());
        assertEquals(productA.getId(), restoredInv.getItems().get(0).getProduct().getId());

        // Stock in Firm A is deducted again (100 - 10 = 90)
        Product pAFinal = productRepo.findById(productA.getId()).orElseThrow();
        assertEquals(new BigDecimal("90.000"), pAFinal.getStockQuantity());

        // History shows 2 entries (1 forward SUPERSEDED/REVERTED, 1 revert ACTIVE)
        List<InvoiceMigrationRecord> history = migrationRecordRepo.findByInvoiceIdOrderByCreatedAtDesc(invId);
        assertEquals(2, history.size());
        assertEquals("ACTIVE", history.get(0).getStatus());
        assertTrue(history.get(0).getIsRevert());
    }

    @Test
    void testRevertMigration_WhenTargetInvoiceWasEdited_RestoresSourceSnapshotAndReversesTargetLedger() {
        Invoice invoice = createTestInvoiceInFirmA();
        Long invId = invoice.getId();

        // Create product in Firm B
        Product productB = productRepo.save(Product.builder()
                .name("Widget Pro")
                .sku("WID-PRO-01")
                .price(new BigDecimal("500.00"))
                .stockQuantity(new BigDecimal("100.000"))
                .firmId(firmBId)
                .itemType("GOODS")
                .build());

        // Migrate A -> B (deducts 10 from B, stock = 90)
        InvoiceMigrationRequest migReq = InvoiceMigrationRequest.builder()
                .targetFirmId(firmBId)
                .createCustomerIfMissing(true)
                .build();
        migrationService.migrateInvoice(invId, migReq);

        // Edit invoice in Firm B: change qty from 10 to 25 (deducts 15 more from B, stock = 75)
        TenantContext.setCurrentFirmId(firmBId);
        InvoiceUpdateRequest updateReq = new InvoiceUpdateRequest();
        updateReq.setCustomerId(invoiceRepo.findById(invId).orElseThrow().getCustomer().getId());
        updateReq.setInvoiceDate(LocalDateTime.now().toString());
        updateReq.setStatus(InvoiceStatus.UNPAID);

        InvoiceRequestItem editedItem = new InvoiceRequestItem();
        editedItem.setProductId(productB.getId());
        editedItem.setProductName(productB.getName());
        editedItem.setQty(25);
        editedItem.setPricePerUnit(new BigDecimal("500.00"));
        updateReq.setItems(Collections.singletonList(editedItem));

        invoiceService.updateFullInvoice(invId, updateReq);

        Product pBAfterEdit = productRepo.findById(productB.getId()).orElseThrow();
        assertEquals(new BigDecimal("75.000"), pBAfterEdit.getStockQuantity());

        // Revert B -> A: Must reverse all 25 units from B (restoring B to 100) and restore original 10 units in A
        InvoiceMigrationResponse revertResp = migrationService.revertMigration(invId);
        assertNotNull(revertResp);

        Product pBRestored = productRepo.findById(productB.getId()).orElseThrow();
        assertEquals(new BigDecimal("100.000"), pBRestored.getStockQuantity());

        Invoice restoredInv = invoiceRepo.findById(invId).orElseThrow();
        assertEquals(firmAId, restoredInv.getFirmId());
        assertEquals(10, restoredInv.getItems().get(0).getQty()); // Snapshot original 10 qty restored, not edited 25
    }

    @Test
    void testRevertMigration_BlockedWhenPaymentAddedInTargetFirm() {
        Invoice invoice = createTestInvoiceInFirmA();
        Long invId = invoice.getId();

        InvoiceMigrationRequest migReq = InvoiceMigrationRequest.builder()
                .targetFirmId(firmBId)
                .createCustomerIfMissing(true)
                .build();
        migrationService.migrateInvoice(invId, migReq);

        // Add payment in target firm
        invoicePaymentRepo.save(InvoicePayment.builder()
                .invoiceId(invId)
                .firmId(firmBId)
                .amount(new BigDecimal("200.00"))
                .paymentDate(java.time.LocalDate.now())
                .build());

        assertThrows(IllegalStateException.class, () -> migrationService.revertMigration(invId));
    }

    @Test
    void testMultiHopMigrationChain_A_to_B_to_A_to_C() {
        Invoice invoice = createTestInvoiceInFirmA();
        Long invId = invoice.getId();

        // 1. A -> B
        InvoiceMigrationResponse m1 = migrationService.migrateInvoice(invId,
                InvoiceMigrationRequest.builder().targetFirmId(firmBId).createCustomerIfMissing(true).build());
        assertEquals(firmBId, m1.getTargetFirmId());

        // 2. Revert B -> A
        InvoiceMigrationResponse r1 = migrationService.revertMigration(invId);
        assertEquals(firmAId, r1.getTargetFirmId());

        // 3. A -> C
        InvoiceMigrationResponse m2 = migrationService.migrateInvoice(invId,
                InvoiceMigrationRequest.builder().targetFirmId(firmCId).createCustomerIfMissing(true).build());
        assertEquals(firmCId, m2.getTargetFirmId());

        // Verify complete audit chain
        List<InvoiceMigrationRecord> history = migrationRecordRepo.findByInvoiceIdOrderByCreatedAtDesc(invId);
        assertEquals(3, history.size());
        assertEquals("ACTIVE", history.get(0).getStatus());
        assertEquals(firmCId, history.get(0).getTargetFirmId());
        assertEquals("SUPERSEDED", history.get(1).getStatus());
        assertEquals("REVERTED", history.get(2).getStatus());
    }

    @Test
    void testInvoiceUpdate_RejectsForeignProduct() {
        Invoice invoice = createTestInvoiceInFirmA();
        Long invId = invoice.getId();

        // Create product in Firm B
        Product foreignProduct = productRepo.save(Product.builder()
                .name("Foreign Gizmo")
                .sku("FOR-01")
                .price(new BigDecimal("300.00"))
                .stockQuantity(new BigDecimal("10.000"))
                .firmId(firmBId)
                .build());

        TenantContext.setCurrentFirmId(firmAId);
        InvoiceUpdateRequest updateReq = new InvoiceUpdateRequest();
        updateReq.setCustomerId(customerA.getId());
        updateReq.setInvoiceDate(LocalDateTime.now().toString());
        updateReq.setStatus(InvoiceStatus.UNPAID);

        InvoiceRequestItem foreignItem = new InvoiceRequestItem();
        foreignItem.setProductId(foreignProduct.getId());
        foreignItem.setProductName(foreignProduct.getName());
        foreignItem.setQty(1);
        foreignItem.setPricePerUnit(new BigDecimal("300.00"));
        updateReq.setItems(Collections.singletonList(foreignItem));

        // Must reject foreign product update
        assertThrows(TenantSecurityException.class, () -> invoiceService.updateFullInvoice(invId, updateReq));
    }

    @Test
    void testMigrationTransactionRollback_WhenExceptionThrownMidTransaction() {
        Invoice invoice = createTestInvoiceInFirmA();
        Long invId = invoice.getId();
        String originalInvNo = invoice.getInvoiceNumber();

        // Stock in Firm A was deducted to 90.000
        Product pAInitial = productRepo.findById(productA.getId()).orElseThrow();
        assertEquals(new BigDecimal("90.000"), pAInitial.getStockQuantity());

        long initialCustomerCountB = customerRepo.findByFirmId(firmBId).size();
        long initialMigrationRecordCount = migrationRecordRepo.count();

        // Attempt migration with an invalid resolved customer ID (causes exception inside transaction after step 4/5)
        InvoiceMigrationRequest migReq = InvoiceMigrationRequest.builder()
                .targetFirmId(firmBId)
                .resolvedCustomerId(999999L) // Non-existent customer in target firm
                .createCustomerIfMissing(false)
                .build();

        assertThrows(IllegalArgumentException.class, () -> migrationService.migrateInvoice(invId, migReq));

        // Verify COMPLETE ROLLBACK:
        // 1. Invoice is unchanged in Firm A
        Invoice rolledBackInv = invoiceRepo.findById(invId).orElseThrow();
        assertEquals(firmAId, rolledBackInv.getFirmId());
        assertEquals(originalInvNo, rolledBackInv.getInvoiceNumber());

        // 2. Stock in Firm A was rolled back (remains 90.000, not restored to 100)
        Product pAAfterRollback = productRepo.findById(productA.getId()).orElseThrow();
        assertEquals(new BigDecimal("90.000"), pAAfterRollback.getStockQuantity());

        // 3. No new customer created in Firm B
        assertEquals(initialCustomerCountB, customerRepo.findByFirmId(firmBId).size());

        // 4. No migration record persisted
        assertEquals(initialMigrationRecordCount, migrationRecordRepo.count());
    }

    @Test
    void testForwardMigration_MissingProductInTargetFirm_AutoCreatesUncategorizedProduct_AndRevertRestoresFirmAProduct() {
        Invoice invoice = createTestInvoiceInFirmA();
        Long invId = invoice.getId();
        String originalInvNo = invoice.getInvoiceNumber();

        // Target Firm B initially has NO products
        List<Product> productsInBBefore = productRepo.findByFirmId(firmBId);
        assertTrue(productsInBBefore.isEmpty(), "Firm B should have no products initially");

        // Execute Forward Migration A -> B with missing product and customer
        InvoiceMigrationRequest migReq = InvoiceMigrationRequest.builder()
                .targetFirmId(firmBId)
                .createCustomerIfMissing(true)
                .build();

        InvoiceMigrationResponse resp = migrationService.migrateInvoice(invId, migReq);
        assertNotNull(resp);
        assertEquals(firmBId, resp.getTargetFirmId());

        // Verify that a new Product was auto-created in Firm B with category UNCATEGORIZED
        List<Product> productsInBAfter = productRepo.findByFirmId(firmBId);
        assertEquals(1, productsInBAfter.size(), "Firm B should now have exactly 1 auto-created product");
        Product createdProdB = productsInBAfter.get(0);
        assertEquals(firmBId, createdProdB.getFirmId());
        assertEquals("Widget Pro", createdProdB.getName());
        assertEquals("UNCATEGORIZED", createdProdB.getCategory());
        assertEquals("WID-PRO-01", createdProdB.getSku());

        // Multi-tenant isolation: product B is unimported by default (inInventory = false)
        TenantContext.setCurrentFirmId(firmBId);
        List<Product> normalInventoryB = productService.getAll(firmBId);
        assertFalse(normalInventoryB.stream().anyMatch(p -> p.getId().equals(createdProdB.getId())), "Unimported product should not appear in normal inventory");

        List<Product> unimportedB = productService.getUnimportedProducts(firmBId);
        assertTrue(unimportedB.stream().anyMatch(p -> p.getId().equals(createdProdB.getId()) && "UNCATEGORIZED".equals(p.getCategory())), "Auto-created product should appear in unimported products list");

        TenantContext.setCurrentFirmId(firmAId);
        assertThrows(com.billing.simple.billsoft.security.TenantSecurityException.class, () -> productService.getUnimportedProducts(firmBId));

        // Verify migrated invoice item references the newly created Firm B product
        Invoice migratedInv = invoiceRepo.findById(invId).orElseThrow();
        assertNotNull(migratedInv.getItems().get(0).getProduct());
        assertEquals(createdProdB.getId(), migratedInv.getItems().get(0).getProduct().getId());

        // Verify Firm B product catalog stock state (0.000 non-negative bounded)
        Product pBStock = productRepo.findById(createdProdB.getId()).orElseThrow();
        assertEquals(new BigDecimal("0.000"), pBStock.getStockQuantity());

        // Now Execute Revert B -> A
        TenantContext.setCurrentFirmId(firmBId);
        InvoiceMigrationResponse revertResp = migrationService.revertMigration(invId);
        assertNotNull(revertResp);
        assertEquals(firmAId, revertResp.getTargetFirmId());

        // Verify Revert restores original Firm A product reference
        Invoice revertedInv = invoiceRepo.findById(invId).orElseThrow();
        assertEquals(firmAId, revertedInv.getFirmId());
        assertNotNull(revertedInv.getItems().get(0).getProduct());
        assertEquals(productA.getId(), revertedInv.getItems().get(0).getProduct().getId());

        // Verify Firm B product is NOT deleted (retains its catalog record in Firm B)
        Product pBAfterRevert = productRepo.findById(createdProdB.getId()).orElseThrow();
        assertNotNull(pBAfterRevert);
        assertEquals(firmBId, pBAfterRevert.getFirmId());
        assertEquals("UNCATEGORIZED", pBAfterRevert.getCategory());
    }
}
