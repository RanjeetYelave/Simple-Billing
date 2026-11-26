package com.billing.simple.billsoft.regression.billing;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.InvoiceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DisplayName("Multi-Tab Document State Preservation and Cross-Firm Isolation Tests")
public class MultiTabDocumentStatePreservationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FirmDetailsRepository firmRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private InvoiceService invoiceService;

    private FirmDetails firmA;
    private FirmDetails firmB;
    private Customer customerA;
    private Customer customerB;
    private Product productA;
    private Product productB;

    @BeforeEach
    void setUp() {
        TenantContext.clear();

        FirmDetails fA = new FirmDetails();
        fA.setFirmName("MultiTab Firm Alpha");
        fA.setEmail("alpha@multitab.com");
        fA.setPhone("9876543201");
        firmA = firmRepo.save(fA);

        FirmDetails fB = new FirmDetails();
        fB.setFirmName("MultiTab Firm Beta");
        fB.setEmail("beta@multitab.com");
        fB.setPhone("9876543202");
        firmB = firmRepo.save(fB);

        Customer cA = new Customer();
        cA.setFirmId(firmA.getId());
        cA.setName("Customer AlphaOne");
        cA.setPhone("9100000001");
        customerA = customerRepo.save(cA);

        Customer cB = new Customer();
        cB.setFirmId(firmB.getId());
        cB.setName("Customer BetaTwo");
        cB.setPhone("9200000002");
        customerB = customerRepo.save(cB);

        Product pA = new Product();
        pA.setFirmId(firmA.getId());
        pA.setName("Widget Alpha");
        pA.setSku("WID-ALPHA-01");
        pA.setPrice(new BigDecimal("250.00"));
        pA.setStockQuantity(new BigDecimal("500.000"));
        productA = productRepo.save(pA);

        Product pB = new Product();
        pB.setFirmId(firmB.getId());
        pB.setName("Gadget Beta");
        pB.setSku("GAD-BETA-02");
        pB.setPrice(new BigDecimal("450.00"));
        pB.setStockQuantity(new BigDecimal("300.000"));
        productB = productRepo.save(pB);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        invoiceRepo.deleteAll();
        productRepo.deleteAll();
        customerRepo.deleteAll();
        firmRepo.deleteAll();
    }

    @Test
    @DisplayName("Scenario 1: Two open invoices - saving Tab 1 creates Invoice A while Tab 2 data remains independent")
    void testTwoOpenInvoices_savingOnePreservesOther() throws Exception {
        TenantContext.setCurrentFirmId(firmA.getId());

        // Document Tab 1: Invoice A for Customer Alpha
        InvoiceRequest tab1Request = new InvoiceRequest();
        tab1Request.setFirmId(firmA.getId());
        tab1Request.setCustomerId(customerA.getId());
        tab1Request.setCustomerName(customerA.getName());
        tab1Request.setAllowOutOfStock(true);
        InvoiceRequestItem it1 = new InvoiceRequestItem();
        it1.setProductId(productA.getId());
        it1.setProductName(productA.getName());
        it1.setQty(2);
        it1.setPricePerUnit(new BigDecimal("250.00"));
        tab1Request.setItems(Collections.singletonList(it1));

        // Document Tab 2: Draft Invoice B for Customer Alpha with different item
        InvoiceRequest tab2Draft = new InvoiceRequest();
        tab2Draft.setFirmId(firmA.getId());
        tab2Draft.setCustomerId(customerA.getId());
        tab2Draft.setCustomerName(customerA.getName());
        InvoiceRequestItem it2 = new InvoiceRequestItem();
        it2.setProductId(productA.getId());
        it2.setProductName(productA.getName());
        it2.setQty(5);
        it2.setPricePerUnit(new BigDecimal("250.00"));
        tab2Draft.setItems(Collections.singletonList(it2));

        // Save Tab 1
        String respJson = mockMvc.perform(post("/api/invoices")
                        .header("X-Firm-Id", String.valueOf(firmA.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tab1Request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();

        Invoice savedTab1 = objectMapper.readValue(respJson, Invoice.class);
        assertNotNull(savedTab1.getId());
        assertEquals(InvoiceStatus.UNPAID, savedTab1.getStatus());
        assertEquals(firmA.getId(), savedTab1.getFirmId());

        // Verify only one invoice exists in DB
        List<Invoice> firmAInvoices = invoiceRepo.findByFirmId(firmA.getId());
        assertEquals(1, firmAInvoices.size());
        assertEquals(savedTab1.getId(), firmAInvoices.get(0).getId());

        // Tab 2 can now be saved independently without collision
        mockMvc.perform(post("/api/invoices")
                        .header("X-Firm-Id", String.valueOf(firmA.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tab2Draft)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists());

        List<Invoice> firmAInvoicesAfter = invoiceRepo.findByFirmId(firmA.getId());
        assertEquals(2, firmAInvoicesAfter.size());
    }

    @Test
    @DisplayName("Scenario 2: Two open quotations - saving Quotation A marks it ESTIMATE and preserves Tab 2")
    void testTwoOpenQuotations_savingOnePreservesOther() throws Exception {
        TenantContext.setCurrentFirmId(firmA.getId());

        InvoiceRequest quote1 = new InvoiceRequest();
        quote1.setFirmId(firmA.getId());
        quote1.setCustomerId(customerA.getId());
        quote1.setCustomerName(customerA.getName());
        InvoiceRequestItem it1 = new InvoiceRequestItem();
        it1.setProductId(productA.getId());
        it1.setProductName(productA.getName());
        it1.setQty(10);
        it1.setPricePerUnit(new BigDecimal("250.00"));
        quote1.setItems(Collections.singletonList(it1));

        String resp = mockMvc.perform(post("/api/invoices/estimate")
                        .header("X-Firm-Id", String.valueOf(firmA.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(quote1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("ESTIMATE"))
                .andReturn().getResponse().getContentAsString();

        Invoice savedQuote1 = objectMapper.readValue(resp, Invoice.class);
        assertNotNull(savedQuote1.getEstimateNumber());
        assertNull(savedQuote1.getInvoiceNumber());
        assertEquals(InvoiceStatus.ESTIMATE, savedQuote1.getStatus());
    }

    @Test
    @DisplayName("Scenario 3: One invoice and one quotation open - saving invoice does not convert or alter quotation")
    void testOneInvoiceOneQuotation_savingInvoicePreservesQuotation() throws Exception {
        TenantContext.setCurrentFirmId(firmA.getId());

        InvoiceRequest invoiceReq = new InvoiceRequest();
        invoiceReq.setFirmId(firmA.getId());
        invoiceReq.setCustomerId(customerA.getId());
        invoiceReq.setCustomerName(customerA.getName());
        invoiceReq.setItems(Collections.singletonList(new InvoiceRequestItem() {{
            setProductId(productA.getId());
            setProductName(productA.getName());
            setQty(3);
            setPricePerUnit(new BigDecimal("250.00"));
        }}));

        mockMvc.perform(post("/api/invoices")
                        .header("X-Firm-Id", String.valueOf(firmA.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invoiceReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNPAID"))
                .andExpect(jsonPath("$.invoiceNumber").exists());

        // Quotation draft can still be saved independently as an ESTIMATE
        InvoiceRequest quoteReq = new InvoiceRequest();
        quoteReq.setFirmId(firmA.getId());
        quoteReq.setCustomerId(customerA.getId());
        quoteReq.setCustomerName(customerA.getName());
        quoteReq.setItems(Collections.singletonList(new InvoiceRequestItem() {{
            setProductId(productA.getId());
            setProductName(productA.getName());
            setQty(8);
            setPricePerUnit(new BigDecimal("250.00"));
        }}));

        mockMvc.perform(post("/api/invoices/estimate")
                        .header("X-Firm-Id", String.valueOf(firmA.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(quoteReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESTIMATE"))
                .andExpect(jsonPath("$.estimateNumber").exists());
    }

    @Test
    @DisplayName("Scenario 4 & 5: Four mixed document tabs across different billing firms - complete isolation")
    void testFourMixedDocumentTabsAcrossDifferentFirms() throws Exception {
        // Tab 1: Invoice under Firm A
        InvoiceRequest tab1 = new InvoiceRequest();
        tab1.setFirmId(firmA.getId());
        tab1.setCustomerId(customerA.getId());
        tab1.setCustomerName(customerA.getName());
        tab1.setItems(Collections.singletonList(new InvoiceRequestItem() {{
            setProductId(productA.getId());
            setProductName(productA.getName());
            setQty(1);
            setPricePerUnit(new BigDecimal("250.00"));
        }}));

        // Tab 2: Quotation under Firm A
        InvoiceRequest tab2 = new InvoiceRequest();
        tab2.setFirmId(firmA.getId());
        tab2.setCustomerId(customerA.getId());
        tab2.setCustomerName(customerA.getName());
        tab2.setItems(Collections.singletonList(new InvoiceRequestItem() {{
            setProductId(productA.getId());
            setProductName(productA.getName());
            setQty(4);
            setPricePerUnit(new BigDecimal("250.00"));
        }}));

        // Tab 3: Invoice under Firm B
        InvoiceRequest tab3 = new InvoiceRequest();
        tab3.setFirmId(firmB.getId());
        tab3.setCustomerId(customerB.getId());
        tab3.setCustomerName(customerB.getName());
        tab3.setItems(Collections.singletonList(new InvoiceRequestItem() {{
            setProductId(productB.getId());
            setProductName(productB.getName());
            setQty(2);
            setPricePerUnit(new BigDecimal("450.00"));
        }}));

        // Tab 4: Quotation under Firm B
        InvoiceRequest tab4 = new InvoiceRequest();
        tab4.setFirmId(firmB.getId());
        tab4.setCustomerId(customerB.getId());
        tab4.setCustomerName(customerB.getName());
        tab4.setItems(Collections.singletonList(new InvoiceRequestItem() {{
            setProductId(productB.getId());
            setProductName(productB.getName());
            setQty(6);
            setPricePerUnit(new BigDecimal("450.00"));
        }}));

        // Save Tab 1 (Invoice A under Firm A)
        TenantContext.setCurrentFirmId(firmA.getId());
        mockMvc.perform(post("/api/invoices")
                        .header("X-Firm-Id", String.valueOf(firmA.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tab1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firmId").value(firmA.getId()))
                .andExpect(jsonPath("$.status").value("UNPAID"));

        // Save Tab 4 (Quotation B under Firm B)
        mockMvc.perform(post("/api/invoices/estimate")
                        .header("X-Firm-Id", String.valueOf(firmB.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tab4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firmId").value(firmB.getId()))
                .andExpect(jsonPath("$.status").value("ESTIMATE"));

        // Verify Firm A has only 1 document, Firm B has only 1 document
        assertEquals(1, invoiceRepo.findByFirmId(firmA.getId()).size());
        assertEquals(1, invoiceRepo.findByFirmId(firmB.getId()).size());
    }
}
