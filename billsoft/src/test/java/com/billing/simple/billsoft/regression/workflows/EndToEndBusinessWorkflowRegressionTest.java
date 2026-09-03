package com.billing.simple.billsoft.regression.workflows;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("regression")
@Tag("e2e")
@DisplayName("End-to-End Complete Business Workflow Regression Test")
class EndToEndBusinessWorkflowRegressionTest {

    @Autowired
    private FirmDetailsService firmService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private ProductService productService;

    @Autowired
    private PartyService partyService;

    @Autowired
    private PurchaseOrderService poService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoicePdfService invoicePdfService;

    @Autowired
    private BackupService backupService;

    private final Long testFirmId = 1L;

    @BeforeEach
    void setUp() {
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Bharat Heavy Instruments");
        firm.setGstin("27AAACB1234F1Z9");
        firm.setPhone("9822334455");
        firm.setEmail("info@bharatheavy.com");
        firm.setAddressLine1("Industrial Area Phase 2, Pune");
        firmService.create(firm);
    }

    @Test
    @DisplayName("Complete E2E Business Workflow: Vendor PO -> Stock Intake -> Quote -> Convert Invoice -> Payment -> PDF -> Backup")
    void testCompleteBusinessWorkflowE2E() throws Exception {
        // Step 1: Create Product with initial stock of 10 units
        Product product = productService.create(Product.builder()
                .name("Precision Sensor X100")
                .price(BigDecimal.valueOf(2500.00))
                .costPrice(BigDecimal.valueOf(1800.00))
                .stockQuantity(BigDecimal.valueOf(10.0))
                .firmId(testFirmId)
                .itemType("GOODS")
                .unit("unit")
                .hsnCode("9031")
                .gstPercentage(BigDecimal.valueOf(18.0))
                .build());

        assertThat(product.getStockQuantity()).isEqualByComparingTo("10.000");

        // Step 2: Create Vendor & Purchase Order to procure 40 additional units
        Party vendor = partyService.createParty(Party.builder()
                .name("Sensor Component Fabricators Ltd")
                .phone("9922110033")
                .email("sales@sensorfab.com")
                .gstin("27AAACS9988C1Z0")
                .firmId(testFirmId)
                .build());

        PurchaseOrder po = PurchaseOrder.builder()
                .party(vendor)
                .poDate(LocalDate.now())
                .firmId(testFirmId)
                .status(PurchaseOrderStatus.ISSUED)
                .build();

        PurchaseOrderItem poItem = PurchaseOrderItem.builder()
                .productId(product.getId())
                .productName(product.getName())
                .quantity(BigDecimal.valueOf(40.0))
                .unitPrice(BigDecimal.valueOf(1800.00))
                .gstPercent(BigDecimal.valueOf(18.0))
                .build();
        po.setItems(List.of(poItem));

        PurchaseOrder createdPo = poService.createPurchaseOrder(po);
        assertThat(createdPo.getId()).isNotNull();

        // Receive PO -> stock should increase from 10 to 50
        poService.updateStatus(createdPo.getId(), testFirmId, PurchaseOrderStatus.RECEIVED);
        Product stockAfterPo = productService.getById(product.getId());
        assertThat(stockAfterPo.getStockQuantity()).isEqualByComparingTo("50.000");

        // Step 3: Onboard Customer
        Customer customer = customerService.create(Customer.builder()
                .name("Mega Projects Ltd")
                .phone("9876543210")
                .email("procurement@megaprojects.com")
                .gstin("27AAACM4455E1Z8")
                .firmId(testFirmId)
                .build());

        // Step 4: Create Quotation for 15 units
        InvoiceRequest quoteReq = new InvoiceRequest();
        quoteReq.setFirmId(testFirmId);
        quoteReq.setCustomerId(customer.getId());
        quoteReq.setStatus(InvoiceStatus.ESTIMATE);

        InvoiceRequestItem quoteItem = new InvoiceRequestItem();
        quoteItem.setProductId(product.getId());
        quoteItem.setQty(15);
        quoteItem.setPricePerUnit(BigDecimal.valueOf(2500.00));
        quoteItem.setGstPercent(BigDecimal.valueOf(18.00));
        quoteReq.setItems(List.of(quoteItem));

        Invoice quotation = invoiceService.createInvoice(quoteReq);
        assertThat(quotation.getStatus()).isEqualTo(InvoiceStatus.ESTIMATE);
        assertThat(quotation.getEstimateNumber()).isNotNull();

        // Step 5: Convert Quotation to Tax Invoice -> stock should decrease from 50 to 35
        Invoice invoice = invoiceService.convertEstimateToInvoice(quotation.getId(), null);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.FINAL);
        assertThat(invoice.getInvoiceNumber()).isNotNull();

        Product stockAfterInvoice = productService.getById(product.getId());
        assertThat(stockAfterInvoice.getStockQuantity()).isEqualByComparingTo("35.000");

        // Step 6: Process Partial Payment (Invoice Total: 15 * 2500 = 37500 + 18% = 44250)
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("44250.00");
        InvoicePayment payment = invoiceService.recordPayment(invoice.getId(), BigDecimal.valueOf(20000.00), LocalDate.now(), "BANK_TRANSFER", "NEFT-12345", "Part Payment");
        assertThat(payment.getId()).isNotNull();

        // Step 7: Generate Invoice PDF
        byte[] pdfBytes = invoicePdfService.generatePdf(invoice, "A4");
        assertThat(pdfBytes).isNotNull();
        String pdfHeader = new String(pdfBytes, 0, Math.min(pdfBytes.length, 8), StandardCharsets.US_ASCII);
        assertThat(pdfHeader).startsWith("%PDF-");

        // Step 8: Export Full Backup and verify all records are captured
        BackupDTO backup = backupService.exportData(testFirmId);
        assertThat(backup.getCustomers()).isNotEmpty();
        assertThat(backup.getProducts()).isNotEmpty();
        assertThat(backup.getInvoices()).isNotEmpty();
    }
}
