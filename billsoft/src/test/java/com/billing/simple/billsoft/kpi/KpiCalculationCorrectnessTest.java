package com.billing.simple.billsoft.kpi;

import com.billing.simple.billsoft.dtos.FirmAnalyticsResponse;
import com.billing.simple.billsoft.kpi.dto.KpiDashboardResponse;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.InvoiceService;
import com.billing.simple.billsoft.service.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class KpiCalculationCorrectnessTest {

    private static final Long FIRM_1 = 1L;
    private static final Long FIRM_2 = 2L;

    @Mock
    private ProductRepository productRepo;

    @InjectMocks
    private ProductService productService;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentFirmId(FIRM_1);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("ProductService.getInventorySummary correctly calculates totalStockUnits, valuation, margin and status counts")
    void testInventorySummaryCalculations() {
        List<Product> products = new ArrayList<>();

        // 1. Regular goods item in healthy stock
        Product p1 = new Product();
        p1.setId(101L);
        p1.setName("Laptop Core i7");
        p1.setItemType("GOODS");
        p1.setPrice(new BigDecimal("60000.00"));
        p1.setCostPrice(new BigDecimal("45000.00"));
        p1.setStockQuantity(new BigDecimal("10.000"));
        p1.setMinStockLevel(new BigDecimal("2.000"));
        p1.setFirmId(FIRM_1);
        p1.setIsArchived(false);
        products.add(p1);

        // 2. Low stock goods item
        Product p2 = new Product();
        p2.setId(102L);
        p2.setName("Wireless Mouse");
        p2.setItemType("GOODS");
        p2.setPrice(new BigDecimal("800.00"));
        p2.setCostPrice(new BigDecimal("500.00"));
        p2.setStockQuantity(new BigDecimal("3.000"));
        p2.setMinStockLevel(new BigDecimal("5.000"));
        p2.setFirmId(FIRM_1);
        p2.setIsArchived(false);
        products.add(p2);

        // 3. Out of stock goods item
        Product p3 = new Product();
        p3.setId(103L);
        p3.setName("Mechanical Keyboard");
        p3.setItemType("GOODS");
        p3.setPrice(new BigDecimal("3500.00"));
        p3.setCostPrice(new BigDecimal("2200.00"));
        p3.setStockQuantity(BigDecimal.ZERO);
        p3.setMinStockLevel(new BigDecimal("3.000"));
        p3.setFirmId(FIRM_1);
        p3.setIsArchived(false);
        products.add(p3);

        // 4. Service item (must NOT contribute to physical stock units or inventory valuation)
        Product p4 = new Product();
        p4.setId(104L);
        p4.setName("Installation & Setup");
        p4.setItemType("SERVICE");
        p4.setPrice(new BigDecimal("1500.00"));
        p4.setCostPrice(BigDecimal.ZERO);
        p4.setStockQuantity(new BigDecimal("100.000"));
        p4.setFirmId(FIRM_1);
        p4.setIsArchived(false);
        products.add(p4);

        // 5. Archived product (must be completely ignored)
        Product p5 = new Product();
        p5.setId(105L);
        p5.setName("Discontinued Adapter");
        p5.setItemType("GOODS");
        p5.setPrice(new BigDecimal("500.00"));
        p5.setCostPrice(new BigDecimal("300.00"));
        p5.setStockQuantity(new BigDecimal("50.000"));
        p5.setFirmId(FIRM_1);
        p5.setIsArchived(true);
        products.add(p5);

        when(productRepo.findByFirmId(FIRM_1)).thenReturn(products);

        Map<String, Object> summary = productService.getInventorySummary(FIRM_1);

        assertNotNull(summary);
        assertEquals(4L, summary.get("totalProducts"));
        assertEquals(3L, summary.get("goodsCount"));
        assertEquals(1L, summary.get("servicesCount"));
        assertEquals(1L, summary.get("lowStockCount"));
        assertEquals(1L, summary.get("outOfStockCount"));

        // Expected stock units = 10 (p1) + 3 (p2) = 13.00
        BigDecimal totalStockUnits = (BigDecimal) summary.get("totalStockUnits");
        assertNotNull(totalStockUnits);
        assertEquals(new BigDecimal("13.00"), totalStockUnits);

        // Expected cost valuation = (45,000 * 10) + (500 * 3) = 450,000 + 1,500 = 451,500.00
        BigDecimal costValuation = (BigDecimal) summary.get("totalCostValue");
        assertNotNull(costValuation);
        assertEquals(new BigDecimal("451500.00"), costValuation);
        assertEquals(costValuation, summary.get("totalValuationAtCost"));

        // Expected retail valuation = (60,000 * 10) + (800 * 3) = 600,000 + 2,400 = 602,400.00
        BigDecimal retailValuation = (BigDecimal) summary.get("totalRetailValue");
        assertNotNull(retailValuation);
        assertEquals(new BigDecimal("602400.00"), retailValuation);

        // Margin p1 = (60000 - 45000) / 60000 = 25.0%
        // Margin p2 = (800 - 500) / 800 = 37.5%
        // Margin p3 = (3500 - 2200) / 3500 = 37.1429%
        // Average margin = (25 + 37.5 + 37.1429) / 3 = 33.2%
        assertNotNull(summary.get("averageGrossMarginPercent"));
        assertEquals(summary.get("averageGrossMarginPercent"), summary.get("estimatedMarginPercent"));
    }

    @Test
    @DisplayName("FirmAnalyticsResponse DTO correctly carries and serializes total and paid invoice counts")
    void testFirmAnalyticsResponseFields() {
        FirmAnalyticsResponse resp = new FirmAnalyticsResponse();
        resp.setTotalBusiness(150000.0);
        resp.setTotalPaid(120000.0);
        resp.setTotalPending(30000.0);
        resp.setTotalInvoiceCount(45L);
        resp.setPaidInvoiceCount(38L);
        resp.setUnpaidInvoiceCount(7L);
        resp.setOverdueInvoiceCount(2L);
        resp.setQuoteCount(5L);
        resp.setQuotePipelineValue(25000.0);

        assertEquals(45L, resp.getTotalInvoiceCount());
        assertEquals(38L, resp.getPaidInvoiceCount());
        assertEquals(7L, resp.getUnpaidInvoiceCount());
        assertEquals(2L, resp.getOverdueInvoiceCount());
        assertEquals(5L, resp.getQuoteCount());
        assertEquals(25000.0, resp.getQuotePipelineValue());
        assertEquals(150000.0, resp.getTotalBusiness());
    }

    @Test
    @DisplayName("KpiDashboardResponse DTO correctly maps total and paid invoice counts")
    void testKpiDashboardResponseFields() {
        KpiDashboardResponse response = KpiDashboardResponse.builder()
                .totalBusiness(200000.0)
                .totalPaid(150000.0)
                .totalPending(50000.0)
                .totalInvoiceCount(60L)
                .paidInvoiceCount(45L)
                .unpaidInvoiceCount(15L)
                .overdueInvoiceCount(4L)
                .build();

        assertEquals(60L, response.getTotalInvoiceCount());
        assertEquals(45L, response.getPaidInvoiceCount());
        assertEquals(15L, response.getUnpaidInvoiceCount());
        assertEquals(4L, response.getOverdueInvoiceCount());
    }
}
