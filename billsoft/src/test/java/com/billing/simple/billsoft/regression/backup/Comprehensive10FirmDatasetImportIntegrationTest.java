package com.billing.simple.billsoft.regression.backup;

import com.billing.simple.billsoft.BillsoftApplication;
import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.dto.BackupInspectionDTO;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.*;
import com.billing.simple.billsoft.service.BackupArchiveService;
import com.billing.simple.billsoft.service.BackupService;
import com.billing.simple.billsoft.service.BackupValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = BillsoftApplication.class)
@ActiveProfiles("test")
@org.junit.jupiter.api.Disabled("Comprehensive 10-firm dataset import takes >10 minutes; run on demand")
public class Comprehensive10FirmDatasetImportIntegrationTest {

    @Autowired
    private BackupService backupService;

    @Autowired
    private BackupValidationService backupValidationService;

    @Autowired
    private BackupArchiveService backupArchiveService;

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

    @Autowired
    private ExpenseRepository expenseRepo;

    @Autowired
    private EmployeeRepository employeeRepo;

    @Autowired
    private GoalRepository goalRepo;

    @Autowired
    private SalesReturnRepository salesReturnRepo;

    @Autowired
    private StockMovementRepository stockMovementRepo;

    private File datasetFile;

    @BeforeEach
    void setupCleanDatabase() {
        backupService.factoryReset();

        // Locate dataset file
        File f1 = new File("RupeeCRM_10_Firms_Comprehensive_Test_Data.json");
        File f2 = new File("../RupeeCRM_10_Firms_Comprehensive_Test_Data.json");
        File f3 = new File("/Users/afk/Documents/GitHub/Simple-Billing/RupeeCRM_10_Firms_Comprehensive_Test_Data.json");

        if (f1.exists()) {
            datasetFile = f1;
        } else if (f2.exists()) {
            datasetFile = f2;
        } else if (f3.exists()) {
            datasetFile = f3;
        }
        assertNotNull(datasetFile, "Dataset file RupeeCRM_10_Firms_Comprehensive_Test_Data.json not found!");
        assertTrue(datasetFile.exists() && datasetFile.length() > 0, "Dataset file must exist and not be empty");
    }

    @Test
    @DisplayName("1. Deserialization, Schema Validation & Inspection of 10-Firm Dataset")
    public void testDatasetDeserializationAndValidation() throws Exception {
        BackupDTO backup;
        try (InputStream is = new FileInputStream(datasetFile)) {
            backup = backupArchiveService.extractAndValidateBackup(is, datasetFile.getName());
        }
        assertNotNull(backup, "Deserialized BackupDTO cannot be null");

        // Verify metadata
        assertNotNull(backup.getMetadata());
        assertEquals("2.0", backup.getMetadata().get("version"));
        assertEquals("FULL_SYSTEM_BACKUP", backup.getMetadata().get("type"));

        // Validate via BackupValidationService (must not throw any exception)
        assertDoesNotThrow(() -> backupValidationService.validate(backup));

        // Inspect via BackupService
        BackupInspectionDTO inspection = backupService.inspectBackup(backup);
        assertNotNull(inspection);
        assertEquals("FULL_SYSTEM_BACKUP", inspection.getBackupType());
        assertEquals(10, inspection.getFirms().size(), "Must contain exactly 10 firm summaries");

        Map<String, Integer> stats = inspection.getTotalStats();
        assertEquals(10, stats.get("totalFirms"));
        assertEquals(1500, stats.get("totalCustomers"));
        assertEquals(3000, stats.get("totalProducts"));
        assertEquals(5000, stats.get("totalInvoices"));
        assertEquals(1000, stats.get("totalPurchaseOrders"));
        assertEquals(100, stats.get("totalEmployees"));
        assertEquals(1500, stats.get("totalExpenses"));
        assertEquals(120, stats.get("totalGoals"));

        // Verify other entity collections in backup
        assertEquals(4000, backup.getInvoicePayments().size());
        assertEquals(300, backup.getParties().size());
        assertEquals(800, backup.getPartyPayments().size());
        assertEquals(200, backup.getSalesReturns().size());
        assertEquals(5500, backup.getStockMovements().size());
        assertEquals(500, backup.getAttendanceRecords().size());
        assertEquals(300, backup.getSalaryRecords().size());
        assertEquals(120, backup.getLeaveRecords().size());
        assertEquals(100, backup.getAdvances().size());
        assertEquals(50, backup.getPromotions().size());
        assertEquals(200, backup.getSavings().size());
        assertEquals(400, backup.getGoalLogs().size());
        assertEquals(200, backup.getReminders().size());
        assertEquals(150, backup.getNotes().size());
        assertEquals(50, backup.getBusinessLetters().size());
        assertEquals(100, backup.getNotifications().size());
        assertEquals(10, backup.getNotificationPreferences().size());
    }

    @Test
    @DisplayName("2. Multi-Firm Database Import & Isolation Verification (Firms 1, 2, 3)")
    public void testSelectiveImportOfFirmsAndIsolation() throws Exception {
        BackupDTO backup;
        try (InputStream is = new FileInputStream(datasetFile)) {
            backup = backupArchiveService.extractAndValidateBackup(is, datasetFile.getName());
        }

        // Import Firm 1, Firm 2, and Firm 3 (Selected IDs 1, 2, 3)
        Set<Long> selectedFirms = new HashSet<>(Arrays.asList(1L, 2L, 3L));
        List<FirmDetails> restoredFirms = backupService.importSelectiveData(backup, selectedFirms, "clone", null);

        assertEquals(3, restoredFirms.size(), "Should restore exactly 3 firms");
        Long f1Id = restoredFirms.get(0).getId();
        Long f2Id = restoredFirms.get(1).getId();
        Long f3Id = restoredFirms.get(2).getId();

        assertNotEquals(f1Id, f2Id);
        assertNotEquals(f2Id, f3Id);

        // Verify record counts per firm
        assertEquals(150, customerRepo.countByFirmId(f1Id), "Firm 1 must have 150 customers");
        assertEquals(150, customerRepo.countByFirmId(f2Id), "Firm 2 must have 150 customers");
        assertEquals(150, customerRepo.countByFirmId(f3Id), "Firm 3 must have 150 customers");

        assertEquals(300, productRepo.countByFirmId(f1Id), "Firm 1 must have 300 products");
        assertEquals(300, productRepo.countByFirmId(f2Id), "Firm 2 must have 300 products");
        assertEquals(300, productRepo.countByFirmId(f3Id), "Firm 3 must have 300 products");

        assertEquals(500, invoiceRepo.countByFirmId(f1Id), "Firm 1 must have 500 invoices");
        assertEquals(500, invoiceRepo.countByFirmId(f2Id), "Firm 2 must have 500 invoices");
        assertEquals(500, invoiceRepo.countByFirmId(f3Id), "Firm 3 must have 500 invoices");

        assertEquals(100, purchaseOrderRepo.countByFirmId(f1Id), "Firm 1 must have 100 POs");
        assertEquals(100, purchaseOrderRepo.countByFirmId(f2Id), "Firm 2 must have 100 POs");
        assertEquals(100, purchaseOrderRepo.countByFirmId(f3Id), "Firm 3 must have 100 POs");

        assertEquals(10, employeeRepo.countByFirmId(f1Id), "Firm 1 must have 10 employees");
        assertEquals(10, employeeRepo.countByFirmId(f2Id), "Firm 2 must have 10 employees");
        assertEquals(10, employeeRepo.countByFirmId(f3Id), "Firm 3 must have 10 employees");

        // Verify Deliberate Collision: "ABC Traders" exists in Firm 1, 2, and 3 as distinct records
        List<Customer> f1Abc = customerRepo.findByFirmIdAndNameIgnoreCase(f1Id, "ABC Traders");
        List<Customer> f2Abc = customerRepo.findByFirmIdAndNameIgnoreCase(f2Id, "ABC Traders");
        List<Customer> f3Abc = customerRepo.findByFirmIdAndNameIgnoreCase(f3Id, "ABC Traders");

        assertFalse(f1Abc.isEmpty(), "Firm 1 must have ABC Traders");
        assertFalse(f2Abc.isEmpty(), "Firm 2 must have ABC Traders");
        assertFalse(f3Abc.isEmpty(), "Firm 3 must have ABC Traders");

        assertNotEquals(f1Abc.get(0).getId(), f2Abc.get(0).getId(), "ABC Traders in Firm 1 and 2 must have distinct IDs");
        assertNotEquals(f2Abc.get(0).getId(), f3Abc.get(0).getId(), "ABC Traders in Firm 2 and 3 must have distinct IDs");

        assertEquals(f1Id, f1Abc.get(0).getFirmId());
        assertEquals(f2Id, f2Abc.get(0).getFirmId());
        assertEquals(f3Id, f3Abc.get(0).getFirmId());

        // Verify Invoices belong strictly to their firm
        List<Invoice> f1Invoices = invoiceRepo.findByFirmId(f1Id);
        assertEquals(500, f1Invoices.size());
        for (Invoice inv : f1Invoices) {
            assertEquals(f1Id, inv.getFirmId());
            assertNotNull(inv.getCustomer());
            assertEquals(f1Id, inv.getCustomer().getFirmId());
            for (InvoiceItem item : inv.getItems()) {
                assertNotNull(item.getProduct());
                assertEquals(f1Id, item.getProduct().getFirmId());
            }
        }
    }

    @Test
    @DisplayName("3. Clean Second Installation Portability & Repeatable Import Verification")
    public void testSecondCleanInstallationPortability() throws Exception {
        // Factory reset to simulate a completely independent, fresh installation
        backupService.factoryReset();
        assertEquals(0, firmDetailsRepo.count());
        assertEquals(0, customerRepo.count());
        assertEquals(0, productRepo.count());
        assertEquals(0, invoiceRepo.count());

        // Import dataset into the second clean installation
        BackupDTO backup;
        try (InputStream is = new FileInputStream(datasetFile)) {
            backup = backupArchiveService.extractAndValidateBackup(is, datasetFile.getName());
        }

        // Import Firm 4 & Firm 5 into clean environment
        Set<Long> selectedFirms = new HashSet<>(Arrays.asList(4L, 5L));
        List<FirmDetails> restored = backupService.importSelectiveData(backup, selectedFirms, "clone", null);

        assertEquals(2, restored.size());
        assertEquals(2, firmDetailsRepo.count());

        Long f4Id = restored.get(0).getId();
        Long f5Id = restored.get(1).getId();

        assertEquals(150, customerRepo.countByFirmId(f4Id));
        assertEquals(150, customerRepo.countByFirmId(f5Id));
        assertEquals(300, productRepo.countByFirmId(f4Id));
        assertEquals(300, productRepo.countByFirmId(f5Id));
        assertEquals(500, invoiceRepo.countByFirmId(f4Id));
        assertEquals(500, invoiceRepo.countByFirmId(f5Id));
    }
}
