package com.billing.simple.billsoft.regression.billing;

import com.billing.simple.billsoft.dtos.*;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Tag("regression")
@Tag("unit")
@DisplayName("Invoice Service Deep Coverage & Logic Branch Tests")
class InvoiceServiceDeepCoverageTest {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private ProductService productService;

    @Autowired
    private FirmDetailsService firmService;

    @Autowired
    private BackupService backupService;

    @Autowired
    private InvoiceRepository invoiceRepo;

    private Customer testCustomer;
    private Product testProductA;
    private Product testProductB;
    private final Long testFirmId = 1L;

    @BeforeEach
    void setUp() {
        backupService.factoryReset();

        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Precision Dynamics Ltd");
        firm.setGstin("27AAACP1122D1Z4");
        firm.setAddressLine1("Tech Hub Pune");
        firmService.create(firm);

        testCustomer = customerService.create(Customer.builder()
                .name("Zenith Automations")
                .phone("9822114455")
                .firmId(testFirmId)
                .build());

        testProductA = productService.create(Product.builder()
                .name("Industrial Sensor X1")
                .price(BigDecimal.valueOf(1200.00))
                .costPrice(BigDecimal.valueOf(800.00))
                .stockQuantity(BigDecimal.valueOf(100.0))
                .firmId(testFirmId)
                .build());

        testProductB = productService.create(Product.builder()
                .name("Power Relay 24V")
                .price(BigDecimal.valueOf(450.00))
                .costPrice(BigDecimal.valueOf(300.00))
                .stockQuantity(BigDecimal.valueOf(50.0))
                .firmId(testFirmId)
                .build());
    }

    @Test
    @DisplayName("Should test atomic invoice and estimate number generation")
    void testNumberGeneration() {
        String inv1 = invoiceService.generateInvoiceNumber(testFirmId);
        String inv2 = invoiceService.generateInvoiceNumber(testFirmId);
        assertThat(inv1).isNotNull().startsWith("INV-");
        assertThat(inv2).isNotNull().startsWith("INV-");

        String est1 = invoiceService.generateEstimateNumber(testFirmId);
        String est2 = invoiceService.generateEstimateNumber(testFirmId);
        assertThat(est1).isNotNull().startsWith("EST-");
        assertThat(est2).isNotNull().startsWith("EST-");
    }

    @Test
    @DisplayName("Should create invoice with discount, convert estimate, update items and verify stock")
    void testInvoiceLifecycleAndCalculations() {
        // 1. Create Estimate
        InvoiceRequest estReq = new InvoiceRequest();
        estReq.setFirmId(testFirmId);
        estReq.setCustomerId(testCustomer.getId());
        estReq.setStatus(InvoiceStatus.ESTIMATE);

        InvoiceRequestItem item1 = new InvoiceRequestItem();
        item1.setProductId(testProductA.getId());
        item1.setQty(10);
        item1.setPricePerUnit(BigDecimal.valueOf(1200.00));
        item1.setGstPercent(BigDecimal.valueOf(18.00));

        estReq.setItems(List.of(item1));
        Invoice estimate = invoiceService.createEstimate(estReq);
        assertThat(estimate.getStatus()).isEqualTo(InvoiceStatus.ESTIMATE);

        // Product stock should not be deducted for estimate
        Product refreshedA = productService.getById(testProductA.getId());
        assertThat(refreshedA.getStockQuantity()).isEqualByComparingTo("100.0");

        // 2. Convert Estimate to FINAL Invoice
        Invoice finalInv = invoiceService.convertEstimateToInvoice(estimate.getId(), null);
        assertThat(finalInv.getStatus()).isEqualTo(InvoiceStatus.FINAL);

        refreshedA = productService.getById(testProductA.getId());
        assertThat(refreshedA.getStockQuantity()).isEqualByComparingTo("90.0");

        // 3. Update Invoice items
        InvoiceUpdateRequest updateReq = new InvoiceUpdateRequest();
        updateReq.setCustomerId(testCustomer.getId());
        updateReq.setStatus(InvoiceStatus.FINAL);

        InvoiceRequestItem updatedItem1 = new InvoiceRequestItem();
        updatedItem1.setProductId(testProductA.getId());
        updatedItem1.setQty(5); // Reduce from 10 to 5
        updatedItem1.setPricePerUnit(BigDecimal.valueOf(1200.00));
        updatedItem1.setGstPercent(BigDecimal.valueOf(18.00));

        InvoiceRequestItem newItem = new InvoiceRequestItem();
        newItem.setProductId(testProductB.getId());
        newItem.setQty(10);
        newItem.setPricePerUnit(BigDecimal.valueOf(450.00));
        newItem.setGstPercent(BigDecimal.valueOf(12.00));

        updateReq.setItems(List.of(updatedItem1, newItem));
        Invoice updated = invoiceService.updateFullInvoice(finalInv.getId(), updateReq);
        assertThat(updated.getItems()).hasSize(2);

        // 4. Update status to CANCELLED
        Invoice cancelled = invoiceService.updateStatus(updated.getId(), InvoiceStatus.CANCELLED);
        assertThat(cancelled.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should test payment history, partial payments, and analytics queries")
    void testPaymentsAndAnalytics() {
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(testFirmId);
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.FINAL);

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(testProductA.getId());
        item.setQty(2);
        item.setPricePerUnit(BigDecimal.valueOf(1000.00));
        item.setGstPercent(BigDecimal.valueOf(18.00));
        req.setItems(List.of(item));

        Invoice inv = invoiceService.createInvoice(req);
        // Total = 2 * 1000 * 1.18 = 2360.00

        // 1. Partial Payment
        InvoicePayment p1 = invoiceService.recordPayment(inv.getId(), BigDecimal.valueOf(1000.00), LocalDate.now(), "CASH", "CASH-01", "Part payment");
        Invoice refreshed = invoiceService.getById(inv.getId());
        assertThat(refreshed.getPaid()).isFalse();

        // 2. Remaining Payment
        InvoicePayment p2 = invoiceService.recordPayment(inv.getId(), BigDecimal.valueOf(1360.00), LocalDate.now(), "UPI", "UPI-999", "Full clearance");
        refreshed = invoiceService.getById(inv.getId());
        assertThat(refreshed.getPaid()).isTrue();
        assertThat(refreshed.getStatus()).isEqualTo(InvoiceStatus.PAID);

        // 3. Query payments
        List<InvoicePayment> payments = invoiceService.getPayments(inv.getId());
        assertThat(payments).hasSize(2);

        // 4. Update Paid Flag
        invoiceService.updatePaidFlag(inv.getId(), false);
        refreshed = invoiceService.getById(inv.getId());
        assertThat(refreshed.getPaid()).isFalse();

        // 5. Query Customer Analytics & Summary
        CustomerAnalyticsResponse custAnalytics = invoiceService.getCustomerAnalytics(testCustomer.getId());
        assertThat(custAnalytics.getTotalBusiness()).isNotNull();

        List<CustomerAnalyticsResponse> searchResults = invoiceService.getCustomerAnalyticsByName("NonExistent");
        assertThat(searchResults).isEmpty();

        // 6. Query Firm Analytics & Firm Stats
        FirmAnalyticsResponse firmAnalytics = invoiceService.getFirmAnalytics(testFirmId);
        assertThat(firmAnalytics.getTotalBusiness()).isNotNull();

        Map<String, Double> firmStats = invoiceService.getFirmStats();
        assertThat(firmStats).containsKey("totalBusiness");

        // 7. Preview Invoice
        Invoice preview = invoiceService.previewInvoice(req);
        assertThat(preview.getTotalAmount()).isNotNull();

        // 8. Query Invoices
        assertThat(invoiceService.getAll(testFirmId)).isNotEmpty();
        assertThat(invoiceService.getAllFinalInvoices(testFirmId)).isNotEmpty();

        // 9. Update Status manually
        Invoice overdue = invoiceService.updateStatus(inv.getId(), InvoiceStatus.OVERDUE);
        assertThat(overdue.getStatus()).isEqualTo(InvoiceStatus.OVERDUE);

        // 10. Delete Invoice
        boolean deleted = invoiceService.delete(inv.getId());
        assertThat(deleted).isTrue();
        assertThat(invoiceService.getById(inv.getId())).isNull();
    }
}
