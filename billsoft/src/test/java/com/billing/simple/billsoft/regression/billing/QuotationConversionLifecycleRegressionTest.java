package com.billing.simple.billsoft.regression.billing;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceStatus;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.InvoiceService;
import com.billing.simple.billsoft.service.ProductService;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("regression")
@Tag("billing")
@DisplayName("Quotation-to-Invoice Conversion State & Lifecycle Regression Tests")
class QuotationConversionLifecycleRegressionTest {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private ProductService productService;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private MockMvc mockMvc;

    private Customer testCustomer;
    private Product testProduct;
    private final Long testFirmId = 1L;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentFirmId(testFirmId);

        testCustomer = new Customer();
        testCustomer.setName("Acme Corporation");
        testCustomer.setPhone("9876543210");
        testCustomer.setFirmId(testFirmId);
        testCustomer = customerRepo.save(testCustomer);

        testProduct = new Product();
        testProduct.setName("Enterprise Cloud Suite");
        testProduct.setPrice(BigDecimal.valueOf(200.0));
        testProduct.setStockQuantity(new BigDecimal("100.000"));
        testProduct.setFirmId(testFirmId);
        testProduct = productRepo.save(testProduct);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private InvoiceRequestItem createItem(Long productId, int qty, double price) {
        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(productId);
        item.setQty(qty);
        item.setPricePerUnit(BigDecimal.valueOf(price));
        return item;
    }

    private Invoice createTestQuotation() {
        InvoiceRequest estReq = new InvoiceRequest();
        estReq.setFirmId(testFirmId);
        estReq.setCustomerId(testCustomer.getId());
        estReq.setStatus(InvoiceStatus.ESTIMATE);
        estReq.setCustomerNote("Valid for 30 days");
        estReq.setTermsAndConditions("Payment due upon receipt");
        estReq.setItems(List.of(createItem(testProduct.getId(), 5, 200.0)));
        return invoiceService.createInvoice(estReq);
    }

    @Test
    @DisplayName("1. New quotation should be NOT_CONVERTED and return NOT_CONVERTED via REST API")
    void newQuotationShouldBeNotConverted() throws Exception {
        Invoice estimate = createTestQuotation();
        assertThat(estimate.getId()).isNotNull();
        assertThat(estimate.getStatus()).isEqualTo(InvoiceStatus.ESTIMATE);
        assertThat(estimate.getConvertedInvoiceId()).isNull();

        Invoice fetched = invoiceService.getById(estimate.getId());
        assertThat(fetched.getConversionStatus()).isEqualTo("NOT_CONVERTED");

        // Verify REST API serialization of conversionStatus
        mockMvc.perform(get("/api/invoices/" + estimate.getId())
                .header("X-Firm-Id", testFirmId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversionStatus").value("NOT_CONVERTED"))
                .andExpect(jsonPath("$.convertedInvoiceId").doesNotExist());

        // Linked invoice endpoint should return 404 for unconverted quotation
        mockMvc.perform(get("/api/invoices/" + estimate.getId() + "/linked-invoice")
                .header("X-Firm-Id", testFirmId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("2 & 3 & 4. Converting quotation creates invoice, transitions to CONVERTED, and links invoice")
    void convertingQuotationShouldCreateInvoiceAndTransitionToConverted() throws Exception {
        Invoice estimate = createTestQuotation();

        // Stock before conversion
        Product prodBefore = productService.getById(testProduct.getId());
        assertThat(prodBefore.getStockQuantity()).isEqualByComparingTo("100.000");

        // Convert quotation to invoice
        Invoice convertedInvoice = invoiceService.convertEstimateToInvoice(estimate.getId(), null);
        assertThat(convertedInvoice.getId()).isNotNull();
        assertThat(convertedInvoice.getStatus()).isEqualTo(InvoiceStatus.UNPAID);
        assertThat(convertedInvoice.getInvoiceNumber()).isNotNull();
        assertThat(convertedInvoice.getCustomer().getId()).isEqualTo(testCustomer.getId());
        assertThat(convertedInvoice.getTotalAmount()).isEqualByComparingTo(estimate.getTotalAmount());

        // Stock deducted
        Product prodAfter = productService.getById(testProduct.getId());
        assertThat(prodAfter.getStockQuantity()).isEqualByComparingTo("95.000");

        // Quotation state
        Invoice reloadedEstimate = invoiceService.getById(estimate.getId());
        assertThat(reloadedEstimate.getConvertedInvoiceId()).isEqualTo(convertedInvoice.getId());
        assertThat(reloadedEstimate.getConversionStatus()).isEqualTo("CONVERTED");

        // Verify REST API serialization of CONVERTED state
        mockMvc.perform(get("/api/invoices/" + estimate.getId())
                .header("X-Firm-Id", testFirmId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversionStatus").value("CONVERTED"))
                .andExpect(jsonPath("$.convertedInvoiceId").value(convertedInvoice.getId()));

        // Linked invoice endpoint should return the converted invoice
        mockMvc.perform(get("/api/invoices/" + estimate.getId() + "/linked-invoice")
                .header("X-Firm-Id", testFirmId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(convertedInvoice.getId()))
                .andExpect(jsonPath("$.invoiceNumber").value(convertedInvoice.getInvoiceNumber()));

        // Attempting to convert again while converted invoice is alive must be rejected
        TenantContext.setCurrentFirmId(testFirmId);
        assertThatThrownBy(() -> invoiceService.convertEstimateToInvoice(estimate.getId(), null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already been converted to invoice #" + convertedInvoice.getId());
    }

    @Test
    @DisplayName("5 & 6 & 7. Deleting converted invoice transitions quotation to CONVERTED_INVOICE_DELETED, linked-invoice returns 404")
    void deletingConvertedInvoiceShouldTransitionQuotationToDeletedState() throws Exception {
        Invoice estimate = createTestQuotation();
        Invoice convertedInvoice = invoiceService.convertEstimateToInvoice(estimate.getId(), null);
        Long invoiceId = convertedInvoice.getId();

        // Delete the converted invoice
        boolean deleted = invoiceService.delete(invoiceId);
        assertThat(deleted).isTrue();
        assertThat(invoiceRepo.existsById(invoiceId)).isFalse();

        // Quotation should retain its details and transition to CONVERTED_INVOICE_DELETED
        Invoice reloadedEstimate = invoiceService.getById(estimate.getId());
        assertThat(reloadedEstimate).isNotNull();
        assertThat(reloadedEstimate.getCustomer().getName()).isEqualTo("Acme Corporation");
        assertThat(reloadedEstimate.getCustomerNote()).isEqualTo("Valid for 30 days");
        assertThat(reloadedEstimate.getConversionStatus()).isEqualTo("CONVERTED_INVOICE_DELETED");

        // Verify REST API serialization reflects CONVERTED_INVOICE_DELETED
        mockMvc.perform(get("/api/invoices/" + estimate.getId())
                .header("X-Firm-Id", testFirmId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversionStatus").value("CONVERTED_INVOICE_DELETED"));

        // Linked invoice endpoint must return 404, preventing stale navigation
        mockMvc.perform(get("/api/invoices/" + estimate.getId() + "/linked-invoice")
                .header("X-Firm-Id", testFirmId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("8 & 9 & 10. Reconversion creates a brand new invoice and updates active conversion reference")
    void reconversionShouldCreateNewInvoiceAndUpdateActiveReference() throws Exception {
        Invoice estimate = createTestQuotation();
        Invoice firstInvoice = invoiceService.convertEstimateToInvoice(estimate.getId(), null);
        Long firstInvoiceId = firstInvoice.getId();
        String firstInvoiceNum = firstInvoice.getInvoiceNumber();

        // Delete first invoice (stock is restored)
        invoiceService.delete(firstInvoiceId);
        Product prodAfterDelete = productService.getById(testProduct.getId());
        assertThat(prodAfterDelete.getStockQuantity()).isEqualByComparingTo("100.000");

        // Reconvert quotation via Convert Again
        Invoice secondInvoice = invoiceService.convertEstimateToInvoice(estimate.getId(), null);
        assertThat(secondInvoice.getId()).isNotNull();
        assertThat(secondInvoice.getId()).isNotEqualTo(firstInvoiceId);
        assertThat(secondInvoice.getInvoiceNumber()).isNotEqualTo(firstInvoiceNum);
        assertThat(secondInvoice.getCustomer().getId()).isEqualTo(testCustomer.getId());
        assertThat(secondInvoice.getTotalAmount()).isEqualByComparingTo(estimate.getTotalAmount());

        // Stock deducted for new invoice
        Product prodAfterReconvert = productService.getById(testProduct.getId());
        assertThat(prodAfterReconvert.getStockQuantity()).isEqualByComparingTo("95.000");

        // Quotation active reference must point to second invoice
        Invoice reloadedEstimate = invoiceService.getById(estimate.getId());
        assertThat(reloadedEstimate.getConvertedInvoiceId()).isEqualTo(secondInvoice.getId());
        assertThat(reloadedEstimate.getConversionStatus()).isEqualTo("CONVERTED");

        // REST API verification: points to new invoice
        mockMvc.perform(get("/api/invoices/" + estimate.getId())
                .header("X-Firm-Id", testFirmId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversionStatus").value("CONVERTED"))
                .andExpect(jsonPath("$.convertedInvoiceId").value(secondInvoice.getId()));

        // Linked invoice endpoint now opens the new invoice
        mockMvc.perform(get("/api/invoices/" + estimate.getId() + "/linked-invoice")
                .header("X-Firm-Id", testFirmId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(secondInvoice.getId()))
                .andExpect(jsonPath("$.invoiceNumber").value(secondInvoice.getInvoiceNumber()));
    }

    @Test
    @DisplayName("10b. Review & Edit reconversion flow: saving invoice referencing quotation after invoice deletion")
    void reviewAndEditReconversionFlowShouldSucceed() {
        Invoice estimate = createTestQuotation();
        Invoice firstInvoice = invoiceService.convertEstimateToInvoice(estimate.getId(), null);
        invoiceService.delete(firstInvoice.getId());

        // Simulate Review & Edit flow: user submits new invoice with convertedInvoiceId = estimate.id
        InvoiceRequest reviewReq = new InvoiceRequest();
        reviewReq.setFirmId(testFirmId);
        reviewReq.setCustomerId(testCustomer.getId());
        reviewReq.setStatus(InvoiceStatus.FINAL);
        reviewReq.setConvertedInvoiceId(estimate.getId());
        reviewReq.setCustomerNote("Custom adjustment during reconversion");
        reviewReq.setItems(List.of(createItem(testProduct.getId(), 6, 190.0)));

        Invoice newInv = invoiceService.createInvoice(reviewReq);
        assertThat(newInv.getId()).isNotNull();
        assertThat(newInv.getId()).isNotEqualTo(firstInvoice.getId());

        // Quotation should be linked to this new invoice
        Invoice reloaded = invoiceService.getById(estimate.getId());
        assertThat(reloaded.getConvertedInvoiceId()).isEqualTo(newInv.getId());
        assertThat(reloaded.getConversionStatus()).isEqualTo("CONVERTED");
    }

    @Test
    @DisplayName("11. Concurrency safety: simultaneous conversion attempts should create only 1 active invoice")
    void concurrentConversionAttemptsShouldPreventDuplicates() throws Exception {
        Invoice estimate = createTestQuotation();

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TenantContext.setCurrentFirmId(testFirmId);
                    invoiceService.convertEstimateToInvoice(estimate.getId(), null);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    TenantContext.clear();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // Exactly one concurrent attempt must succeed; the rest must be rejected
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(errorCount.get()).isEqualTo(threadCount - 1);

        // Verify only 1 active invoice was created for this quotation
        Invoice reloaded = invoiceService.getById(estimate.getId());
        assertThat(reloaded.getConversionStatus()).isEqualTo("CONVERTED");
        assertThat(reloaded.getConvertedInvoiceId()).isNotNull();
    }

    @Test
    @DisplayName("12 & 13. Deleting quotation whose converted invoice was deleted should succeed")
    void deletingQuotationWhoseConvertedInvoiceWasDeletedShouldSucceed() {
        Invoice estimate = createTestQuotation();
        Invoice invoice = invoiceService.convertEstimateToInvoice(estimate.getId(), null);

        // Deleting estimate while invoice is alive must be blocked
        assertThatThrownBy(() -> invoiceService.delete(estimate.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cannot delete this estimate as it has already been converted to invoice #" + invoice.getId());

        // Delete invoice
        invoiceService.delete(invoice.getId());

        // Now deleting the quotation should succeed
        boolean deleted = invoiceService.delete(estimate.getId());
        assertThat(deleted).isTrue();
        assertThat(invoiceRepo.existsById(estimate.getId())).isFalse();
    }

    @Test
    @DisplayName("Tenant Isolation: Missing authoritative TenantContext must reject quotation conversion")
    void convertEstimateToInvoice_missingTenantContext_mustThrowTenantSecurityException() {
        Invoice estimate = createTestQuotation();
        TenantContext.clear();

        assertThatThrownBy(() -> invoiceService.convertEstimateToInvoice(estimate.getId(), null))
                .isInstanceOf(com.billing.simple.billsoft.security.TenantSecurityException.class)
                .hasMessageContaining("Authoritative tenant context is required");
    }

    @Test
    @DisplayName("Tenant Isolation: Cross-firm quotation conversion must be strictly rejected")
    void convertEstimateToInvoice_crossFirmAccess_mustThrowTenantSecurityException() {
        Invoice estimate = createTestQuotation();
        Long foreignFirmId = 999L;
        TenantContext.setCurrentFirmId(foreignFirmId);

        assertThatThrownBy(() -> invoiceService.convertEstimateToInvoice(estimate.getId(), null))
                .isInstanceOf(com.billing.simple.billsoft.security.TenantSecurityException.class);
    }
}
