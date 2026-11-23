package com.billing.simple.billsoft.regression.demo;

import com.billing.simple.billsoft.BillsoftApplication;
import com.billing.simple.billsoft.controllers.DemoModeController;
import com.billing.simple.billsoft.demo.DemoModeService;
import com.billing.simple.billsoft.demo.EnvironmentMode;
import com.billing.simple.billsoft.demo.EnvironmentModeContext;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.*;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.BackupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = BillsoftApplication.class)
@ActiveProfiles("test")
public class RupeeCrmCompleteDemoModeLifecycleTest {

    @Autowired
    private DemoModeService demoModeService;

    @Autowired
    private DemoModeController demoModeController;

    @Autowired
    private BackupService backupService;

    @Autowired
    private FirmDetailsRepository firmDetailsRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private InvoicePaymentRepository invoicePaymentRepo;

    @Autowired
    private PartyRepository partyRepo;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepo;

    private Long realFirm1Id;
    private Long realFirm2Id;
    private Long realFirm3Id;

    @BeforeEach
    void setUpRealEnvironment() {
        // Ensure clean NORMAL mode
        EnvironmentModeContext.setGlobalMode(EnvironmentMode.NORMAL);
        TenantContext.clear();

        // Populate 3 real businesses in the normal database
        firmDetailsRepo.deleteAll();

        FirmDetails f1 = new FirmDetails();
        f1.setFirmName("Real Enterprise Alpha");
        f1.setOwnerName("Alpha Owner");
        f1.setGstin("24AAACA1111A1Z1");
        f1.setCity("Ahmedabad");
        f1.setState("Gujarat");
        f1.setPhone("9800000001");
        f1 = firmDetailsRepo.save(f1);
        realFirm1Id = f1.getId();

        FirmDetails f2 = new FirmDetails();
        f2.setFirmName("Real Enterprise Beta");
        f2.setOwnerName("Beta Owner");
        f2.setGstin("24AAACB2222B1Z2");
        f2.setCity("Surat");
        f2.setState("Gujarat");
        f2.setPhone("9800000002");
        f2 = firmDetailsRepo.save(f2);
        realFirm2Id = f2.getId();

        FirmDetails f3 = new FirmDetails();
        f3.setFirmName("Real Enterprise Gamma");
        f3.setOwnerName("Gamma Owner");
        f3.setGstin("24AAACG3333C1Z3");
        f3.setCity("Vadodara");
        f3.setState("Gujarat");
        f3.setPhone("9800000003");
        f3 = firmDetailsRepo.save(f3);
        realFirm3Id = f3.getId();

        // Create initial real customer & product for Alpha
        Customer realCust = new Customer();
        realCust.setFirmId(realFirm1Id);
        realCust.setName("Original Real Customer");
        realCust.setPhone("9999900001");
        realCust.setAddress("Real Address 1");
        customerRepo.save(realCust);

        Product realProd = new Product();
        realProd.setFirmId(realFirm1Id);
        realProd.setName("Original Real Product");
        realProd.setPrice(new java.math.BigDecimal("1000.00"));
        realProd.setCostPrice(new java.math.BigDecimal("700.00"));
        realProd.setGstPercentage(new java.math.BigDecimal("18.00"));
        realProd.setStockQuantity(new java.math.BigDecimal("50.000"));
        productRepo.save(realProd);

        TenantContext.setCurrentFirmId(realFirm1Id);
    }

    @AfterEach
    void tearDownCleanEnvironment() {
        EnvironmentModeContext.setGlobalMode(EnvironmentMode.NORMAL);
        TenantContext.clear();
    }

    @Test
    @DisplayName("1. Verification of PIN 0000 and Rejection of Unauthorized Access")
    void testPinVerificationAndAccessControl() {
        assertTrue(demoModeService.verifyPin("0000"));
        assertTrue(demoModeService.verifyPin(" 0000 "));
        assertFalse(demoModeService.verifyPin("1234"));
        assertFalse(demoModeService.verifyPin(""));
        assertFalse(demoModeService.verifyPin(null));

        // Controller endpoint with invalid PIN should return 401
        Map<String, String> badBody = Collections.singletonMap("pin", "9999");
        ResponseEntity<Map<String, Object>> badResp = demoModeController.enterDemoMode(badBody);
        assertEquals(401, badResp.getStatusCode().value());
        assertFalse((Boolean) badResp.getBody().get("success"));
        assertTrue(badResp.getBody().get("error").toString().contains("Invalid Demo PIN"));
    }

    @Test
    @DisplayName("2. Complete Acceptance Scenario: Real Data Preservation, Demo CRUD, Multi-Firm Isolation, Clean Exit & Deterministic Reset")
    void testCompleteDemoModeAcceptanceLifecycle() {
        // Record baseline state of real database
        assertEquals(3, firmDetailsRepo.count(), "Must have 3 real firms in normal database");
        long baselineRealCustCount = customerRepo.count();
        long baselineRealProdCount = productRepo.count();
        assertTrue(baselineRealCustCount >= 1);
        assertTrue(baselineRealProdCount >= 1);

        // --- STEP A: ENTER DEMO MODE WITH PIN 0000 ---
        Map<String, String> enterBody = Collections.singletonMap("pin", "0000");
        ResponseEntity<Map<String, Object>> enterResp = demoModeController.enterDemoMode(enterBody);
        assertEquals(200, enterResp.getStatusCode().value());
        Map<String, Object> enterData = enterResp.getBody();
        assertNotNull(enterData);
        assertTrue((Boolean) enterData.get("success"));
        assertEquals("DEMO", enterData.get("mode"));
        assertTrue(EnvironmentModeContext.isDemoMode());

        // Verify status endpoint reflects DEMO mode
        ResponseEntity<Map<String, Object>> statusResp = demoModeController.getStatus();
        assertTrue((Boolean) statusResp.getBody().get("isDemoMode"));
        assertEquals("DEMO", statusResp.getBody().get("activeMode"));

        // --- STEP B: VERIFY PREDEFINED 3 DEMO FIRMS AND ENTITIES ---
        List<FirmDetails> demoFirms = firmDetailsRepo.findAll();
        assertEquals(3, demoFirms.size(), "Demo database must contain exactly 3 predefined fictional firms");

        FirmDetails demoFirm1 = null;
        FirmDetails demoFirm2 = null;
        FirmDetails demoFirm3 = null;
        for (FirmDetails df : demoFirms) {
            if ("Apex Plumbing Solutions".equals(df.getFirmName())) demoFirm1 = df;
            if ("Bright Electrical Traders".equals(df.getFirmName())) demoFirm2 = df;
            if ("Mehta Industrial Supplies".equals(df.getFirmName())) demoFirm3 = df;
        }
        assertNotNull(demoFirm1, "Firm 1: Apex Plumbing Solutions must exist");
        assertNotNull(demoFirm2, "Firm 2: Bright Electrical Traders must exist");
        assertNotNull(demoFirm3, "Firm 3: Mehta Industrial Supplies must exist");

        // Verify demo database entity volume matches starting requirements
        long initialDemoCustomers = customerRepo.count();
        long initialDemoProducts = productRepo.count();
        long initialDemoInvoices = invoiceRepo.count();
        long initialDemoPayments = invoicePaymentRepo.count();
        long initialDemoParties = partyRepo.count();

        assertEquals(40, initialDemoCustomers, "Demo database must contain 40 customers");
        assertEquals(150, initialDemoProducts, "Demo database must contain 150 products");
        assertEquals(45, initialDemoInvoices, "Demo database must contain 45 invoices");
        assertEquals(15, initialDemoParties, "Demo database must contain 15 suppliers");

        // --- STEP C: DEMONSTRATE NATURAL APP CRUD OPERATIONS IN DEMO MODE ---
        TenantContext.setCurrentFirmId(demoFirm1.getId());

        // 1. Create a new demo customer
        Customer newDemoCust = new Customer();
        newDemoCust.setFirmId(demoFirm1.getId());
        newDemoCust.setName("Demonstration Walkthrough Plumber");
        newDemoCust.setPhone("9898001122");
        newDemoCust.setAddress("Live Demo Address");
        Customer savedDemoCust = customerRepo.save(newDemoCust);
        assertNotNull(savedDemoCust.getId());

        // 2. Add a new demo product
        Product newDemoProd = new Product();
        newDemoProd.setFirmId(demoFirm1.getId());
        newDemoProd.setName("Live Demo Ball Valve 2-inch");
        newDemoProd.setPrice(new java.math.BigDecimal("950.00"));
        newDemoProd.setCostPrice(new java.math.BigDecimal("680.00"));
        newDemoProd.setGstPercentage(new java.math.BigDecimal("18.00"));
        newDemoProd.setStockQuantity(new java.math.BigDecimal("30.000"));
        Product savedDemoProd = productRepo.save(newDemoProd);
        assertNotNull(savedDemoProd.getId());

        // 3. Create a demo invoice
        Invoice demoInv = new Invoice();
        demoInv.setFirmId(demoFirm1.getId());
        demoInv.setInvoiceNumber("APS-DEMO-LIVE-001");
        demoInv.setInvoiceDate(java.time.LocalDateTime.now());
        demoInv.setCustomer(savedDemoCust);
        demoInv.setTotalAmount(new java.math.BigDecimal("1121.00"));
        demoInv.setStatus(InvoiceStatus.PAID);
        Invoice savedDemoInv = invoiceRepo.save(demoInv);
        assertNotNull(savedDemoInv.getId());

        // 4. Record a demo payment
        InvoicePayment demoPay = new InvoicePayment();
        demoPay.setFirmId(demoFirm1.getId());
        demoPay.setInvoiceId(savedDemoInv.getId());
        demoPay.setAmount(new java.math.BigDecimal("1121.00"));
        demoPay.setPaymentDate(LocalDate.now());
        demoPay.setPaymentMode("UPI");
        invoicePaymentRepo.save(demoPay);

        // Verify count in demo mode increased by 1
        assertEquals(41, customerRepo.count());
        assertEquals(151, productRepo.count());
        assertEquals(46, invoiceRepo.count());

        // --- STEP D: MULTI-FIRM ISOLATION TEST INSIDE DEMO MODE ---
        // Switching to Firm 2 (Bright Electrical Traders)
        TenantContext.setCurrentFirmId(demoFirm2.getId());
        List<Customer> f2Customers = customerRepo.findByFirmId(demoFirm2.getId());
        assertFalse(f2Customers.isEmpty());
        // Verify Firm 1's newly created customer is not visible under Firm 2
        boolean leakedToFirm2 = f2Customers.stream().anyMatch(c -> c.getName().equals("Demonstration Walkthrough Plumber"));
        assertFalse(leakedToFirm2, "Firm 1 customer must not leak to Firm 2");

        // --- STEP E: EXIT DEMO MODE ---
        ResponseEntity<Map<String, Object>> exitResp = demoModeController.exitDemoMode();
        assertEquals(200, exitResp.getStatusCode().value());
        assertTrue((Boolean) exitResp.getBody().get("success"));
        assertEquals("NORMAL", exitResp.getBody().get("mode"));
        assertFalse(EnvironmentModeContext.isDemoMode());

        // Verify status endpoint reflects NORMAL mode
        ResponseEntity<Map<String, Object>> normalStatusResp = demoModeController.getStatus();
        assertFalse((Boolean) normalStatusResp.getBody().get("isDemoMode"));
        assertEquals("NORMAL", normalStatusResp.getBody().get("activeMode"));

        // --- STEP F: VERIFY 100% REAL DATA PRESERVATION ---
        // The normal database must have exactly its original 3 real firms
        List<FirmDetails> normalFirms = firmDetailsRepo.findAll();
        assertEquals(3, normalFirms.size(), "Normal database must have exactly 3 firms");
        assertTrue(normalFirms.stream().anyMatch(f -> "Real Enterprise Alpha".equals(f.getFirmName())));
        assertTrue(normalFirms.stream().anyMatch(f -> "Real Enterprise Beta".equals(f.getFirmName())));
        assertTrue(normalFirms.stream().anyMatch(f -> "Real Enterprise Gamma".equals(f.getFirmName())));

        // Verify zero demo records leaked into real database
        assertFalse(normalFirms.stream().anyMatch(f -> "Apex Plumbing Solutions".equals(f.getFirmName())));
        assertFalse(normalFirms.stream().anyMatch(f -> "Bright Electrical Traders".equals(f.getFirmName())));
        assertFalse(normalFirms.stream().anyMatch(f -> "Mehta Industrial Supplies".equals(f.getFirmName())));

        assertEquals(baselineRealCustCount, customerRepo.count(), "Real customer count must be completely unchanged");
        assertEquals(baselineRealProdCount, productRepo.count(), "Real product count must be completely unchanged");
        assertFalse(customerRepo.findAll().stream().anyMatch(c -> "Demonstration Walkthrough Plumber".equals(c.getName())),
                "Demo customer must NEVER exist in normal database");

        // --- STEP G: RE-ENTER DEMO MODE AND VERIFY EXACT DETERMINISTIC RESET ---
        ResponseEntity<Map<String, Object>> reEnterResp = demoModeController.enterDemoMode(enterBody);
        assertEquals(200, reEnterResp.getStatusCode().value());
        assertTrue((Boolean) reEnterResp.getBody().get("success"));
        assertTrue(EnvironmentModeContext.isDemoMode());

        // All counts must return to the EXACT initial predefined counts (40 customers, 150 products, 45 invoices)
        assertEquals(40, customerRepo.count(), "Re-entering Demo Mode must reset customer count to exactly 40");
        assertEquals(150, productRepo.count(), "Re-entering Demo Mode must reset product count to exactly 150");
        assertEquals(45, invoiceRepo.count(), "Re-entering Demo Mode must reset invoice count to exactly 45");

        // The transient invoice APS-DEMO-LIVE-001 must NOT exist
        assertFalse(invoiceRepo.findAll().stream().anyMatch(i -> "APS-DEMO-LIVE-001".equals(i.getInvoiceNumber())),
                "Transient invoice created in previous demo session must be cleanly discarded");

        // Clean up: exit demo mode
        demoModeController.exitDemoMode();
        assertFalse(EnvironmentModeContext.isDemoMode());
    }

    @Test
    @DisplayName("3. Crash Recovery Protection: Unexpected Session Disruption Safely Recovers to Normal Mode")
    void testCrashRecoverySafety() {
        // 1. Enter demo mode
        demoModeController.enterDemoMode(Collections.singletonMap("pin", "0000"));
        assertTrue(EnvironmentModeContext.isDemoMode());

        // 2. Simulate application abrupt exit / crash while in demo mode
        // In this scenario, cleanExit was left as false and mode was DEMO
        // Trigger startup crash recovery logic
        demoModeService.checkStartupCrashRecovery();

        // 3. System must safely default to NORMAL mode
        assertFalse(EnvironmentModeContext.isDemoMode());
        assertEquals(EnvironmentMode.NORMAL, EnvironmentModeContext.getCurrentMode());

        // 4. Status reflects crash recovery
        Map<String, Object> status = demoModeService.getStatus();
        assertFalse((Boolean) status.get("isDemoMode"));
        assertTrue((Boolean) status.get("crashRecovered"));

        // 5. Real database records remain safe and accessible
        assertEquals(3, firmDetailsRepo.count());
    }
}
