package com.billing.simple.billsoft.regression.billing;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("regression")
@Tag("integration")
@DisplayName("Customer & Firm Statements and Ledger Exports Regression Tests")
class StatementsRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private ProductService productService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private StatementService statementService;

    @Autowired
    private FirmDetailsService firmService;

    @Autowired
    private BackupService backupService;

    private Customer testCustomer;
    private final Long testFirmId = 1L;

    @BeforeEach
    void setUp() {
        backupService.factoryReset();

        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Pinnacle Instruments");
        firm.setGstin("27AAACP9988Z1Z2");
        firm.setAddressLine1("Industrial Estate, Pune");
        firmService.create(firm);

        testCustomer = customerService.create(Customer.builder()
                .name("Apex Systems")
                .phone("9822334411")
                .firmId(testFirmId)
                .build());

        Product product = productService.create(Product.builder()
                .name("Calibration Sensor")
                .price(BigDecimal.valueOf(1000.00))
                .stockQuantity(BigDecimal.valueOf(50.0))
                .firmId(testFirmId)
                .build());

        // Create Invoice
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(testFirmId);
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.FINAL);

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(product.getId());
        item.setQty(5);
        item.setPricePerUnit(BigDecimal.valueOf(1000.00));
        item.setGstPercent(BigDecimal.valueOf(18.00));
        req.setItems(List.of(item));

        Invoice inv = invoiceService.createInvoice(req);

        // Record Payment & Mark Paid
        invoiceService.updatePaidFlag(inv.getId(), true);
    }

    @Test
    @DisplayName("Should calculate customer statement with debits, credits, and balance")
    void testCustomerStatement() throws Exception {
        mockMvc.perform(get("/api/statements/customer/" + testCustomer.getId() + "?firmId=" + testFirmId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(testCustomer.getId()))
                .andExpect(jsonPath("$.totalBilled").value(5900.0))
                .andExpect(jsonPath("$.totalPaid").value(5900.0))
                .andExpect(jsonPath("$.closingBalance").value(0.0));
    }

    @Test
    @DisplayName("Should generate customer statement PDF byte stream")
    void testCustomerStatementPdf() throws Exception {
        byte[] pdf = statementService.generateCustomerStatementPdf(testFirmId, testCustomer.getId(), null, null);
        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(500);

        mockMvc.perform(get("/api/statements/customer/" + testCustomer.getId() + "/pdf?firmId=" + testFirmId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    @Test
    @DisplayName("Should calculate firm statement and generate firm PDF report")
    void testFirmStatement() throws Exception {
        mockMvc.perform(get("/api/statements/firm?firmId=" + testFirmId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firmName").value("Pinnacle Instruments"))
                .andExpect(jsonPath("$.totalBilled").value(5900.0));

        mockMvc.perform(get("/api/statements/firm/pdf?firmId=" + testFirmId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    @Autowired
    private PartyService partyService;

    @Test
    @DisplayName("Should calculate party statement and generate party PDF report")
    void testPartyStatementAndPdf() throws Exception {
        Party party = partyService.createParty(Party.builder()
                .name("Precision Raw Materials")
                .phone("9876543210")
                .firmId(testFirmId)
                .build());

        var stmt = statementService.getPartyStatement(testFirmId, party.getId(), null, null);
        assertThat(stmt).isNotNull();
        assertThat(stmt.getPartyName()).isEqualTo("Precision Raw Materials");

        byte[] pdf = statementService.generatePartyStatementPdf(testFirmId, party.getId(), null, null);
        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(500);
    }
}
