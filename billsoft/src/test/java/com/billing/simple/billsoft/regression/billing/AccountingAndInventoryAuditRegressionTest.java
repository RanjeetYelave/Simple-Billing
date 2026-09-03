package com.billing.simple.billsoft.regression.billing;

import com.billing.simple.billsoft.dtos.CustomerAnalyticsResponse;
import com.billing.simple.billsoft.dtos.CustomerStatementResponse;
import com.billing.simple.billsoft.dtos.FirmAnalyticsResponse;
import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.InboxMessageRepository;
import com.billing.simple.billsoft.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("regression")
@Tag("integration")
@DisplayName("Accounting, Ledger Precision, Inventory Reversal & Scheduler Audit Regression Tests")
class AccountingAndInventoryAuditRegressionTest {

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
    private PurchaseOrderService purchaseOrderService;

    @Autowired
    private PartyService partyService;

    @Autowired
    private PlannerNotificationScheduler scheduler;

    @Autowired
    private InboxMessageRepository inboxMessageRepository;

    @Autowired
    private BackupService backupService;

    private Customer testCustomer;
    private Product testProduct;
    private Long testFirmId;

    @BeforeEach
    void setUp() {
        backupService.factoryReset();

        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Adversarial Audit Testing Corp");
        firm.setGstin("27AADCB2230M1Z2");
        firm.setPhone("9988776655");
        firm.setEmail("audit@billsoft.io");
        FirmDetails createdFirm = firmService.create(firm);
        testFirmId = createdFirm.getId();

        Customer cust = new Customer();
        cust.setName("Precision Enterprise");
        cust.setPhone("9876543210");
        cust.setEmail("precision@enterprise.com");
        cust.setFirmId(testFirmId);
        testCustomer = customerService.create(cust);

        Product prod = new Product();
        prod.setName("Heavy Machinery Unit");
        prod.setPrice(new BigDecimal("1000.00"));
        prod.setStockQuantity(new BigDecimal("50.00"));
        prod.setGstPercentage(new BigDecimal("0.00"));
        prod.setFirmId(testFirmId);
        testProduct = productService.create(prod);
    }

    @Test
    @DisplayName("Verify partial payments correctly aggregate into totalPaid and totalPending in firm and customer analytics")
    void testPartialPaymentReflectedInDashboardAndAnalytics() {
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(testFirmId);
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.FINAL);
        req.setInvoiceDate(LocalDateTime.now().toString());

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(testProduct.getId());
        item.setQty(10);
        item.setPricePerUnit(new BigDecimal("1000.00"));
        item.setGstPercent(new BigDecimal("0.00")); // Taxable 10,000, GST 0 -> Total 10,000
        req.setItems(List.of(item));

        Invoice inv = invoiceService.createInvoice(req);
        assertThat(inv.getTotalAmount()).isEqualByComparingTo(new BigDecimal("10000.00"));
        assertThat(inv.getPaid()).isFalse();

        // Record partial payment of 4,000 via UPI
        invoiceService.recordPayment(inv.getId(), new BigDecimal("4000.00"), LocalDate.now(), "UPI", "UPI-123456", "Deposit");

        // Verify Customer Analytics
        CustomerAnalyticsResponse custAnalytics = invoiceService.getCustomerAnalytics(testCustomer.getId());
        assertThat(custAnalytics.getTotalBusiness()).isEqualTo(10000.0);
        assertThat(custAnalytics.getTotalPaid()).isEqualTo(4000.0);
        assertThat(custAnalytics.getTotalPending()).isEqualTo(6000.0);

        // Verify Firm Analytics
        FirmAnalyticsResponse firmAnalytics = invoiceService.getFirmAnalytics(testFirmId);
        assertThat(firmAnalytics.getTotalBusiness()).isEqualTo(10000.0);
        assertThat(firmAnalytics.getTotalPaid()).isEqualTo(4000.0);
        assertThat(firmAnalytics.getTotalPending()).isEqualTo(6000.0);

        // Record remaining payment of 6,000 via Cash
        invoiceService.recordPayment(inv.getId(), new BigDecimal("6000.00"), LocalDate.now(), "Cash", "CASH-REC", "Final settlement");

        CustomerAnalyticsResponse settledAnalytics = invoiceService.getCustomerAnalytics(testCustomer.getId());
        assertThat(settledAnalytics.getTotalBusiness()).isEqualTo(10000.0);
        assertThat(settledAnalytics.getTotalPaid()).isEqualTo(10000.0);
        assertThat(settledAnalytics.getTotalPending()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Verify Customer Statement shows exact partial payment records, modes, dates, and running balances")
    void testCustomerStatementChronologicalLedgerWithPartialPayments() {
        LocalDate invDate = LocalDate.now().minusDays(10);
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(testFirmId);
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.FINAL);
        req.setInvoiceDate(invDate.atStartOfDay().toString());

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(testProduct.getId());
        item.setQty(5);
        item.setPricePerUnit(new BigDecimal("1000.00"));
        item.setGstPercent(new BigDecimal("0.00")); // Total 5,000
        req.setItems(List.of(item));

        Invoice inv = invoiceService.createInvoice(req);

        // Payment 1: 2,000 on Day -7
        invoiceService.recordPayment(inv.getId(), new BigDecimal("2000.00"), LocalDate.now().minusDays(7), "NetBanking", "TXN-999", "Installment 1");
        // Payment 2: 3,000 on Day -3
        invoiceService.recordPayment(inv.getId(), new BigDecimal("3000.00"), LocalDate.now().minusDays(3), "Cheque", "CHQ-888", "Installment 2");

        CustomerStatementResponse stmt = statementService.getCustomerStatement(testFirmId, testCustomer.getId(), LocalDate.now().minusDays(15), LocalDate.now());

        assertThat(stmt.getOpeningBalance()).isEqualTo(0.0);
        assertThat(stmt.getTotalBilled()).isEqualTo(5000.0);
        assertThat(stmt.getTotalPaid()).isEqualTo(5000.0);
        assertThat(stmt.getClosingBalance()).isEqualTo(0.0);

        assertThat(stmt.getEntries()).hasSize(3);
        assertThat(stmt.getEntries().get(0).getType()).isEqualTo("INVOICE");
        assertThat(stmt.getEntries().get(0).getDebit()).isEqualTo(5000.0);
        assertThat(stmt.getEntries().get(0).getBalance()).isEqualTo(5000.0);

        assertThat(stmt.getEntries().get(1).getType()).isEqualTo("PAYMENT");
        assertThat(stmt.getEntries().get(1).getCredit()).isEqualTo(2000.0);
        assertThat(stmt.getEntries().get(1).getDescription()).contains("NetBanking");
        assertThat(stmt.getEntries().get(1).getBalance()).isEqualTo(3000.0);

        assertThat(stmt.getEntries().get(2).getType()).isEqualTo("PAYMENT");
        assertThat(stmt.getEntries().get(2).getCredit()).isEqualTo(3000.0);
        assertThat(stmt.getEntries().get(2).getDescription()).contains("Cheque");
        assertThat(stmt.getEntries().get(2).getBalance()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Verify Purchase Order cancellation and deletion reverses inventory stock movements")
    void testPurchaseOrderCancellationReversesStock() {
        Product initialProduct = productService.getById(testProduct.getId());
        BigDecimal initialStock = initialProduct.getStockQuantity();
        assertThat(initialStock).isEqualByComparingTo(new BigDecimal("50.00"));

        Party party = new Party();
        party.setName("Global Industrial Supply");
        party.setFirmId(testFirmId);
        party.setPhone("1122334455");
        party = partyService.createParty(party);

        PurchaseOrder po = new PurchaseOrder();
        po.setFirmId(testFirmId);
        po.setParty(party);
        po.setPoDate(LocalDate.now());
        po.setStatus(PurchaseOrderStatus.ISSUED);

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setProductId(testProduct.getId());
        item.setProductName(testProduct.getName());
        item.setQuantity(new BigDecimal("25.00"));
        item.setUnitPrice(new BigDecimal("800.00"));
        item.setGstPercent(new BigDecimal("0.00"));
        po.setItems(List.of(item));

        PurchaseOrder savedPo = purchaseOrderService.createPurchaseOrder(po);

        // Mark as RECEIVED -> Stock increases by 25 to 75
        PurchaseOrder receivedPo = purchaseOrderService.updateStatus(savedPo.getId(), testFirmId, PurchaseOrderStatus.RECEIVED);
        assertThat(receivedPo.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);

        Product stockAfterReceipt = productService.getById(testProduct.getId());
        assertThat(stockAfterReceipt.getStockQuantity()).isEqualByComparingTo(new BigDecimal("75.00"));

        // Change status to CANCELLED -> Stock should reverse back to 50
        purchaseOrderService.updateStatus(savedPo.getId(), testFirmId, PurchaseOrderStatus.CANCELLED);
        Product stockAfterCancel = productService.getById(testProduct.getId());
        assertThat(stockAfterCancel.getStockQuantity()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("Verify that Estimates with past due dates do NOT trigger false Overdue Invoice alerts")
    void testEstimatesDoNotTriggerOverdueNotifications() {
        inboxMessageRepository.deleteAll();

        // Create an ESTIMATE quotation with past due date
        InvoiceRequest estReq = new InvoiceRequest();
        estReq.setFirmId(testFirmId);
        estReq.setCustomerId(testCustomer.getId());
        estReq.setStatus(InvoiceStatus.ESTIMATE);
        estReq.setDueDate(LocalDate.now().minusDays(5));
        estReq.setInvoiceDate(LocalDateTime.now().minusDays(10).toString());

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(testProduct.getId());
        item.setQty(1);
        item.setPricePerUnit(new BigDecimal("1000.00"));
        estReq.setItems(List.of(item));

        Invoice estimate = invoiceService.createEstimate(estReq);
        assertThat(estimate.getStatus()).isEqualTo(InvoiceStatus.ESTIMATE);

        // Run scheduler
        scheduler.checkOverdueInvoices();

        // Verify no inbox messages were created for this estimate
        long estimateMsgCount = inboxMessageRepository.findAll().stream()
                .filter(m -> m.getSubject() != null && m.getSubject().contains("Overdue Invoice"))
                .count();
        assertThat(estimateMsgCount).isEqualTo(0);

        // Now create a real FINAL invoice with past due date
        InvoiceRequest finalReq = new InvoiceRequest();
        finalReq.setFirmId(testFirmId);
        finalReq.setCustomerId(testCustomer.getId());
        finalReq.setStatus(InvoiceStatus.FINAL);
        finalReq.setDueDate(LocalDate.now().minusDays(3));
        finalReq.setInvoiceDate(LocalDateTime.now().minusDays(8).toString());
        finalReq.setItems(List.of(item));

        invoiceService.createInvoice(finalReq);

        // Run scheduler again
        scheduler.checkOverdueInvoices();

        // Verify overdue alert IS triggered for the final invoice
        long finalMsgCount = inboxMessageRepository.findAll().stream()
                .filter(m -> m.getSubject() != null && m.getSubject().contains("Overdue Invoice"))
                .count();
        assertThat(finalMsgCount).isEqualTo(1);
    }
}
