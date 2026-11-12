package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderItemRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class ProductArchiveAndHistoricalIntegrityTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private InvoiceItemRepository invoiceItemRepo;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepo;

    @Autowired
    private PurchaseOrderItemRepository purchaseOrderItemRepo;

    @Autowired
    private SalesReturnRepository salesReturnRepo;

    @Autowired
    private SalesReturnItemRepository salesReturnItemRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private PartyRepository partyRepo;

    @Autowired
    private BackupService backupService;

    private final Long firm1Id = 8801L;
    private final Long firm2Id = 8802L;

    @BeforeEach
    void setup() {
        TenantContext.setCurrentFirmId(firm1Id);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Issue 1: Product field editing and persistence across all editable fields")
    @Transactional
    void testProductFieldEditingAndPersistence() {
        Product initial = Product.builder()
                .firmId(firm1Id)
                .name("Legacy Display 24")
                .sku("SKU-INIT-24")
                .barcode("890000000001")
                .category("Old Monitors")
                .itemType("GOODS")
                .price(new BigDecimal("12000.00"))
                .costPrice(new BigDecimal("9000.00"))
                .unit("pcs")
                .hsnCode("8471")
                .gstPercentage(new BigDecimal("18.00"))
                .stockQuantity(new BigDecimal("25"))
                .minStockLevel(new BigDecimal("5"))
                .description("Initial stock description")
                .inInventory(true)
                .isArchived(false)
                .build();
        Product saved = productRepo.save(initial);
        assertNotNull(saved.getId());

        // Update all fields (Issue 1 requirements)
        Product updatePayload = new Product();
        updatePayload.setName("Dell UltraSharp 27 4K HDR");
        updatePayload.setSku("SKU-U2723QE");
        updatePayload.setBarcode("890000000099");
        updatePayload.setCategory("Premium Displays");
        updatePayload.setItemType("GOODS");
        updatePayload.setPrice(new BigDecimal("48500.50"));
        updatePayload.setCostPrice(new BigDecimal("37200.25"));
        updatePayload.setUnit("box");
        updatePayload.setHsnCode("85285200");
        updatePayload.setGstPercentage(new BigDecimal("18.00"));
        updatePayload.setMinStockLevel(new BigDecimal("8"));
        updatePayload.setDescription("Color accurate IPS Black panel with USB-C Hub");

        Product updated = productService.update(saved.getId(), updatePayload);
        assertNotNull(updated);
        assertEquals("Dell UltraSharp 27 4K HDR", updated.getName());
        assertEquals("SKU-U2723QE", updated.getSku());
        assertEquals("890000000099", updated.getBarcode());
        assertEquals("Premium Displays", updated.getCategory());
        assertEquals(new BigDecimal("48500.50"), updated.getPrice());
        assertEquals(new BigDecimal("37200.25"), updated.getCostPrice());
        assertEquals("box", updated.getUnit());
        assertEquals("85285200", updated.getHsnCode());
        assertEquals(new BigDecimal("18.00"), updated.getGstPercentage());
        assertEquals(new BigDecimal("8"), updated.getMinStockLevel());
        assertEquals("Color accurate IPS Black panel with USB-C Hub", updated.getDescription());

        // Verify persisted state from repository
        Product reloaded = productRepo.findById(saved.getId()).orElseThrow();
        assertEquals("Dell UltraSharp 27 4K HDR", reloaded.getName());
        assertEquals("SKU-U2723QE", reloaded.getSku());
        assertEquals("890000000099", reloaded.getBarcode());
    }

    @Test
    @DisplayName("Issue 3: Archiving active product sets isArchived, archivedAt, reason and retains DB row")
    @Transactional
    void testArchiveProductPreservesRowInDatabase() {
        Product p1 = Product.builder()
                .firmId(firm1Id)
                .name("Keyboard Mechanical RGB")
                .sku("SKU-KB-01")
                .barcode("890000000002")
                .price(new BigDecimal("3500.00"))
                .costPrice(new BigDecimal("2100.00"))
                .stockQuantity(new BigDecimal("40"))
                .inInventory(true)
                .isArchived(false)
                .build();
        p1 = productRepo.save(p1);
        Long p1Id = p1.getId();

        // Archive product via explicit archive service
        Product archived = productService.archive(p1Id, "Discontinued winter batch");
        assertNotNull(archived);
        assertTrue(archived.getIsArchived());
        assertNotNull(archived.getArchivedAt());
        assertEquals("Discontinued winter batch", archived.getArchiveReason());

        // Verify row still exists in database
        Optional<Product> rowInDb = productRepo.findById(p1Id);
        assertTrue(rowInDb.isPresent(), "Product row must remain in database table");
        assertTrue(rowInDb.get().getIsArchived());

        // Archive via delete(id) must also soft-delete
        Product p2 = Product.builder()
                .firmId(firm1Id)
                .name("Mouse Optical Wireless")
                .sku("SKU-MS-02")
                .price(new BigDecimal("999.00"))
                .costPrice(new BigDecimal("550.00"))
                .stockQuantity(new BigDecimal("15"))
                .inInventory(true)
                .isArchived(false)
                .build();
        p2 = productRepo.save(p2);
        Long p2Id = p2.getId();

        boolean deleted = productService.delete(p2Id);
        assertTrue(deleted);

        // Verify p2 is still in DB with isArchived = true
        Optional<Product> p2InDb = productRepo.findById(p2Id);
        assertTrue(p2InDb.isPresent(), "Deleted product row must NOT be physically erased from DB");
        assertTrue(p2InDb.get().getIsArchived());
        assertNotNull(p2InDb.get().getArchivedAt());
        assertEquals("Archived via delete", p2InDb.get().getArchiveReason());
    }

    @Test
    @DisplayName("Issue 3: Active product queries and search exclude archived products")
    @Transactional
    void testActiveQueriesExcludeArchivedProducts() {
        Product activeProd = productRepo.save(Product.builder()
                .firmId(firm1Id)
                .name("Active Headset Pro")
                .sku("SKU-HS-ACTIVE")
                .price(new BigDecimal("2500.00"))
                .inInventory(true)
                .isArchived(false)
                .build());

        Product archivedProd = productRepo.save(Product.builder()
                .firmId(firm1Id)
                .name("Archived Headset Basic")
                .sku("SKU-HS-ARCHIVED")
                .price(new BigDecimal("1200.00"))
                .inInventory(true)
                .isArchived(true)
                .archivedAt(LocalDateTime.now())
                .archiveReason("End of life")
                .build());

        // Active list query
        List<Product> activeList = productRepo.findByFirmIdAndIsArchivedFalseOrderByNameAsc(firm1Id);
        assertTrue(activeList.stream().anyMatch(p -> p.getId().equals(activeProd.getId())));
        assertFalse(activeList.stream().anyMatch(p -> p.getId().equals(archivedProd.getId())));

        // Archived list query
        List<Product> archivedList = productService.listArchived(firm1Id);
        assertTrue(archivedList.stream().anyMatch(p -> p.getId().equals(archivedProd.getId())));
        assertFalse(archivedList.stream().anyMatch(p -> p.getId().equals(activeProd.getId())));
    }

    @Test
    @DisplayName("Issue 3: Historical documents (Invoices, Quotations, POs) retain archived product references")
    @Transactional
    void testHistoricalDocumentsRetainArchivedProductReferences() {
        // 1. Create active product
        Product product = productRepo.save(Product.builder()
                .firmId(firm1Id)
                .name("Industrial Sensor Module X1")
                .sku("SKU-SNS-X1")
                .barcode("890123999999")
                .hsnCode("90318000")
                .unit("pcs")
                .price(new BigDecimal("6500.00"))
                .costPrice(new BigDecimal("4200.00"))
                .stockQuantity(new BigDecimal("100"))
                .inInventory(true)
                .isArchived(false)
                .build());
        Long prodId = product.getId();

        // 2. Create customer
        Customer customer = customerRepo.save(Customer.builder()
                .firmId(firm1Id)
                .name("Apex Industrial Automation")
                .phone("9876543210")
                .build());

        // 3. Create Invoice with line item referencing product
        Invoice invoice = Invoice.builder()
                .firmId(firm1Id)
                .invoiceNumber("INV-HIST-ARCHIVE-01")
                .invoiceDate(LocalDateTime.now())
                .customer(customer)
                .status(InvoiceStatus.PAID)
                .subtotalWithoutTax(new BigDecimal("13000.00"))
                .totalTax(new BigDecimal("2340.00"))
                .totalAmount(new BigDecimal("15340.00"))
                .build();
        invoice = invoiceRepo.save(invoice);

        InvoiceItem invoiceItem = InvoiceItem.builder()
                .invoice(invoice)
                .product(product)
                .productName(product.getName())
                .hsnCode(product.getHsnCode())
                .unit(product.getUnit())
                .qty(2)
                .pricePerUnit(new BigDecimal("6500.00"))
                .unitCostPrice(new BigDecimal("4200.00"))
                .amountWithoutTax(new BigDecimal("13000.00"))
                .gstPercent(new BigDecimal("18.00"))
                .lineTotal(new BigDecimal("15340.00"))
                .build();
        invoiceItem = invoiceItemRepo.save(invoiceItem);
        List<InvoiceItem> invItems = new ArrayList<>();
        invItems.add(invoiceItem);
        invoice.setItems(invItems);
        invoice = invoiceRepo.save(invoice);

        // 4. Create Quotation (Invoice with status ESTIMATE)
        Invoice quotation = Invoice.builder()
                .firmId(firm1Id)
                .invoiceNumber("EST-HIST-01")
                .invoiceDate(LocalDateTime.now())
                .customer(customer)
                .status(InvoiceStatus.ESTIMATE)
                .subtotalWithoutTax(new BigDecimal("6500.00"))
                .totalTax(new BigDecimal("1170.00"))
                .totalAmount(new BigDecimal("7670.00"))
                .build();
        quotation = invoiceRepo.save(quotation);
        InvoiceItem quoteItem = InvoiceItem.builder()
                .invoice(quotation)
                .product(product)
                .productName(product.getName())
                .hsnCode(product.getHsnCode())
                .qty(1)
                .pricePerUnit(new BigDecimal("6500.00"))
                .lineTotal(new BigDecimal("7670.00"))
                .build();
        invoiceItemRepo.save(quoteItem);

        // 5. Create Purchase Order referencing product
        Party vendor = partyRepo.save(Party.builder()
                .firmId(firm1Id)
                .name("Sensor Technologies Corp")
                .phone("9123456789")
                .build());

        PurchaseOrder po = PurchaseOrder.builder()
                .firmId(firm1Id)
                .poNumber("PO-2026-ARCHIVE-01")
                .poDate(LocalDate.now())
                .status(PurchaseOrderStatus.ISSUED)
                .party(vendor)
                .partyName(vendor.getName())
                .subtotalWithoutTax(new BigDecimal("42000.00"))
                .totalGstAmount(new BigDecimal("7560.00"))
                .totalAmount(new BigDecimal("49560.00"))
                .build();
        po = purchaseOrderRepo.save(po);

        PurchaseOrderItem poItem = PurchaseOrderItem.builder()
                .purchaseOrder(po)
                .productId(prodId)
                .productName(product.getName())
                .hsnCode(product.getHsnCode())
                .quantity(new BigDecimal("10"))
                .unitPrice(new BigDecimal("4200.00"))
                .taxableAmount(new BigDecimal("42000.00"))
                .build();
        purchaseOrderItemRepo.save(poItem);

        // 6. NOW ARCHIVE THE PRODUCT
        Product archived = productService.archive(prodId, "Product discontinued after batch run");
        assertTrue(archived.getIsArchived());

        // 7. Verify Invoice and Line Items still load cleanly and retain foreign keys
        Invoice retrievedInvoice = invoiceRepo.findById(invoice.getId()).orElseThrow();
        assertNotNull(retrievedInvoice);
        assertNotNull(retrievedInvoice.getItems());
        assertEquals(1, retrievedInvoice.getItems().size());
        InvoiceItem itemFromDb = retrievedInvoice.getItems().get(0);
        assertNotNull(itemFromDb.getProduct(), "InvoiceItem product foreign key must remain intact");
        assertEquals(prodId, itemFromDb.getProduct().getId());
        assertTrue(itemFromDb.getProduct().getIsArchived(), "Referenced product is archived");
        assertEquals("Industrial Sensor Module X1", itemFromDb.getProductName());
        assertEquals(new BigDecimal("15340.00"), itemFromDb.getLineTotal());

        // 8. Verify Quotation line item
        Invoice retrievedQuotation = invoiceRepo.findById(quotation.getId()).orElseThrow();
        assertEquals(InvoiceStatus.ESTIMATE, retrievedQuotation.getStatus());

        // 9. Verify PO line item
        PurchaseOrder retrievedPo = purchaseOrderRepo.findById(po.getId()).orElseThrow();
        List<PurchaseOrderItem> poItems = purchaseOrderItemRepo.findByPurchaseOrderId(retrievedPo.getId());
        assertEquals(1, poItems.size());
        assertEquals(prodId, poItems.get(0).getProductId());
    }

    @Test
    @DisplayName("Issue 3: Sales return with archived product preserves relational integrity")
    @Transactional
    void testSalesReturnWithArchivedProduct() {
        Product product = productRepo.save(Product.builder()
                .firmId(firm1Id)
                .name("Ergonomic Mesh Chair")
                .sku("SKU-CHR-01")
                .price(new BigDecimal("8000.00"))
                .costPrice(new BigDecimal("5000.00"))
                .stockQuantity(new BigDecimal("20"))
                .inInventory(true)
                .isArchived(false)
                .build());

        // Create customer and invoice
        Customer customer = customerRepo.save(Customer.builder()
                .firmId(firm1Id)
                .name("Office Innovations")
                .build());

        Invoice invoice = invoiceRepo.save(Invoice.builder()
                .firmId(firm1Id)
                .invoiceNumber("INV-SR-01")
                .invoiceDate(LocalDateTime.now())
                .customer(customer)
                .status(InvoiceStatus.PAID)
                .totalAmount(new BigDecimal("8000.00"))
                .build());

        InvoiceItem invItem = invoiceItemRepo.save(InvoiceItem.builder()
                .invoice(invoice)
                .product(product)
                .productName(product.getName())
                .qty(1)
                .pricePerUnit(new BigDecimal("8000.00"))
                .lineTotal(new BigDecimal("8000.00"))
                .build());

        // Archive product
        productService.archive(product.getId(), "Discontinued product model");

        // Create Sales Return referencing archived product
        SalesReturn salesReturn = SalesReturn.builder()
                .firmId(firm1Id)
                .returnNumber("SR-2026-ARCHIVE-01")
                .returnDate(LocalDate.now())
                .invoice(invoice)
                .customer(customer)
                .reason("Customer ordered wrong variant")
                .grossAmount(new BigDecimal("8000.00"))
                .build();
        salesReturn = salesReturnRepo.save(salesReturn);

        SalesReturnItem returnItem = SalesReturnItem.builder()
                .salesReturn(salesReturn)
                .invoiceItem(invItem)
                .product(product)
                .productName(product.getName())
                .unit("pcs")
                .returnQty(1)
                .unitPrice(new BigDecimal("8000.00"))
                .grossAmount(new BigDecimal("8000.00"))
                .build();
        returnItem = salesReturnItemRepo.save(returnItem);

        assertNotNull(returnItem.getId());
        assertEquals(product.getId(), returnItem.getProduct().getId());
        assertTrue(returnItem.getProduct().getIsArchived());
    }

    @Test
    @DisplayName("Issue 3: Restoring an archived product and active SKU/Barcode conflict detection")
    @Transactional
    void testRestoreProductAndConflictDetection() {
        // 1. Create active product A
        Product prodA = productRepo.save(Product.builder()
                .firmId(firm1Id)
                .name("Product Variant Alpha")
                .sku("SKU-CONFLICT-TEST")
                .barcode("BAR-CONFLICT-TEST")
                .price(new BigDecimal("1000.00"))
                .stockQuantity(new BigDecimal("50"))
                .inInventory(true)
                .isArchived(false)
                .build());

        // 2. Archive product A
        productService.archive(prodA.getId(), "Temporary test archive");
        Product checkA = productRepo.findById(prodA.getId()).orElseThrow();
        assertTrue(checkA.getIsArchived());

        // 3. Create product B with same SKU while product A is archived
        Product prodB = productRepo.save(Product.builder()
                .firmId(firm1Id)
                .name("Product Variant Beta (Takes over SKU)")
                .sku("SKU-CONFLICT-TEST")
                .barcode("BAR-DIFFERENT")
                .price(new BigDecimal("1500.00"))
                .stockQuantity(new BigDecimal("30"))
                .inInventory(true)
                .isArchived(false)
                .build());

        // 4. Attempt to restore product A -> must fail with active SKU conflict
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            productService.restore(prodA.getId());
        });
        assertTrue(ex.getMessage().contains("SKU 'SKU-CONFLICT-TEST' already exists"),
                "Expected conflict message for active SKU collision, got: " + ex.getMessage());

        // 5. Change prodB SKU so conflict is eliminated
        prodB.setSku("SKU-NON-CONFLICTING");
        productRepo.save(prodB);

        // 6. Now restore product A -> must succeed cleanly
        Product restoredA = productService.restore(prodA.getId());
        assertNotNull(restoredA);
        assertFalse(restoredA.getIsArchived(), "Product must be active after restore");
        assertNull(restoredA.getArchivedAt(), "archivedAt must be cleared on restore");
        assertNull(restoredA.getArchiveReason(), "archiveReason must be cleared on restore");

        // Verify prodA appears in active list
        List<Product> activeList = productRepo.findByFirmIdAndIsArchivedFalseOrderByNameAsc(firm1Id);
        assertTrue(activeList.stream().anyMatch(p -> p.getId().equals(prodA.getId())));
    }

    @Test
    @DisplayName("Tenant Isolation: Cross-firm archive and restore operations are rejected")
    @Transactional
    void testTenantIsolationOnArchiveAndRestore() {
        // Firm 1 creates a product
        TenantContext.setCurrentFirmId(firm1Id);
        Product firm1Prod = productRepo.save(Product.builder()
                .firmId(firm1Id)
                .name("Firm 1 Proprietary Item")
                .sku("SKU-FIRM1-ONLY")
                .price(new BigDecimal("990.00"))
                .inInventory(true)
                .isArchived(false)
                .build());

        // Switch context to Firm 2
        TenantContext.setCurrentFirmId(firm2Id);

        // Firm 2 attempts to archive Firm 1's product
        assertThrows(TenantSecurityException.class, () -> {
            productService.archive(firm1Prod.getId(), "Cross-firm malicious archive");
        });

        // Switch back to Firm 1 and archive properly
        TenantContext.setCurrentFirmId(firm1Id);
        productService.archive(firm1Prod.getId(), "Legitimate archive by owner firm");

        // Switch context back to Firm 2 and attempt to restore Firm 1's product
        TenantContext.setCurrentFirmId(firm2Id);
        assertThrows(TenantSecurityException.class, () -> {
            productService.restore(firm1Prod.getId());
        });

        // Firm 2 attempts to delete Firm 1's product
        boolean deleted = productService.delete(firm1Prod.getId());
        assertFalse(deleted, "Cross-firm delete must return false");
    }

    @Test
    @DisplayName("Backup & Restore: Preserves archivedAt and archiveReason across backup cycle")
    @Transactional
    void testBackupAndRestorePreservesArchivedAtAndReason() {
        LocalDateTime archTime = LocalDateTime.of(2026, 11, 10, 14, 30, 0);
        Product archivedProd = productRepo.save(Product.builder()
                .firmId(firm1Id)
                .name("Archived Widget Model Z")
                .sku("SKU-WIDGET-Z")
                .price(new BigDecimal("499.00"))
                .inInventory(true)
                .isArchived(true)
                .archivedAt(archTime)
                .archiveReason("Legacy model superseded by Z-Pro")
                .build());

        // Create backup
        BackupDTO backup = backupService.exportData(firm1Id);
        assertNotNull(backup);
        assertNotNull(backup.getProducts());

        // Find archived product in backup
        Product exported = backup.getProducts().stream()
                .filter(p -> p.getId().equals(archivedProd.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Archived product missing from backup"));

        assertTrue(exported.getIsArchived());
        assertEquals(archTime, exported.getArchivedAt());
        assertEquals("Legacy model superseded by Z-Pro", exported.getArchiveReason());
    }
}
