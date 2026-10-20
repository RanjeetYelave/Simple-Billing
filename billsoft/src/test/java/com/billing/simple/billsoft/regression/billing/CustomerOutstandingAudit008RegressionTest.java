package com.billing.simple.billsoft.regression.billing;

import com.billing.simple.billsoft.assistant.dto.OmnisearchQuickHelpDTOs.CustomerQuickHelpDTO;
import com.billing.simple.billsoft.assistant.service.OmnisearchQuickHelpService;
import com.billing.simple.billsoft.dtos.*;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.enums.PrintFormat;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.billing.simple.billsoft.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@DisplayName("AUDIT-008: Customer Outstanding & Balance Consolidation Regression Tests")
public class CustomerOutstandingAudit008RegressionTest {

    @Autowired
    private CustomerBalanceService customerBalanceService;

    @Autowired
    private CustomerSettlementService customerSettlementService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private OmnisearchQuickHelpService omnisearchQuickHelpService;

    @Autowired
    private StatementService statementService;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private InvoicePaymentRepository paymentRepo;

    @Autowired
    private SalesReturnRepository salesReturnRepo;

    @Autowired
    private FirmDetailsRepository firmRepo;

    @Autowired
    private BackupService backupService;

    private Long firmAId;
    private Long firmBId;
    private Customer customerA;
    private Customer customerB;
    private Product productA;

    @BeforeEach
    void setUp() {
        backupService.factoryReset();

        FirmDetails firmA = new FirmDetails();
        firmA.setFirmName("Firm Alpha");
        firmA.setGstin("27AAAAA0000A1Z5");
        firmA.setState("Maharashtra");
        firmA = firmRepo.save(firmA);
        firmAId = firmA.getId();

        FirmDetails firmB = new FirmDetails();
        firmB.setFirmName("Firm Beta");
        firmB.setGstin("27BBBBB1111B1Z6");
        firmB.setState("Maharashtra");
        firmB = firmRepo.save(firmB);
        firmBId = firmB.getId();

        TenantContext.setCurrentFirmId(firmAId);

        customerA = new Customer();
        customerA.setName("Customer Alpha");
        customerA.setPhone("9811111111");
        customerA.setFirmId(firmAId);
        customerA = customerRepo.save(customerA);

        customerB = new Customer();
        customerB.setName("Customer Beta");
        customerB.setPhone("9822222222");
        customerB.setFirmId(firmBId);
        customerB = customerRepo.save(customerB);

        productA = new Product();
        productA.setName("Product Widget");
        productA.setPrice(new BigDecimal("100.00"));
        productA.setStockQuantity(new BigDecimal("500.00"));
        productA.setFirmId(firmAId);
        productA = productRepo.save(productA);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Invoice createTestInvoice(Long firmId, Customer cust, InvoiceStatus status, BigDecimal amount, Boolean paid) {
        Invoice inv = new Invoice();
        inv.setFirmId(firmId);
        inv.setCustomer(cust);
        inv.setInvoiceNumber("INV-" + System.nanoTime());
        inv.setStatus(status);
        inv.setTotalAmount(amount);
        inv.setPaid(paid != null ? paid : false);
        inv.setInvoiceDate(LocalDateTime.now());
        return invoiceRepo.save(inv);
    }

    @Test
    @DisplayName("1. Customer 360 excludes DRAFT invoices from billed and outstanding totals")
    void testCustomer360ExcludesDraftInvoices() {
        createTestInvoice(firmAId, customerA, InvoiceStatus.DRAFT, new BigDecimal("500.00"), false);
        createTestInvoice(firmAId, customerA, InvoiceStatus.FINAL, new BigDecimal("1000.00"), false);

        Customer360Response c360 = customerSettlementService.getCustomer360(customerA.getId());

        assertThat(c360.getTotalBilled()).isEqualTo(1000.0);
        assertThat(c360.getNetBalance()).isEqualTo(1000.0);
        assertThat(c360.getOutstandingInvoices()).hasSize(1);
        assertThat(c360.getOutstandingInvoices().get(0).getTotalAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("2. Customer 360 excludes ESTIMATE and CANCELLED invoices")
    void testCustomer360ExcludesEstimateAndCancelledInvoices() {
        createTestInvoice(firmAId, customerA, InvoiceStatus.ESTIMATE, new BigDecimal("400.00"), false);
        createTestInvoice(firmAId, customerA, InvoiceStatus.CANCELLED, new BigDecimal("600.00"), false);
        createTestInvoice(firmAId, customerA, InvoiceStatus.FINAL, new BigDecimal("1000.00"), false);

        Customer360Response c360 = customerSettlementService.getCustomer360(customerA.getId());

        assertThat(c360.getTotalBilled()).isEqualTo(1000.0);
        assertThat(c360.getNetBalance()).isEqualTo(1000.0);
        assertThat(c360.getOutstandingInvoices()).hasSize(1);
        assertThat(c360.getOutstandingInvoices().get(0).getStatus()).isEqualTo(InvoiceStatus.FINAL);
    }

    @Test
    @DisplayName("3. Customer 360 matches authoritative balance summary exactly")
    void testCustomer360MatchesAuthoritativeBalance() {
        Invoice inv = createTestInvoice(firmAId, customerA, InvoiceStatus.FINAL, new BigDecimal("1200.00"), false);
        InvoicePayment pmt = new InvoicePayment();
        pmt.setFirmId(firmAId);
        pmt.setCustomerId(customerA.getId());
        pmt.setInvoiceId(inv.getId());
        pmt.setAmount(new BigDecimal("400.00"));
        pmt.setPaymentDate(LocalDate.now());
        paymentRepo.save(pmt);

        CustomerBalanceSummary bal = customerBalanceService.getCustomerBalance(firmAId, customerA.getId());
        Customer360Response c360 = customerSettlementService.getCustomer360(customerA.getId());

        assertThat(BigDecimal.valueOf(c360.getTotalBilled())).isEqualByComparingTo(bal.getTotalBilled());
        assertThat(BigDecimal.valueOf(c360.getTotalPaid())).isEqualByComparingTo(bal.getTotalPaid());
        assertThat(BigDecimal.valueOf(c360.getNetBalance())).isEqualByComparingTo(bal.getClosingBalance());
    }

    @Test
    @DisplayName("4. Customer Analytics excludes DRAFT, ESTIMATE, and CANCELLED invoices")
    void testCustomerAnalyticsExcludesDraftEstimateCancelled() {
        createTestInvoice(firmAId, customerA, InvoiceStatus.DRAFT, new BigDecimal("500.00"), false);
        createTestInvoice(firmAId, customerA, InvoiceStatus.ESTIMATE, new BigDecimal("400.00"), false);
        createTestInvoice(firmAId, customerA, InvoiceStatus.CANCELLED, new BigDecimal("300.00"), false);
        createTestInvoice(firmAId, customerA, InvoiceStatus.FINAL, new BigDecimal("2000.00"), false);

        CustomerAnalyticsResponse analytics = invoiceService.getCustomerAnalytics(customerA.getId());

        assertThat(analytics.getTotalBusiness()).isEqualTo(2000.0);
        assertThat(analytics.getTotalPending()).isEqualTo(2000.0);
        assertThat(analytics.getInvoiceCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("5. Customer Analytics includes unallocated advance payment")
    void testCustomerAnalyticsIncludesUnallocatedAdvance() {
        createTestInvoice(firmAId, customerA, InvoiceStatus.FINAL, new BigDecimal("1000.00"), false);

        // Unallocated advance payment (invoiceId is null)
        InvoicePayment advance = new InvoicePayment();
        advance.setFirmId(firmAId);
        advance.setCustomerId(customerA.getId());
        advance.setInvoiceId(null);
        advance.setAmount(new BigDecimal("300.00"));
        advance.setPaymentDate(LocalDate.now());
        paymentRepo.save(advance);

        CustomerAnalyticsResponse analytics = invoiceService.getCustomerAnalytics(customerA.getId());

        assertThat(analytics.getTotalPaid()).isEqualTo(300.0);
        assertThat(analytics.getTotalPending()).isEqualTo(700.0);
    }

    @Test
    @DisplayName("6. Paid invoice with sales return produces customer credit balance (does not clamp to zero)")
    void testPaidInvoiceWithSalesReturnProducesCustomerCredit() {
        // Invoice ₹1,000
        Invoice inv = createTestInvoice(firmAId, customerA, InvoiceStatus.PAID, new BigDecimal("1000.00"), true);

        // Payment ₹1,000
        InvoicePayment pmt = new InvoicePayment();
        pmt.setFirmId(firmAId);
        pmt.setCustomerId(customerA.getId());
        pmt.setInvoiceId(inv.getId());
        pmt.setAmount(new BigDecimal("1000.00"));
        pmt.setPaymentDate(LocalDate.now());
        paymentRepo.save(pmt);

        // Sales Return ₹300 refund
        SalesReturn ret = new SalesReturn();
        ret.setFirmId(firmAId);
        ret.setCustomer(customerA);
        ret.setInvoice(inv);
        ret.setReturnNumber("RET-001");
        ret.setReturnDate(LocalDate.now());
        ret.setTotalRefundAmount(new BigDecimal("300.00"));
        salesReturnRepo.save(ret);

        CustomerBalanceSummary bal = customerBalanceService.getCustomerBalance(firmAId, customerA.getId());
        assertThat(bal.getTotalBilled()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(bal.getTotalPaid()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(bal.getTotalReturns()).isEqualByComparingTo(new BigDecimal("300.00"));
        // Authoritative balance: 1000 - 1000 - 300 = -300. Must NOT clamp to zero!
        assertThat(bal.getClosingBalance()).isEqualByComparingTo(new BigDecimal("-300.00"));

        Customer360Response c360 = customerSettlementService.getCustomer360(customerA.getId());
        assertThat(c360.getNetBalance()).isEqualTo(-300.0);

        CustomerQuickHelpDTO help = omnisearchQuickHelpService.assembleCustomerDetails(firmAId, customerA);
        assertThat(help.getCurrentOutstanding()).isEqualByComparingTo(new BigDecimal("-300.00"));
    }

    @Test
    @DisplayName("7. OmniSearch recognizes legacy paid invoice without child payment records")
    void testOmniSearchRecognizesLegacyPaidInvoice() {
        // Legacy paid invoice with paid = true and no InvoicePayment child record
        createTestInvoice(firmAId, customerA, InvoiceStatus.PAID, new BigDecimal("1000.00"), true);

        CustomerQuickHelpDTO help = omnisearchQuickHelpService.assembleCustomerDetails(firmAId, customerA);

        assertThat(help.getTotalSales()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(help.getTotalPaid()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(help.getCurrentOutstanding()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("8. OmniSearch preserves negative customer balance")
    void testOmniSearchPreservesNegativeCustomerBalance() {
        Invoice inv = createTestInvoice(firmAId, customerA, InvoiceStatus.PAID, new BigDecimal("500.00"), true);
        InvoicePayment pmt = new InvoicePayment();
        pmt.setFirmId(firmAId);
        pmt.setCustomerId(customerA.getId());
        pmt.setInvoiceId(inv.getId());
        pmt.setAmount(new BigDecimal("500.00"));
        pmt.setPaymentDate(LocalDate.now());
        paymentRepo.save(pmt);

        SalesReturn ret = new SalesReturn();
        ret.setFirmId(firmAId);
        ret.setCustomer(customerA);
        ret.setInvoice(inv);
        ret.setReturnNumber("RET-002");
        ret.setReturnDate(LocalDate.now());
        ret.setTotalRefundAmount(new BigDecimal("200.00"));
        salesReturnRepo.save(ret);

        CustomerQuickHelpDTO help = omnisearchQuickHelpService.assembleCustomerDetails(firmAId, customerA);
        // 500 - 500 - 200 = -200
        assertThat(help.getCurrentOutstanding()).isEqualByComparingTo(new BigDecimal("-200.00"));
    }

    @Test
    @DisplayName("9. Customer Balance enforces multi-tenant isolation")
    void testCustomerBalanceMultiTenantIsolation() {
        createTestInvoice(firmAId, customerA, InvoiceStatus.FINAL, new BigDecimal("1500.00"), false);
        createTestInvoice(firmBId, customerB, InvoiceStatus.FINAL, new BigDecimal("2500.00"), false);

        // Firm A balance for customer A
        TenantContext.setCurrentFirmId(firmAId);
        CustomerBalanceSummary balA = customerBalanceService.getCustomerBalance(firmAId, customerA.getId());
        assertThat(balA.getTotalBilled()).isEqualByComparingTo(new BigDecimal("1500.00"));

        // While in Firm A context, passing Firm B throws TenantSecurityException
        assertThatThrownBy(() -> customerBalanceService.getCustomerBalance(firmBId, customerA.getId()))
                .isInstanceOf(TenantSecurityException.class);

        // When context is Firm B, customer A (from Firm A) cannot be accessed (throws TenantSecurityException)
        TenantContext.setCurrentFirmId(firmBId);
        assertThatThrownBy(() -> customerBalanceService.getCustomerBalance(firmBId, customerA.getId()))
                .isInstanceOf(TenantSecurityException.class);

        // Firm B can access customer B
        CustomerBalanceSummary balB = customerBalanceService.getCustomerBalance(firmBId, customerB.getId());
        assertThat(balB.getTotalBilled()).isEqualByComparingTo(new BigDecimal("2500.00"));

        // Firm A context trying to access Customer B via settlement throws TenantSecurityException
        TenantContext.setCurrentFirmId(firmAId);
        assertThatThrownBy(() -> customerSettlementService.getCustomer360(customerB.getId()))
                .isInstanceOf(TenantSecurityException.class);
    }

    @Test
    @DisplayName("10. Customer Analytics requires valid tenant context and forbids cross-firm access")
    void testCustomerAnalyticsRequiresTenantContext() {
        createTestInvoice(firmBId, customerB, InvoiceStatus.FINAL, new BigDecimal("1000.00"), false);

        TenantContext.setCurrentFirmId(firmAId);
        // Firm A cannot access customer B analytics
        assertThatThrownBy(() -> invoiceService.getCustomerAnalytics(customerB.getId()))
                .isInstanceOf(TenantSecurityException.class);
    }

    @Test
    @DisplayName("11. Outstanding invoice list strictly excludes DRAFT, ESTIMATE, and CANCELLED")
    void testOutstandingInvoiceListExcludesDraft() {
        createTestInvoice(firmAId, customerA, InvoiceStatus.DRAFT, new BigDecimal("100.00"), false);
        createTestInvoice(firmAId, customerA, InvoiceStatus.ESTIMATE, new BigDecimal("200.00"), false);
        createTestInvoice(firmAId, customerA, InvoiceStatus.CANCELLED, new BigDecimal("300.00"), false);
        Invoice finalInv = createTestInvoice(firmAId, customerA, InvoiceStatus.FINAL, new BigDecimal("1000.00"), false);
        Invoice unpaidInv = createTestInvoice(firmAId, customerA, InvoiceStatus.UNPAID, new BigDecimal("500.00"), false);

        List<CustomerOutstandingInvoiceDto> outstanding = customerSettlementService.getOutstandingInvoices(customerA.getId());

        assertThat(outstanding).hasSize(2);
        assertThat(outstanding).extracting(CustomerOutstandingInvoiceDto::getInvoiceId)
                .containsExactlyInAnyOrder(finalInv.getId(), unpaidInv.getId());
    }

    @Test
    @DisplayName("12. Authoritative customer balance matches StatementService closing balance")
    void testCustomerBalanceMatchesStatementBalance() {
        Invoice inv1 = createTestInvoice(firmAId, customerA, InvoiceStatus.FINAL, new BigDecimal("2000.00"), false);
        Invoice inv2 = createTestInvoice(firmAId, customerA, InvoiceStatus.FINAL, new BigDecimal("1000.00"), false);

        InvoicePayment pmt = new InvoicePayment();
        pmt.setFirmId(firmAId);
        pmt.setCustomerId(customerA.getId());
        pmt.setInvoiceId(inv1.getId());
        pmt.setAmount(new BigDecimal("500.00"));
        pmt.setPaymentDate(LocalDate.now());
        paymentRepo.save(pmt);

        SalesReturn ret = new SalesReturn();
        ret.setFirmId(firmAId);
        ret.setCustomer(customerA);
        ret.setInvoice(inv2);
        ret.setReturnNumber("RET-003");
        ret.setReturnDate(LocalDate.now());
        ret.setTotalRefundAmount(new BigDecimal("300.00"));
        salesReturnRepo.save(ret);

        CustomerBalanceSummary bal = customerBalanceService.getCustomerBalance(firmAId, customerA.getId());
        CustomerStatementResponse stmt = statementService.getCustomerStatement(firmAId, customerA.getId(), null, null);

        assertThat(bal.getClosingBalance()).isEqualByComparingTo(BigDecimal.valueOf(stmt.getClosingBalance()));
        // 3000 - 500 - 300 = 2200
        assertThat(bal.getClosingBalance()).isEqualByComparingTo(new BigDecimal("2200.00"));
    }

    @Test
    @DisplayName("13. Multiple invoices with mixed statuses aggregate only active issued statuses")
    void testMultipleInvoicesMixedStatuses() {
        createTestInvoice(firmAId, customerA, InvoiceStatus.DRAFT, new BigDecimal("100.00"), false);
        createTestInvoice(firmAId, customerA, InvoiceStatus.ESTIMATE, new BigDecimal("200.00"), false);
        createTestInvoice(firmAId, customerA, InvoiceStatus.CANCELLED, new BigDecimal("300.00"), false);
        createTestInvoice(firmAId, customerA, InvoiceStatus.FINAL, new BigDecimal("1000.00"), false);
        createTestInvoice(firmAId, customerA, InvoiceStatus.UNPAID, new BigDecimal("500.00"), false);
        createTestInvoice(firmAId, customerA, InvoiceStatus.PAID, new BigDecimal("600.00"), true);
        createTestInvoice(firmAId, customerA, InvoiceStatus.OVERDUE, new BigDecimal("400.00"), false);
        createTestInvoice(firmAId, customerA, InvoiceStatus.SENT, new BigDecimal("300.00"), false);

        // Active: 1000 + 500 + 600 + 400 + 300 = 2800
        CustomerBalanceSummary bal = customerBalanceService.getCustomerBalance(firmAId, customerA.getId());
        assertThat(bal.getTotalBilled()).isEqualByComparingTo(new BigDecimal("2800.00"));
    }

    @Test
    @DisplayName("14. Partial payment and sales return aggregate correctly")
    void testPartialPaymentAndSalesReturn() {
        Invoice inv = createTestInvoice(firmAId, customerA, InvoiceStatus.FINAL, new BigDecimal("2000.00"), false);

        InvoicePayment pmt = new InvoicePayment();
        pmt.setFirmId(firmAId);
        pmt.setCustomerId(customerA.getId());
        pmt.setInvoiceId(inv.getId());
        pmt.setAmount(new BigDecimal("800.00"));
        pmt.setPaymentDate(LocalDate.now());
        paymentRepo.save(pmt);

        SalesReturn ret = new SalesReturn();
        ret.setFirmId(firmAId);
        ret.setCustomer(customerA);
        ret.setInvoice(inv);
        ret.setReturnNumber("RET-004");
        ret.setReturnDate(LocalDate.now());
        ret.setTotalRefundAmount(new BigDecimal("400.00"));
        salesReturnRepo.save(ret);

        CustomerBalanceSummary bal = customerBalanceService.getCustomerBalance(firmAId, customerA.getId());
        assertThat(bal.getTotalBilled()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(bal.getTotalPaid()).isEqualByComparingTo(new BigDecimal("800.00"));
        assertThat(bal.getTotalReturns()).isEqualByComparingTo(new BigDecimal("400.00"));
        assertThat(bal.getClosingBalance()).isEqualByComparingTo(new BigDecimal("800.00"));
    }

    @Test
    @DisplayName("15. No double-counting between legacy paid fallback and child invoice payments")
    void testNoDoubleCountingLegacyPaidFallback() {
        // Invoice 1: paid=true AND has child InvoicePayment
        Invoice inv1 = createTestInvoice(firmAId, customerA, InvoiceStatus.PAID, new BigDecimal("1000.00"), true);
        InvoicePayment pmt = new InvoicePayment();
        pmt.setFirmId(firmAId);
        pmt.setCustomerId(customerA.getId());
        pmt.setInvoiceId(inv1.getId());
        pmt.setAmount(new BigDecimal("1000.00"));
        pmt.setPaymentDate(LocalDate.now());
        paymentRepo.save(pmt);

        // Invoice 2: paid=true with NO child InvoicePayment
        createTestInvoice(firmAId, customerA, InvoiceStatus.PAID, new BigDecimal("500.00"), true);

        CustomerBalanceSummary bal = customerBalanceService.getCustomerBalance(firmAId, customerA.getId());
        assertThat(bal.getTotalBilled()).isEqualByComparingTo(new BigDecimal("1500.00"));
        // Total paid must be 1500 (1000 from pmt + 500 from direct paid). Invoice 1 must NOT be 2000.
        assertThat(bal.getTotalPaid()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(bal.getClosingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
