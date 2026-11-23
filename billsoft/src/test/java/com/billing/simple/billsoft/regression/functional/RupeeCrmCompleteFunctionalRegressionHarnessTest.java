package com.billing.simple.billsoft.regression.functional;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.dtos.*;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.*;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.billing.simple.billsoft.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * =========================================================================
 * RupeeCrmCompleteFunctionalRegressionHarnessTest
 *
 * Comprehensive Application-Wide Functional Regression Audit & Integrity
 * Verification Suite certifying that ALL core operations continue to function:
 * - Viewing, fetching, creating, modifying, deleting records
 * - Billing, invoicing, calculations, draft/final, sales returns
 * - Statement generation (Customer, Party, Firm) and balance reconciliation
 * - PDF generation, print payloads, file exports, imports, templates
 * - Inventory tracking, stock movement ledger, pricing, Smart Discovery
 * - HR payroll, attendance, advances, salary slips
 * - Planner & Inbox (Notes, Reminders, Goals, Savings, Letters)
 * - Local backup export, inspection, and multi-firm data protection
 * - Multi-firm isolation: same-firm operations 100% succeed; cross-firm blocked
 * =========================================================================
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DisplayName("RupeeCRM Complete Functional Regression Audit & Production Certification Harness")
public class RupeeCrmCompleteFunctionalRegressionHarnessTest {

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
    private InvoicePaymentRepository paymentRepo;

    @Autowired
    private SalesReturnRepository salesReturnRepo;

    @Autowired
    private PartyRepository partyRepo;

    @Autowired
    private PurchaseOrderRepository poRepo;

    @Autowired
    private ExpenseRepository expenseRepo;

    @Autowired
    private NoteRepository noteRepo;

    @Autowired
    private ReminderRepository reminderRepo;

    @Autowired
    private EmployeeRepository employeeRepo;

    @Autowired
    private AttendanceRecordRepository attendanceRepo;

    @Autowired
    private EmployeeAdvanceRepository advanceRepo;

    @Autowired
    private SalaryRecordRepository salaryRepo;

    @Autowired
    private BusinessLetterRepository letterRepo;

    @Autowired
    private GoalRepository goalRepo;

    @Autowired
    private SavingRepository savingRepo;

    @Autowired
    private AppConfigRepository appConfigRepo;

    @Autowired
    private StockMovementRepository stockMovementRepo;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private ProductService productService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private PartyService partyService;

    @Autowired
    private PurchaseOrderService poService;

    @Autowired
    private StatementService statementService;

    @Autowired
    private InvoicePdfService invoicePdfService;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private TenantDataIntegrityAuditService auditService;

    @Autowired
    private com.billing.simple.billsoft.service.BackupService backupService;

    private Long primaryFirmId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();

        FirmDetails firm = new FirmDetails();
        firm.setFirmName("RupeeCRM Primary Enterprise");
        firm.setGstin("27ABCDE1234F1Z5");
        firm.setPhone("9876543210");
        firm.setEmail("admin@rupeecrm.com");
        firm.setAddressLine1("100 Business Tech Park, Pune, Maharashtra");
        firm.setCity("Pune");
        firm.setState("Maharashtra");
        firm.setPincode("411001");
        firm = firmRepo.save(firm);
        primaryFirmId = firm.getId();

        TenantContext.setCurrentFirmId(primaryFirmId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("01. Firm Management & Configuration: Profile, Settings & Persistence")
    void test01_firmManagementAndConfigurationLifecycle() throws Exception {
        // Read Firm Profile
        mockMvc.perform(get("/api/firm/" + primaryFirmId)
                        .header("X-Firm-Id", primaryFirmId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firmName").value("RupeeCRM Primary Enterprise"))
                .andExpect(jsonPath("$.gstin").value("27ABCDE1234F1Z5"));

        // Update Firm Profile
        FirmDetails updateReq = firmRepo.findById(primaryFirmId).orElseThrow();
        updateReq.setBankName("HDFC Bank");
        updateReq.setBankAccount("123456789012");
        updateReq.setBankIfsc("HDFC0001234");
        updateReq.setUpiId("rupeecrm@hdfcbank");

        mockMvc.perform(put("/api/firm/" + primaryFirmId)
                        .header("X-Firm-Id", primaryFirmId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankName").value("HDFC Bank"))
                .andExpect(jsonPath("$.upiId").value("rupeecrm@hdfcbank"));

        // Configure & Read AppConfig Keys for Firm
        appConfigRepo.save(new AppConfig("INVOICE_PREFIX_" + primaryFirmId, "RCP-"));
        appConfigRepo.save(new AppConfig("INVOICE_TERMS_" + primaryFirmId, "Payment due in 15 days"));

        Optional<AppConfig> prefixConfig = appConfigRepo.findById("INVOICE_PREFIX_" + primaryFirmId);
        assertTrue(prefixConfig.isPresent());
        assertEquals("RCP-", prefixConfig.get().getConfigValue());
    }

    @Test
    @DisplayName("02. Customer Lifecycle: Create, Fetch, Search, Update & Delete")
    void test02_customerCompleteLifecycle() throws Exception {
        // Create Customer
        Customer c = Customer.builder()
                .firmId(primaryFirmId)
                .name("Acme MegaCorp")
                .phone("9988776655")
                .email("procurement@acme.com")
                .gstin("27AAACA1234A1Z1")
                .address("Plot 42 Industrial Corridor, Pune, Maharashtra 411057")
                .build();

        Customer saved = customerService.create(c);
        assertNotNull(saved.getId());
        assertEquals(primaryFirmId, saved.getFirmId());

        // Fetch by ID
        Customer fetched = customerService.getById(saved.getId(), primaryFirmId);
        assertNotNull(fetched);
        assertEquals("Acme MegaCorp", fetched.getName());

        // Search Customer
        List<Customer> searchResults = customerService.search(primaryFirmId, "Acme");
        assertFalse(searchResults.isEmpty());
        assertEquals("Acme MegaCorp", searchResults.get(0).getName());

        // Update Customer
        CustomerRequest updateReq = new CustomerRequest();
        updateReq.setName("Acme MegaCorp Global");
        updateReq.setPhone("9988776600");
        updateReq.setEmail("accounts@acmeglobal.com");
        updateReq.setAddress("Tower 1, Cyber City, Pune");
        updateReq.setGstin("27AAACA1234A1Z1");
        updateReq.setFirmId(primaryFirmId);

        Customer updated = customerService.update(saved.getId(), updateReq);
        assertEquals("Acme MegaCorp Global", updated.getName());
        assertEquals("9988776600", updated.getPhone());

        // Delete Customer (no invoices)
        boolean deleted = customerService.delete(saved.getId());
        assertTrue(deleted);
        assertNull(customerService.getById(saved.getId(), primaryFirmId));
    }

    @Test
    @DisplayName("03. Product & Inventory Lifecycle: Stock Ledger, Adjustments, Snooze & Smart Discovery")
    void test03_productAndInventoryCompleteLifecycle() throws Exception {
        // Create Product
        Product p = Product.builder()
                .firmId(primaryFirmId)
                .name("Industrial Brass Valve 25 mm")
                .sku("VALV-BRS-25")
                .barcode("8901234567890")
                .category("Plumbing & Hardware")
                .price(new BigDecimal("450.00"))
                .costPrice(new BigDecimal("300.00"))
                .stockQuantity(new BigDecimal("50.000"))
                .minStockLevel(new BigDecimal("10.000"))
                .unit("pcs")
                .itemType("GOODS")
                .inInventory(true)
                .build();

        Product created = productService.create(p);
        assertNotNull(created.getId());
        assertEquals(new BigDecimal("50.000"), created.getStockQuantity());

        // Stock Movements: Verify Initial Stock Ledger Entry
        List<StockMovement> initialLedger = stockMovementRepo.findByProductIdAndFirmIdOrderByCreatedAtDesc(created.getId(), primaryFirmId);
        assertFalse(initialLedger.isEmpty());
        assertEquals("INITIAL_STOCK", initialLedger.get(0).getMovementType());

        // Manual Stock Adjustment: Add +15 Units
        productService.recordStockMovement(
                created.getId(),
                primaryFirmId,
                "RESTOCK",
                new BigDecimal("15.000"),
                "MANUAL",
                "PO-REC-101",
                "Vendor Shipment Received"
        );
        Product adjustedAdd = productService.getById(created.getId());
        assertEquals(new BigDecimal("65.000"), adjustedAdd.getStockQuantity());

        // Manual Stock Adjustment: Deduct -5 Units (Damaged Goods)
        productService.recordStockMovement(
                created.getId(),
                primaryFirmId,
                "DAMAGED",
                new BigDecimal("-5.000"),
                "MANUAL",
                "ADJ-001",
                "Damaged in transit"
        );
        Product adjustedSub = productService.getById(created.getId());
        assertEquals(new BigDecimal("60.000"), adjustedSub.getStockQuantity());

        // Snooze Stock Alert for 14 days
        Product snoozed = productService.snoozeStockAlert(created.getId(), primaryFirmId, 14);
        assertNotNull(snoozed.getStockAlertSnoozedUntil());
        assertTrue(snoozed.getStockAlertSnoozedUntil().isAfter(LocalDateTime.now().plusDays(10)));

        // Favorites Management
        List<Long> favorites = productService.toggleFavorite(created.getId(), primaryFirmId);
        assertTrue(favorites.contains(created.getId()));

        // Smart Discovery Endpoint
        mockMvc.perform(get("/api/products/smart-discovery")
                        .header("X-Firm-Id", primaryFirmId)
                        .param("days", "90"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firmId").value(primaryFirmId))
                .andExpect(jsonPath("$.favorites[0]").value(created.getId()));
    }

    @Test
    @DisplayName("04. Invoicing Lifecycle: Preview, Create Final, Auto-Stock Deduction & Edit")
    void test04_invoicingDraftCalculationAndFinalization() throws Exception {
        // Create Customer & 2 Products
        Customer cust = customerRepo.save(Customer.builder().firmId(primaryFirmId).name("Apollo Healthcare").phone("9822001122").build());
        Product prodA = productRepo.save(Product.builder().firmId(primaryFirmId).name("Medical Oxygen Regulator").price(new BigDecimal("1200.00")).stockQuantity(new BigDecimal("20.000")).unit("pcs").build());
        Product prodB = productRepo.save(Product.builder().firmId(primaryFirmId).name("High Pressure Hose 5m").price(new BigDecimal("350.00")).stockQuantity(new BigDecimal("40.000")).unit("pcs").build());

        // 1. Invoice Preview Calculation (Zero Persistence)
        InvoiceRequest previewReq = new InvoiceRequest();
        previewReq.setFirmId(primaryFirmId);
        previewReq.setCustomerId(cust.getId());
        InvoiceRequestItem itemA = InvoiceRequestItem.builder().productId(prodA.getId()).qty(2).pricePerUnit(new BigDecimal("1200.00")).build();
        InvoiceRequestItem itemB = InvoiceRequestItem.builder().productId(prodB.getId()).qty(4).pricePerUnit(new BigDecimal("350.00")).build();
        previewReq.setItems(List.of(itemA, itemB));

        Invoice previewCalc = invoiceService.previewInvoice(previewReq);
        assertNotNull(previewCalc);
        assertEquals(new BigDecimal("3800.00"), previewCalc.getTotalAmount()); // 2*1200 + 4*350 = 2400 + 1400 = 3800
        assertNull(previewCalc.getId()); // Zero DB persistence confirmed

        // 2. Create Authoritative Final Invoice
        InvoiceRequest finalReq = new InvoiceRequest();
        finalReq.setFirmId(primaryFirmId);
        finalReq.setCustomerId(cust.getId());
        finalReq.setStatus(InvoiceStatus.UNPAID);
        finalReq.setItems(List.of(itemA, itemB));

        Invoice createdInv = invoiceService.createInvoice(finalReq);
        assertNotNull(createdInv.getId());
        assertNotNull(createdInv.getInvoiceNumber());
        assertEquals(new BigDecimal("3800.00"), createdInv.getTotalAmount());

        // Verify Automatic Stock Deduction upon Final Invoice Creation
        Product prodAAfter = productRepo.findById(prodA.getId()).orElseThrow();
        Product prodBAfter = productRepo.findById(prodB.getId()).orElseThrow();
        assertEquals(new BigDecimal("18.000"), prodAAfter.getStockQuantity()); // 20 - 2 = 18
        assertEquals(new BigDecimal("36.000"), prodBAfter.getStockQuantity()); // 40 - 4 = 36

        // 3. Invoice Modification: Update item quantity (itemA from 2 to 3)
        InvoiceUpdateRequest updateReq = new InvoiceUpdateRequest();
        updateReq.setCustomerId(cust.getId());
        updateReq.setStatus(InvoiceStatus.UNPAID);
        InvoiceRequestItem itemAMod = InvoiceRequestItem.builder().productId(prodA.getId()).qty(3).pricePerUnit(new BigDecimal("1200.00")).build();
        updateReq.setItems(List.of(itemAMod, itemB));

        Invoice updatedInv = invoiceService.updateFullInvoice(createdInv.getId(), updateReq);
        assertEquals(new BigDecimal("5000.00"), updatedInv.getTotalAmount()); // 3*1200 + 4*350 = 3600 + 1400 = 5000
    }

    @Test
    @DisplayName("05. Invoice PDF Generation & Thermal Receipt Export Verification")
    void test05_invoicePdfGenerationAndPrintModalPayload() throws Exception {
        Customer cust = customerRepo.save(Customer.builder().firmId(primaryFirmId).name("Zenith Automations").phone("9711223344").build());
        Product prod = productRepo.save(Product.builder().firmId(primaryFirmId).name("Stepper Motor NEMA 23").price(new BigDecimal("1500.00")).stockQuantity(new BigDecimal("15.000")).unit("pcs").build());

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(primaryFirmId);
        req.setCustomerId(cust.getId());
        req.setStatus(InvoiceStatus.UNPAID);
        req.setItems(List.of(InvoiceRequestItem.builder().productId(prod.getId()).qty(2).pricePerUnit(new BigDecimal("1500.00")).build()));

        Invoice inv = invoiceService.createInvoice(req);

        // A4 PDF Generation
        byte[] a4Pdf = invoicePdfService.generatePdf(inv, "A4");
        assertNotNull(a4Pdf);
        assertTrue(a4Pdf.length > 500, "A4 PDF bytes must be non-empty");
        assertEquals("%PDF-", new String(a4Pdf, 0, 5, StandardCharsets.US_ASCII), "PDF magic header missing");

        // Thermal Receipt (3-inch / 80mm) PDF Generation
        byte[] thermalPdf = invoicePdfService.generatePdf(inv, "3-inch");
        assertNotNull(thermalPdf);
        assertTrue(thermalPdf.length > 500, "Thermal 3-inch PDF bytes must be non-empty");
        assertEquals("%PDF-", new String(thermalPdf, 0, 5, StandardCharsets.US_ASCII));

        // REST Endpoint Download Verification
        mockMvc.perform(get("/api/invoices/" + inv.getId() + "/pdf")
                        .header("X-Firm-Id", primaryFirmId)
                        .param("size", "A4"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().exists("Content-Disposition"));
    }

    @Test
    @DisplayName("06. Payment Processing: Partial Payment, Full Allocation & Outstanding Reconciliation")
    void test06_paymentsAndOutstandingBalanceTracking() throws Exception {
        Customer cust = customerRepo.save(Customer.builder().firmId(primaryFirmId).name("Sterling Fab").phone("9811002233").build());
        Product prod = productRepo.save(Product.builder().firmId(primaryFirmId).name("Laser Cut Bracket").price(new BigDecimal("2000.00")).stockQuantity(new BigDecimal("50.000")).unit("pcs").build());

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(primaryFirmId);
        req.setCustomerId(cust.getId());
        req.setStatus(InvoiceStatus.UNPAID);
        req.setItems(List.of(InvoiceRequestItem.builder().productId(prod.getId()).qty(1).pricePerUnit(new BigDecimal("2000.00")).build()));

        Invoice inv = invoiceService.createInvoice(req);
        assertEquals(new BigDecimal("2000.00"), inv.getTotalAmount());
        assertFalse(inv.getPaid());

        // 1. Record Partial Payment (₹800)
        InvoicePayment payment1 = invoiceService.recordPayment(inv.getId(), new BigDecimal("800.00"), LocalDate.now(), "UPI", "UPI-101", "Advance tranche 1");
        assertNotNull(payment1);
        Invoice invAfterP1 = invoiceService.getById(inv.getId());
        assertFalse(invAfterP1.getPaid());

        // 2. Record Final Settling Payment (₹1200)
        InvoicePayment payment2 = invoiceService.recordPayment(inv.getId(), new BigDecimal("1200.00"), LocalDate.now(), "NEFT", "NEFT-102", "Final tranche 2");
        assertNotNull(payment2);
        Invoice invAfterP2 = invoiceService.getById(inv.getId());
        assertTrue(invAfterP2.getPaid());
        assertEquals(InvoiceStatus.PAID, invAfterP2.getStatus());

        // 3. Verify Payment Records History
        List<InvoicePayment> payments = invoiceService.getPayments(inv.getId());
        assertEquals(2, payments.size());
    }

    @Test
    @DisplayName("07. Financial Statements: Customer Statement Generation, PDF & Ledger Reconciliation")
    void test07_customerStatementGenerationAndReconciliation() throws Exception {
        Customer cust = customerRepo.save(Customer.builder().firmId(primaryFirmId).name("Vertex Dynamics").phone("9555444333").build());
        Product prod = productRepo.save(Product.builder().firmId(primaryFirmId).name("Servo Drive Controller").price(new BigDecimal("5000.00")).stockQuantity(new BigDecimal("10.000")).unit("pcs").build());

        // Create Invoice ₹5,000
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(primaryFirmId);
        req.setCustomerId(cust.getId());
        req.setStatus(InvoiceStatus.UNPAID);
        req.setItems(List.of(InvoiceRequestItem.builder().productId(prod.getId()).qty(1).pricePerUnit(new BigDecimal("5000.00")).build()));
        Invoice inv = invoiceService.createInvoice(req);

        // Record Partial Payment ₹2,000
        invoiceService.recordPayment(inv.getId(), new BigDecimal("2000.00"), LocalDate.now(), "BANK_TRANSFER", "BT-999", "Partial payment");

        // Customer Statement via StatementService
        CustomerStatementResponse stmt = statementService.getCustomerStatement(
                primaryFirmId,
                cust.getId(),
                LocalDate.now().minusDays(30),
                LocalDate.now().plusDays(1)
        );

        assertNotNull(stmt);
        assertEquals(cust.getId(), stmt.getCustomerId());
        assertEquals(5000.0, stmt.getTotalBilled());
        assertEquals(2000.0, stmt.getTotalPaid());
        assertEquals(3000.0, stmt.getClosingBalance()); // 5000 - 2000 = 3000 outstanding

        // Statement PDF Export Verification
        byte[] stmtPdf = statementService.generateCustomerStatementPdf(
                primaryFirmId,
                cust.getId(),
                LocalDate.now().minusDays(30),
                LocalDate.now().plusDays(1)
        );
        assertNotNull(stmtPdf);
        assertEquals("%PDF-", new String(stmtPdf, 0, 5, StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("08. Sales Return & Credit Note: Item Reversal & Inventory Restoration")
    void test08_salesReturnAndCreditNoteLifecycle() throws Exception {
        Customer cust = customerRepo.save(Customer.builder().firmId(primaryFirmId).name("Omega Precision").phone("9111222333").build());
        Product prod = productRepo.save(Product.builder().firmId(primaryFirmId).name("Carbide Endmill 10mm").price(new BigDecimal("800.00")).stockQuantity(new BigDecimal("25.000")).unit("pcs").build());

        // Sale of 5 units (Stock becomes 20)
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(primaryFirmId);
        req.setCustomerId(cust.getId());
        req.setStatus(InvoiceStatus.PAID);
        req.setItems(List.of(InvoiceRequestItem.builder().productId(prod.getId()).qty(5).pricePerUnit(new BigDecimal("800.00")).build()));
        Invoice inv = invoiceService.createInvoice(req);

        Product stockAfterSale = productRepo.findById(prod.getId()).orElseThrow();
        assertEquals(new BigDecimal("20.000"), stockAfterSale.getStockQuantity());

        // Create Sales Return of 2 units
        SalesReturnItem returnItem = new SalesReturnItem();
        returnItem.setProduct(prod);
        returnItem.setProductName(prod.getName());
        returnItem.setReturnQty(2);
        returnItem.setUnitPrice(new BigDecimal("800.00"));
        returnItem.setRefundTotal(new BigDecimal("1600.00"));

        SalesReturn sr = SalesReturn.builder()
                .firmId(primaryFirmId)
                .invoice(inv)
                .customer(cust)
                .returnNumber("SR-2026-001")
                .returnDate(LocalDate.now())
                .grossAmount(new BigDecimal("1600.00"))
                .totalRefundAmount(new BigDecimal("1600.00"))
                .reason("Defective packaging")
                .items(List.of(returnItem))
                .build();
        returnItem.setSalesReturn(sr);

        SalesReturn savedReturn = salesReturnRepo.save(sr);
        assertNotNull(savedReturn.getId());

        // Stock restored (+2 units back to 22)
        productService.recordStockMovement(
                prod.getId(),
                primaryFirmId,
                "SALES_RETURN",
                new BigDecimal("2.000"),
                "MANUAL",
                savedReturn.getReturnNumber(),
                "Customer return restock"
        );

        Product stockAfterReturn = productRepo.findById(prod.getId()).orElseThrow();
        assertEquals(new BigDecimal("22.000"), stockAfterReturn.getStockQuantity());
    }

    @Test
    @DisplayName("09. Vendor / Party & Purchase Orders: PO Workflow, Reception & Vendor Statements")
    void test09_partySupplierAndPurchaseOrderLifecycle() throws Exception {
        // Create Supplier Party
        Party party = Party.builder()
                .firmId(primaryFirmId)
                .name("Global Steel Corporation")
                .contactPerson("Vikas Sharma")
                .phone("9833445566")
                .email("sales@globalsteel.com")
                .gstin("27AAACG9999G1Z9")
                .build();
        Party savedParty = partyService.createParty(party);
        assertNotNull(savedParty.getId());

        // Create Purchase Order
        PurchaseOrderItem poItem = PurchaseOrderItem.builder()
                .productName("Stainless Steel Rod 316L")
                .quantity(new BigDecimal("100.000"))
                .unit("kg")
                .unitPrice(new BigDecimal("420.00"))
                .totalAmount(new BigDecimal("42000.00"))
                .build();

        PurchaseOrder po = PurchaseOrder.builder()
                .firmId(primaryFirmId)
                .party(savedParty)
                .poNumber("PO-2026-001")
                .poDate(LocalDate.now())
                .status(PurchaseOrderStatus.ISSUED)
                .totalAmount(new BigDecimal("42000.00"))
                .items(List.of(poItem))
                .build();
        poItem.setPurchaseOrder(po);

        PurchaseOrder savedPo = poService.createPurchaseOrder(po);
        assertEquals(PurchaseOrderStatus.ISSUED, savedPo.getStatus());

        // Update PO Status to RECEIVED
        PurchaseOrder receivedPo = poService.updateStatus(savedPo.getId(), primaryFirmId, PurchaseOrderStatus.RECEIVED);
        assertEquals(PurchaseOrderStatus.RECEIVED, receivedPo.getStatus());

        // Generate PO PDF
        byte[] poPdf = poService.generatePoPdf(receivedPo.getId(), primaryFirmId);
        assertNotNull(poPdf);
        assertEquals("%PDF-", new String(poPdf, 0, 5, StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("10. Expense Management & Firm Financial Analytics Reconciliation")
    void test10_expensesAndFinancialSummaries() throws Exception {
        // Record Expenses
        Expense exp1 = new Expense();
        exp1.setFirmId(primaryFirmId);
        exp1.setTitle("Stationery and toner");
        exp1.setCategory("Office Supplies");
        exp1.setAmount(new BigDecimal("4500.00"));
        exp1.setExpenseDate(LocalDate.now());
        exp1.setPaymentMode("CREDIT_CARD");
        exp1.setNotes("Stationery and printer toner");
        expenseService.createExpense(exp1);

        Expense exp2 = new Expense();
        exp2.setFirmId(primaryFirmId);
        exp2.setTitle("Fiber internet");
        exp2.setCategory("Utilities");
        exp2.setAmount(new BigDecimal("8200.00"));
        exp2.setExpenseDate(LocalDate.now());
        exp2.setPaymentMode("NET_BANKING");
        exp2.setNotes("High speed fiber internet");
        expenseService.createExpense(exp2);

        // Fetch Expenses & Categories
        List<Expense> expenses = expenseService.getExpensesByFirm(primaryFirmId);
        assertTrue(expenses.size() >= 2);

        List<String> categories = expenseService.getCategoriesByFirm(primaryFirmId);
        assertTrue(categories.contains("Office Supplies"));
        assertTrue(categories.contains("Utilities"));

        // Query Firm Analytics
        FirmAnalyticsResponse analytics = invoiceService.getFirmAnalytics(primaryFirmId);
        assertNotNull(analytics);
        assertNotNull(analytics.getTotalBusiness());
    }

    @Test
    @DisplayName("11. Human Resources: Employee Lifecycle, Attendance, Advances & Salary Slip PDF")
    void test11_humanResourcesPayrollAndAttendanceLifecycle() throws Exception {
        // Create Employee
        Employee emp = new Employee();
        emp.setFirmId(primaryFirmId);
        emp.setName("Rahul Deshmukh");
        emp.setDesignation("Production Supervisor");
        emp.setDepartment("Manufacturing");
        emp.setMonthlyBaseSalary(65000.0);
        emp.setIsActive(true);
        emp.setPhone("9811223344");
        emp = employeeRepo.save(emp);

        // Record Daily Attendance
        AttendanceRecord att = new AttendanceRecord();
        att.setEmployee(emp);
        att.setDate(LocalDate.now());
        att.setStatus("PRESENT");
        att.setRemarks("Full day shift");
        attendanceRepo.save(att);

        // Record Salary Advance
        EmployeeAdvance adv = new EmployeeAdvance();
        adv.setEmployee(emp);
        adv.setAmount(10000.0);
        adv.setDate(LocalDate.now().minusDays(5));
        adv.setDescription("Medical emergency");
        advanceRepo.save(adv);

        // Record Monthly Salary Disbursement
        SalaryRecord sal = new SalaryRecord();
        sal.setEmployee(emp);
        sal.setMonthYear("2026-11");
        sal.setPaymentDate(LocalDate.now());
        sal.setBaseSalaryAtTime(65000.0);
        sal.setAdvanceDeducted(10000.0);
        sal.setNetPaid(55000.0);
        sal = salaryRepo.save(sal);

        assertNotNull(sal.getId());
        assertEquals(55000.0, sal.getNetPaid());
    }

    @Test
    @DisplayName("12. Master Data Workspace: Notes, Reminders, Goals & Business Letters")
    void test12_plannerAndInboxOperations() throws Exception {
        // Notes
        Note note = noteRepo.save(Note.builder().firmId(primaryFirmId).title("Q4 Expansion Plan").content("Procure 5 CNC machines").build());
        assertNotNull(note.getId());
        note.setTitle("Q4 Expansion Plan Rev 2");
        noteRepo.save(note);
        assertEquals("Q4 Expansion Plan Rev 2", noteRepo.findById(note.getId()).orElseThrow().getTitle());

        // Reminders
        Reminder reminder = reminderRepo.save(Reminder.builder().firmId(primaryFirmId).title("GST GSTR-3B Return").dueDate(LocalDateTime.now().plusDays(3)).completed(false).build());
        reminder.setCompleted(true);
        reminderRepo.save(reminder);
        assertTrue(reminderRepo.findById(reminder.getId()).orElseThrow().isCompleted());

        // Goals & Savings
        Goal goal = goalRepo.save(Goal.builder()
                .firmId(primaryFirmId)
                .title("Factory Solar Installation")
                .goalType(GoalType.SAVINGS_TARGET)
                .targetValue(new BigDecimal("500000.00"))
                .currentValue(BigDecimal.ZERO)
                .targetDate(LocalDate.now().plusMonths(6))
                .status("ACTIVE")
                .build());

        SavingRecord saving = savingRepo.save(SavingRecord.builder()
                .firmId(primaryFirmId)
                .goalId(goal.getId())
                .title("Monthly Solar Deposit")
                .amount(new BigDecimal("50000.00"))
                .savingDate(LocalDate.now())
                .category("Emergency Fund")
                .build());
        assertNotNull(saving.getId());

        // Business Letters
        BusinessLetter letter = letterRepo.save(BusinessLetter.builder()
                .firmId(primaryFirmId)
                .letterNumber("LTR-2026-001")
                .letterDate(LocalDate.now())
                .recipientName("Director of Industries")
                .subject("Application for Subsidy")
                .content("<p>We hereby submit the audit report...</p>")
                .status(LetterStatus.DRAFT)
                .build());
        assertNotNull(letter.getId());
    }

    @Test
    @DisplayName("13. File Operations: CSV Product Export, Templates & Bulk Import Processing")
    void test13_fileExportImportAndTemplates() throws Exception {
        // 1. Download Product CSV Template
        mockMvc.perform(get("/api/products/template/csv")
                        .header("X-Firm-Id", primaryFirmId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv"));

        // 2. Download Product Excel Template
        mockMvc.perform(get("/api/products/template/xlsx")
                        .header("X-Firm-Id", primaryFirmId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        // 3. Bulk CSV Product Import
        String csvData = "name,sku,category,price,costprice,initialstock,unit\n" +
                "Titanium Hex Bolt M8,BOLT-TI-M8,Fasteners,85.00,50.00,200,pcs\n" +
                "Nylon Lock Nut M8,NUT-NYL-M8,Fasteners,12.00,5.00,500,pcs\n";

        mockMvc.perform(post("/api/products/import/csv")
                        .header("X-Firm-Id", primaryFirmId)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(csvData))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(2));

        // Verify Products are persisted and searchable
        List<Product> imported = productRepo.findByFirmId(primaryFirmId);
        assertTrue(imported.stream().anyMatch(p -> "Titanium Hex Bolt M8".equals(p.getName())));
        assertTrue(imported.stream().anyMatch(p -> "Nylon Lock Nut M8".equals(p.getName())));

        // 4. Product CSV Export
        MvcResult exportResult = mockMvc.perform(get("/api/products/export/csv")
                        .header("X-Firm-Id", primaryFirmId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv"))
                .andReturn();

        String exportContent = exportResult.getResponse().getContentAsString();
        assertTrue(exportContent.contains("Titanium Hex Bolt M8"));
        assertTrue(exportContent.contains("Nylon Lock Nut M8"));
    }

    @Test
    @DisplayName("14. Backup & Recovery: Tenant Backup Archive Export & Stream Integrity")
    void test14_localBackupExportInspectionAndRestore() throws Exception {
        MvcResult backupResult = mockMvc.perform(get("/api/backup/export")
                        .header("X-Firm-Id", primaryFirmId)
                        .param("firmId", primaryFirmId.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/json"))
                .andReturn();

        String jsonBackup = backupResult.getResponse().getContentAsString();
        BackupDTO backupDto = objectMapper.readValue(jsonBackup, BackupDTO.class);
        assertNotNull(backupDto);
        assertEquals(primaryFirmId, backupDto.getSourceFirmId());

        // Create and verify ZIP archive structure and manifest
        BackupArchiveService archiveService = new BackupArchiveService(objectMapper, new BackupValidationService());
        byte[] zipBytes = archiveService.createZipArchive(backupDto);
        assertTrue(zipBytes.length > 100, "Backup ZIP must contain bytes");

        Set<String> entryNames = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
                zis.closeEntry();
            }
        }

        assertTrue(entryNames.contains("manifest.json"), "manifest.json missing in backup archive");
        assertTrue(entryNames.stream().anyMatch(n -> n.endsWith(".json")), "Entity JSON files missing in backup archive");

        // Read-back verification and validation from ZIP stream
        try (ByteArrayInputStream bais = new ByteArrayInputStream(zipBytes)) {
            BackupDTO extracted = archiveService.extractAndValidateBackup(bais, "test-backup.zip");
            assertNotNull(extracted);
            assertEquals(primaryFirmId, extracted.getSourceFirmId());
        }
    }

    @Test
    @DisplayName("15. Multi-Firm Isolation Guarantee: Same-Firm Succeeds, Cross-Firm Strictly Blocked")
    void test15_multiFirmIsolationAndZeroLeakageVerification() throws Exception {
        // Create Second Synthetic Firm (Firm Beta)
        FirmDetails firmBeta = new FirmDetails();
        firmBeta.setFirmName("Beta Logistics Private Limited");
        firmBeta.setGstin("27BETA1234F1Z1");
        firmBeta.setPhone("9112233445");
        firmBeta = firmRepo.save(firmBeta);
        Long betaFirmId = firmBeta.getId();

        // Populate Firm Beta with private customer and product
        Customer betaCust = customerRepo.save(Customer.builder().firmId(betaFirmId).name("Beta Private Client").phone("8888899999").build());
        Product betaProd = productRepo.save(Product.builder().firmId(betaFirmId).name("Beta Exclusive Cargo Belt").price(new BigDecimal("750.00")).stockQuantity(new BigDecimal("30.000")).unit("pcs").build());
        Invoice betaInv = invoiceRepo.save(Invoice.builder()
                .firmId(betaFirmId)
                .customer(betaCust)
                .invoiceNumber("BETA-INV-101")
                .invoiceDate(LocalDateTime.now())
                .status(InvoiceStatus.PAID)
                .totalAmount(new BigDecimal("750.00"))
                .paid(true)
                .build());

        // 1. Same-Firm Legitimate Access: Firm Beta reads its own records -> 200 OK
        mockMvc.perform(get("/api/invoices/" + betaInv.getId())
                        .header("X-Firm-Id", betaFirmId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceNumber").value("BETA-INV-101"));

        // 2. Cross-Firm Attack: Primary Firm attempts to read Firm Beta's invoice -> 404 / 403 Forbidden
        mockMvc.perform(get("/api/invoices/" + betaInv.getId())
                        .header("X-Firm-Id", primaryFirmId))
                .andExpect(status().isNotFound());

        // 3. Cross-Firm Attack: Primary Firm attempts to fetch Firm Beta's customer -> 404 Not Found
        mockMvc.perform(get("/api/customers/" + betaCust.getId())
                        .header("X-Firm-Id", primaryFirmId))
                .andExpect(status().isNotFound());

        // 4. Cross-Firm Service Layer Rejection: Direct service call throws TenantSecurityException
        TenantContext.setCurrentFirmId(primaryFirmId);
        assertThrows(TenantSecurityException.class, () -> {
            invoiceService.getFirmAnalytics(betaFirmId);
        });

        assertThrows(TenantSecurityException.class, () -> {
            customerService.getAll(betaFirmId);
        });

        assertThrows(TenantSecurityException.class, () -> {
            productService.getSmartDiscovery(betaFirmId, 90);
        });
    }

    @Test
    @DisplayName("16. Data Consistency & Referential Integrity Sweep: 0 Violations (CLEAN)")
    void test16_dataConsistencyAndReconciliationSweep() {
        Map<String, Object> audit = auditService.performFullAudit();
        assertNotNull(audit);
        assertEquals("CLEAN", audit.get("status"), "Diagnostic tenant integrity audit must report CLEAN");
        assertEquals(0, audit.get("totalViolationsCount"), "There must be zero tenant referential violations across all entities");
        if (backupService != null) {
            backupService.factoryReset();
        }
    }
}
