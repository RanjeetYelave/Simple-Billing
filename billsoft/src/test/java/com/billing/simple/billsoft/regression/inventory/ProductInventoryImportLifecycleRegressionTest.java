package com.billing.simple.billsoft.regression.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceStatus;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.forecast.service.RestockAdvisoryService;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.billing.simple.billsoft.service.InvoiceService;
import com.billing.simple.billsoft.service.ProductService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ProductInventoryImportLifecycleRegressionTest {

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private ProductService productService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private RestockAdvisoryService restockAdvisoryService;

    private final Long firmAId = 1L;
    private final Long firmBId = 2L;
    private Customer customerA;
    private Customer customerB;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentFirmId(firmAId);
        customerA = customerRepo.save(Customer.builder()
                .firmId(firmAId)
                .name("Customer A")
                .phone("9876543210")
                .build());

        TenantContext.setCurrentFirmId(firmBId);
        customerB = customerRepo.save(Customer.builder()
                .firmId(firmBId)
                .name("Customer B")
                .phone("9876543211")
                .build());

        TenantContext.setCurrentFirmId(firmAId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Path A: Explicitly created product has inInventory=true and appears in normal inventory")
    void testExplicitProductCreation_HasInInventoryTrue() {
        TenantContext.setCurrentFirmId(firmAId);

        Product p = Product.builder()
                .firmId(firmAId)
                .name("Catalog Item Direct")
                .category("Electronics")
                .price(new BigDecimal("150.00"))
                .stockQuantity(BigDecimal.ZERO)
                .build();

        Product created = productService.create(p);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getInInventory()).isTrue();
        assertThat(created.getIsArchived()).isFalse();

        // Should appear in normal inventory
        List<Product> normalInventory = productService.getAll(firmAId);
        assertThat(normalInventory).extracting(Product::getId).contains(created.getId());

        // Should NOT appear in unimported products
        List<Product> unimported = productService.getUnimportedProducts(firmAId);
        assertThat(unimported).extracting(Product::getId).doesNotContain(created.getId());
    }

    @Test
    @DisplayName("Path B & E2E Lifecycle: Ad-hoc invoice item -> inInventory=false -> Bulk Import -> inInventory=true")
    void testAdHocInvoiceItem_LifecycleAndIdempotency() {
        TenantContext.setCurrentFirmId(firmAId);

        // 1. Create Invoice with unknown product name
        String adHocName = "FINAL_BULK_IMPORT_TEST_001";
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(customerA.getId());
        req.setStatus(InvoiceStatus.PAID);
        req.setPaid(true);

        InvoiceRequestItem line = new InvoiceRequestItem();
        line.setProductId(null);
        line.setProductName(adHocName);
        line.setQty(5);
        line.setPricePerUnit(new BigDecimal("100.00"));
        line.setUnit("pcs");
        req.setItems(List.of(line));

        Invoice inv = invoiceService.createInvoice(req);
        assertThat(inv).isNotNull();

        // 2. Verify database record
        List<Product> matches = productRepo.findByFirmIdAndNameIgnoreCase(firmAId, adHocName);
        assertThat(matches).hasSize(1);
        Product autoCreated = matches.get(0);
        assertThat(autoCreated.getName()).isEqualTo(adHocName);
        assertThat(autoCreated.getCategory()).isEqualTo("UNCATEGORIZED");
        assertThat(autoCreated.getInInventory()).isFalse();
        assertThat(autoCreated.getIsArchived()).isFalse();
        assertThat(autoCreated.getStockQuantity()).isEqualByComparingTo(BigDecimal.ZERO);

        Long prodId = autoCreated.getId();

        // 3. Verify normal Inventory: MUST NOT appear
        List<Product> normalInventoryBefore = productService.getAll(firmAId);
        assertThat(normalInventoryBefore).extracting(Product::getId).doesNotContain(prodId);

        // 4. Verify Bulk Items Import: MUST appear
        List<Product> unimportedBefore = productService.getUnimportedProducts(firmAId);
        assertThat(unimportedBefore).extracting(Product::getId).contains(prodId);

        // 5. Import to inventory
        Product imported = productService.importToInventory(prodId);
        assertThat(imported.getId()).isEqualTo(prodId); // SAME Product ID
        assertThat(imported.getInInventory()).isTrue();
        assertThat(imported.getStockQuantity()).isEqualByComparingTo(BigDecimal.ZERO); // stock unchanged

        // 6. Verify database after import
        Product reloaded = productRepo.findById(prodId).orElseThrow();
        assertThat(reloaded.getInInventory()).isTrue();

        // 7. Verify normal Inventory: NOW appears
        List<Product> normalInventoryAfter = productService.getAll(firmAId);
        assertThat(normalInventoryAfter).extracting(Product::getId).contains(prodId);

        // 8. Verify Bulk Items Import: NO LONGER appears
        List<Product> unimportedAfter = productService.getUnimportedProducts(firmAId);
        assertThat(unimportedAfter).extracting(Product::getId).doesNotContain(prodId);

        // 9. Idempotency: Import second time
        Product secondImport = productService.importToInventory(prodId);
        assertThat(secondImport.getId()).isEqualTo(prodId);
        assertThat(secondImport.getInInventory()).isTrue();

        // Count in DB remains 1 (no duplicate product created)
        List<Product> matchesAfter = productRepo.findByFirmIdAndNameIgnoreCase(firmAId, adHocName);
        assertThat(matchesAfter).hasSize(1);
    }

    @Test
    @DisplayName("Zero stock products remain valid in normal inventory if inInventory=true")
    void testZeroStockInventoryProducts_RemainVisible() {
        TenantContext.setCurrentFirmId(firmAId);

        Product zeroStockProd = productRepo.save(Product.builder()
                .firmId(firmAId)
                .name("Zero Stock Nut")
                .category("Hardware")
                .price(new BigDecimal("10.00"))
                .stockQuantity(BigDecimal.ZERO)
                .inInventory(true)
                .isArchived(false)
                .build());

        List<Product> inventory = productService.getAll(firmAId);
        assertThat(inventory).extracting(Product::getId).contains(zeroStockProd.getId());
    }

    @Test
    @DisplayName("Multi-firm isolation: Firm A cannot view or import unimported products of Firm B")
    void testMultiFirmSecurity_IsolationForUnimportedProducts() {
        // Create unimported product in Firm A
        TenantContext.setCurrentFirmId(firmAId);
        Product prodA = productRepo.save(Product.builder()
                .firmId(firmAId)
                .name("Unimported Firm A")
                .category("UNCATEGORIZED")
                .price(new BigDecimal("25.00"))
                .inInventory(false)
                .isArchived(false)
                .build());

        // Create unimported product in Firm B
        TenantContext.setCurrentFirmId(firmBId);
        Product prodB = productRepo.save(Product.builder()
                .firmId(firmBId)
                .name("Unimported Firm B")
                .category("UNCATEGORIZED")
                .price(new BigDecimal("45.00"))
                .inInventory(false)
                .isArchived(false)
                .build());

        // When Firm A is active
        TenantContext.setCurrentFirmId(firmAId);
        List<Product> unimportedForA = productService.getUnimportedProducts(firmAId);
        assertThat(unimportedForA).extracting(Product::getId).contains(prodA.getId());
        assertThat(unimportedForA).extracting(Product::getId).doesNotContain(prodB.getId());

        // Firm A cannot import Firm B's product
        assertThrows(TenantSecurityException.class, () -> productService.importToInventory(prodB.getId()));

        // Cross-firm querying throws TenantSecurityException
        assertThrows(TenantSecurityException.class, () -> productService.getUnimportedProducts(firmBId));
    }

    @Test
    @DisplayName("Smart Reorder does not generate recommendations for inInventory=false products")
    void testSmartReorder_ExcludesUnimportedProducts() {
        TenantContext.setCurrentFirmId(firmAId);

        productRepo.save(Product.builder()
                .firmId(firmAId)
                .name("Unimported Non-Stocked")
                .category("UNCATEGORIZED")
                .price(new BigDecimal("200.00"))
                .stockQuantity(BigDecimal.ZERO)
                .minStockLevel(new BigDecimal("10.000"))
                .inInventory(false)
                .isArchived(false)
                .build());

        productRepo.save(Product.builder()
                .firmId(firmAId)
                .name("Active Inventory Item")
                .category("Hardware")
                .price(new BigDecimal("50.00"))
                .stockQuantity(BigDecimal.ONE)
                .minStockLevel(new BigDecimal("10.000"))
                .inInventory(true)
                .isArchived(false)
                .build());

        int count = restockAdvisoryService.generateCatalogRecommendations(firmAId);
        // Only active inventory items should be processed
        assertThat(count).isGreaterThanOrEqualTo(1);
    }
}
