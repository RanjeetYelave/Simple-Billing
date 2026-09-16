package com.billing.simple.billsoft.regression.inventory;

import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.StockMovement;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repo.StockMovementRepository;
import com.billing.simple.billsoft.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("regression")
@Tag("integration")
@DisplayName("Inventory Stock & Movement Ledger Regression Tests")
class InventoryStockRegressionTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private StockMovementRepository movementRepo;

    @Autowired
    private com.billing.simple.billsoft.service.BackupService backupService;

    private final Long testFirmId = 1L;

    @BeforeEach
    void setUp() {
        backupService.factoryReset();
    }

    @Test
    @DisplayName("Should create opening stock movement ledger entry when initial stock > 0")
    void shouldRecordOpeningStockMovement() {
        Product p = productService.create(Product.builder()
                .name("Copper Wire Spool")
                .price(BigDecimal.valueOf(800.00))
                .stockQuantity(BigDecimal.valueOf(25.0))
                .firmId(testFirmId)
                .itemType("GOODS")
                .unit("spool")
                .build());

        List<StockMovement> movements = productService.getStockMovements(p.getId(), testFirmId);
        assertThat(movements).hasSize(1);
        StockMovement m = movements.get(0);
        assertThat(m.getMovementType()).isEqualTo("INITIAL_STOCK");
        assertThat(m.getQuantityChange()).isEqualByComparingTo("25.000");
        assertThat(m.getNewStock()).isEqualByComparingTo("25.000");
    }

    @Test
    @DisplayName("Should clamp stock to zero and prevent negative numbers on over-deduction")
    void shouldClampStockToZeroOnOverDeduction() {
        Product p = productService.create(Product.builder()
                .name("Limited Spare Bolt")
                .price(BigDecimal.valueOf(20.00))
                .stockQuantity(BigDecimal.valueOf(5.0))
                .firmId(testFirmId)
                .itemType("GOODS")
                .build());

        // 1. Manual deduction of 10 from stock of 5
        Product adjusted = productService.adjustStock(p.getId(), BigDecimal.valueOf(10.0), "DEDUCT", "Excess test deduction");
        assertThat(adjusted.getStockQuantity()).isEqualByComparingTo("0.000");

        // 2. Programmatic deduction of another 15
        productService.recordStockMovement(p.getId(), testFirmId, "INVOICE_SALE", BigDecimal.valueOf(-15.0), "INVOICE", "INV-9999", "Sale");

        Product afterSale = productService.getById(p.getId());
        assertThat(afterSale.getStockQuantity()).isEqualByComparingTo("0.000");
    }

    @Test
    @DisplayName("Should correctly handle manual stock adjustments (ADD, DEDUCT, SET)")
    void shouldHandleManualAdjustments() {
        Product p = productService.create(Product.builder()
                .name("Aluminum Sheet")
                .price(BigDecimal.valueOf(450.00))
                .stockQuantity(BigDecimal.valueOf(10.0))
                .firmId(testFirmId)
                .itemType("GOODS")
                .build());

        // ADD 15 -> 25
        Product addRes = productService.adjustStock(p.getId(), BigDecimal.valueOf(15.0), "ADD", "Shipment received");
        assertThat(addRes.getStockQuantity()).isEqualByComparingTo("25.000");

        // DEDUCT 5 -> 20
        Product deductRes = productService.adjustStock(p.getId(), BigDecimal.valueOf(5.0), "DEDUCT", "Damaged goods");
        assertThat(deductRes.getStockQuantity()).isEqualByComparingTo("20.000");

        // SET 50 -> 50
        Product setRes = productService.adjustStock(p.getId(), BigDecimal.valueOf(50.0), "SET", "Audit count");
        assertThat(setRes.getStockQuantity()).isEqualByComparingTo("50.000");
    }

    @Test
    @DisplayName("Should skip stock deduction for SERVICE items")
    void shouldSkipStockDeductionForServices() {
        Product serviceProduct = productService.create(Product.builder()
                .name("Consulting & Installation")
                .price(BigDecimal.valueOf(1500.00))
                .stockQuantity(BigDecimal.ZERO)
                .firmId(testFirmId)
                .itemType("SERVICE")
                .build());

        productService.recordStockMovement(serviceProduct.getId(), testFirmId, "INVOICE_SALE", BigDecimal.valueOf(-5.0), "INVOICE", "INV-100", "Service invoice");

        Product retrieved = productService.getById(serviceProduct.getId());
        assertThat(retrieved.getStockQuantity()).isEqualByComparingTo("0.000");
        List<StockMovement> movements = productService.getStockMovements(serviceProduct.getId(), testFirmId);
        assertThat(movements).isEmpty();
    }
}
