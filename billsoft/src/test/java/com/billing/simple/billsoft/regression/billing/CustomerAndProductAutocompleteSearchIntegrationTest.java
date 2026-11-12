package com.billing.simple.billsoft.regression.billing;

import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repositories.BusinessPartyLinkRepository;
import com.billing.simple.billsoft.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Tag("regression")
@Tag("billing")
public class CustomerAndProductAutocompleteSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private BusinessPartyLinkRepository businessPartyLinkRepository;

    private static final Long FIRM_A = 901L;
    private static final Long FIRM_B = 902L;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        businessPartyLinkRepository.deleteAll();
        invoiceRepository.deleteAll();
        customerRepository.deleteAll();
        productRepository.deleteAll();

        // ── Seed Firm A Customers ──
        Customer c1 = new Customer();
        c1.setName("Alice Sharma Hardware");
        c1.setPhone("9876543210");
        c1.setEmail("alice@sharma.in");
        c1.setAddress("Shop 4, MG Road, Pune");
        c1.setGstin("27AAAAA1111A1Z1");
        c1.setFirmId(FIRM_A);
        customerRepository.save(c1);

        Customer c2 = new Customer();
        c2.setName("Bob Electronics");
        c2.setPhone("9123456780");
        c2.setEmail("bob@electronics.com");
        c2.setAddress("Unit 12, Ind Area, Mumbai");
        c2.setGstin("27BBBBB2222B2Z2");
        c2.setFirmId(FIRM_A);
        customerRepository.save(c2);

        // ── Seed Firm B Customer (Multi-Tenant Isolation Check) ──
        Customer cB = new Customer();
        cB.setName("Alice From Firm B");
        cB.setPhone("9876599999");
        cB.setEmail("alice@firmb.com");
        cB.setAddress("Firm B Address");
        cB.setGstin("27CCCCC3333C3Z3");
        cB.setFirmId(FIRM_B);
        customerRepository.save(cB);

        // ── Seed Firm A Products ──
        Product p1 = Product.builder()
                .name("Galvanized Steel Pipe")
                .sku("GSP-34-INCH")
                .barcode("8901234567890")
                .hsnCode("7306")
                .category("Plumbing")
                .unit("meter")
                .price(new BigDecimal("450.00"))
                .costPrice(new BigDecimal("350.00"))
                .stockQuantity(new BigDecimal("120"))
                .minStockLevel(new BigDecimal("10"))
                .itemType("GOODS")
                .firmId(FIRM_A)
                .inInventory(true)
                .isArchived(false)
                .build();
        productRepository.save(p1);

        Product p2 = Product.builder()
                .name("Brass Ball Valve")
                .sku("BBV-HALF-INCH")
                .barcode("8909876543210")
                .hsnCode("8481")
                .category("Valves")
                .unit("pcs")
                .price(new BigDecimal("280.00"))
                .costPrice(new BigDecimal("210.00"))
                .stockQuantity(new BigDecimal("85"))
                .minStockLevel(new BigDecimal("5"))
                .itemType("GOODS")
                .firmId(FIRM_A)
                .inInventory(true)
                .isArchived(false)
                .build();
        productRepository.save(p2);

        // ── Seed Firm B Product (Multi-Tenant Isolation Check) ──
        Product pB = Product.builder()
                .name("Galvanized Pipe Firm B")
                .sku("GSP-FIRM-B")
                .barcode("8901111111111")
                .hsnCode("7306")
                .category("Plumbing")
                .unit("meter")
                .price(new BigDecimal("500.00"))
                .costPrice(new BigDecimal("400.00"))
                .stockQuantity(new BigDecimal("50"))
                .minStockLevel(new BigDecimal("10"))
                .itemType("GOODS")
                .firmId(FIRM_B)
                .inInventory(true)
                .isArchived(false)
                .build();
        productRepository.save(pB);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        businessPartyLinkRepository.deleteAll();
        invoiceRepository.deleteAll();
        customerRepository.deleteAll();
        productRepository.deleteAll();
    }

    // ══════════════════════════════════════════════════════════════
    // Customer Search Autocomplete Tests
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Customer search: Matches by name")
    void testCustomerSearch_ByName() throws Exception {
        mockMvc.perform(get("/api/customers/search")
                        .header("X-Firm-Id", FIRM_A)
                        .param("q", "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Alice Sharma Hardware")))
                .andExpect(jsonPath("$[0].phone", is("9876543210")));
    }

    @Test
    @DisplayName("Customer search: Matches by phone number")
    void testCustomerSearch_ByPhone() throws Exception {
        mockMvc.perform(get("/api/customers/search")
                        .header("X-Firm-Id", FIRM_A)
                        .param("q", "9123456780"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Bob Electronics")));
    }

    @Test
    @DisplayName("Customer search: Matches by GSTIN")
    void testCustomerSearch_ByGstin() throws Exception {
        mockMvc.perform(get("/api/customers/search")
                        .header("X-Firm-Id", FIRM_A)
                        .param("q", "AAAAA1111"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Alice Sharma Hardware")));
    }

    @Test
    @DisplayName("Customer search: Matches by Email")
    void testCustomerSearch_ByEmail() throws Exception {
        mockMvc.perform(get("/api/customers/search")
                        .header("X-Firm-Id", FIRM_A)
                        .param("q", "bob@electronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Bob Electronics")));
    }

    @Test
    @DisplayName("Customer search: Strictly enforces tenant isolation across firms")
    void testCustomerSearch_TenantIsolation() throws Exception {
        // Querying for "Alice" in Firm A must NOT return Firm B's Alice
        mockMvc.perform(get("/api/customers/search")
                        .header("X-Firm-Id", FIRM_A)
                        .param("q", "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].firmId", is(FIRM_A.intValue())))
                .andExpect(jsonPath("$[0].name", is("Alice Sharma Hardware")));

        // Querying for "Alice" in Firm B must return ONLY Firm B's Alice
        mockMvc.perform(get("/api/customers/search")
                        .header("X-Firm-Id", FIRM_B)
                        .param("q", "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].firmId", is(FIRM_B.intValue())))
                .andExpect(jsonPath("$[0].name", is("Alice From Firm B")));
    }

    @Test
    @DisplayName("Customer search: Blank or empty query returns all active firm customers")
    void testCustomerSearch_BlankQuery() throws Exception {
        mockMvc.perform(get("/api/customers/search")
                        .header("X-Firm-Id", FIRM_A)
                        .param("q", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // ══════════════════════════════════════════════════════════════
    // Product Search Autocomplete Tests
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Product search: Matches by product name")
    void testProductSearch_ByName() throws Exception {
        mockMvc.perform(get("/api/products/search")
                        .header("X-Firm-Id", FIRM_A)
                        .param("q", "Galvanized"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Galvanized Steel Pipe")));
    }

    @Test
    @DisplayName("Product search: Matches by SKU")
    void testProductSearch_BySku() throws Exception {
        mockMvc.perform(get("/api/products/search")
                        .header("X-Firm-Id", FIRM_A)
                        .param("q", "BBV-HALF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Brass Ball Valve")));
    }

    @Test
    @DisplayName("Product search: Matches by Barcode")
    void testProductSearch_ByBarcode() throws Exception {
        mockMvc.perform(get("/api/products/search")
                        .header("X-Firm-Id", FIRM_A)
                        .param("q", "8901234567890"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Galvanized Steel Pipe")));
    }

    @Test
    @DisplayName("Product search: Matches by HSN Code")
    void testProductSearch_ByHsnCode() throws Exception {
        mockMvc.perform(get("/api/products/search")
                        .header("X-Firm-Id", FIRM_A)
                        .param("q", "8481"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Brass Ball Valve")));
    }

    @Test
    @DisplayName("Product search: Matches by Category")
    void testProductSearch_ByCategory() throws Exception {
        mockMvc.perform(get("/api/products/search")
                        .header("X-Firm-Id", FIRM_A)
                        .param("q", "Plumbing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Galvanized Steel Pipe")));
    }

    @Test
    @DisplayName("Product search: Strictly enforces tenant isolation across firms")
    void testProductSearch_TenantIsolation() throws Exception {
        mockMvc.perform(get("/api/products/search")
                        .header("X-Firm-Id", FIRM_A)
                        .param("q", "Galvanized"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].firmId", is(FIRM_A.intValue())))
                .andExpect(jsonPath("$[0].name", is("Galvanized Steel Pipe")));

        mockMvc.perform(get("/api/products/search")
                        .header("X-Firm-Id", FIRM_B)
                        .param("q", "Galvanized"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].firmId", is(FIRM_B.intValue())))
                .andExpect(jsonPath("$[0].name", is("Galvanized Pipe Firm B")));
    }

    // ══════════════════════════════════════════════════════════════
    // Unified Items Autocomplete Tests
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Unified items autocomplete: Returns items with SKU and Barcode matching")
    void testUnifiedItemsAutocomplete() throws Exception {
        mockMvc.perform(get("/api/items/autocomplete")
                        .header("X-Firm-Id", FIRM_A)
                        .param("q", "8909876543210"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Brass Ball Valve")))
                .andExpect(jsonPath("$[0].sku", is("BBV-HALF-INCH")))
                .andExpect(jsonPath("$[0].barcode", is("8909876543210")))
                .andExpect(jsonPath("$[0].source", is("INVENTORY")));
    }
}