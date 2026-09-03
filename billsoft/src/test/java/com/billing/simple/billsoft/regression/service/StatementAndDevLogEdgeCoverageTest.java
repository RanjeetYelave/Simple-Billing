package com.billing.simple.billsoft.regression.service;

import com.billing.simple.billsoft.dtos.*;
import com.billing.simple.billsoft.entities.*;
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

@SpringBootTest
@Tag("regression")
@Tag("unit")
@DisplayName("Statement & DevLog Edge Branch Coverage Tests")
class StatementAndDevLogEdgeCoverageTest {

    @Autowired
    private StatementService statementService;

    @Autowired
    private DevLogService devLogService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private ProductService productService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private PartyService partyService;

    @Autowired
    private FirmDetailsService firmService;

    @Autowired
    private BackupService backupService;

    private Customer testCustomer;
    private Party testParty;
    private FirmDetails testFirm;
    private final Long testFirmId = 1L;

    @BeforeEach
    void setUp() {
        backupService.factoryReset();

        testFirm = new FirmDetails();
        testFirm.setFirmName("Apex Quantum Systems");
        testFirm.setGstin("27AABCQ9988D1Z8");
        testFirm.setAddressLine1("Tech Boulevard Pune");
        testFirm.setPhone("9811002233");
        testFirm.setEmail("contact@apexquantum.com");
        testFirm = firmService.create(testFirm);

        testCustomer = customerService.create(Customer.builder()
                .name("Alpha Retailers")
                .phone("9822334455")
                .firmId(testFirmId)
                .build());

        testParty = partyService.createParty(Party.builder()
                .name("Beta Wholesale Suppliers")
                .phone("9822445566")
                .firmId(testFirmId)
                .build());

        Product p = productService.create(Product.builder()
                .name("Quantum Processor Unit")
                .price(BigDecimal.valueOf(5000.0))
                .costPrice(BigDecimal.valueOf(3500.0))
                .stockQuantity(BigDecimal.valueOf(50.0))
                .firmId(testFirmId)
                .build());

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(testFirmId);
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.FINAL);

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(p.getId());
        item.setQty(2);
        item.setPricePerUnit(BigDecimal.valueOf(5000.0));
        item.setGstPercent(BigDecimal.valueOf(18.0));
        req.setItems(List.of(item));

        Invoice inv = invoiceService.createInvoice(req);
        invoiceService.recordPayment(inv.getId(), BigDecimal.valueOf(5000.0), LocalDate.now(), "UPI", "UPI-111", "Advance");
    }

    @Test
    @DisplayName("Should test Statements with null firmId, unbounded dates, and PDF exports")
    void testStatementEdgeBranches() throws Exception {
        // 1. Customer statement with null firmId and null dates (defaults to 1970 - now)
        CustomerStatementResponse custStmt = statementService.getCustomerStatement(null, testCustomer.getId(), null, null);
        assertThat(custStmt).isNotNull();
        assertThat(custStmt.getEntries()).isNotEmpty();

        // 2. Firm statement with null dates
        FirmStatementResponse firmStmt = statementService.getFirmStatement(testFirmId, null, null);
        assertThat(firmStmt).isNotNull();

        // 3. Customer statement PDF with null dates
        byte[] custPdf = statementService.generateCustomerStatementPdf(testFirmId, testCustomer.getId(), null, null);
        assertThat(custPdf).isNotNull();
        assertThat(custPdf.length).isGreaterThan(1000);

        // 4. Firm statement PDF with null dates
        byte[] firmPdf = statementService.generateFirmStatementPdf(testFirmId, null, null);
        assertThat(firmPdf).isNotNull();
        assertThat(firmPdf.length).isGreaterThan(1000);

        // 5. Party statement with null dates
        PartyStatementResponse partyStmt = statementService.getPartyStatement(testFirmId, testParty.getId(), null, null);
        assertThat(partyStmt).isNotNull();

        // 6. Party statement PDF with null dates
        byte[] partyPdf = statementService.generatePartyStatementPdf(testFirmId, testParty.getId(), null, null);
        assertThat(partyPdf).isNotNull();
        assertThat(partyPdf.length).isGreaterThan(1000);
    }

    @Test
    @DisplayName("Should test DevLogService enable, disable, and status reporting")
    void testDevLogServiceBranches() {
        Map<String, Object> statusBefore = devLogService.getStatus();
        assertThat(statusBefore).containsKey("enabled");

        // Enable dev logs
        Map<String, Object> enabledStatus = devLogService.setEnabled(true);
        assertThat(enabledStatus.get("enabled")).isEqualTo(true);

        // Re-read status while enabled
        Map<String, Object> statusWhileEnabled = devLogService.getStatus();
        assertThat(statusWhileEnabled.get("enabled")).isEqualTo(true);

        // Disable dev logs
        Map<String, Object> disabledStatus = devLogService.setEnabled(false);
        assertThat(disabledStatus.get("enabled")).isEqualTo(false);
    }
}
