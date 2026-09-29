package com.billing.simple.billsoft.regression.backup;

import com.billing.simple.billsoft.BillsoftApplication;
import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.*;
import com.billing.simple.billsoft.service.BackupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = BillsoftApplication.class)
@ActiveProfiles("test")
public class GenericMultiFirmBackupIdempotencyTest {

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
    @Autowired
    private ExpenseRepository expenseRepo;
    @Autowired
    private EmployeeRepository employeeRepo;

    @BeforeEach
    void setupCleanDatabase() {
        backupService.factoryReset();
    }

    @Test
    @DisplayName("1. Multi-Firm Generic Isolation & Idempotency Across 3 Distinct Firms (Firms 1, 2, 3)")
    public void testThreeFirmsSimultaneousExportAndMergeImportIdempotency() {
        // Setup Firm 1
        FirmDetails f1 = new FirmDetails();
        f1.setFirmName("Alpha Logistics Ltd");
        f1.setGstin("27ALPHAFIRM01");
        f1 = firmDetailsRepo.save(f1);
        Long f1Id = f1.getId();

        Customer c1 = new Customer();
        c1.setFirmId(f1Id);
        c1.setName("Alpha Client");
        c1.setPhone("9111111111");
        customerRepo.save(c1);

        Product p1 = Product.builder().firmId(f1Id).name("Alpha Widget").sku("AW-01").price(new BigDecimal("100.00")).build();
        productRepo.save(p1);

        // Setup Firm 2
        FirmDetails f2 = new FirmDetails();
        f2.setFirmName("Beta Solutions Ltd");
        f2.setGstin("27BETAFIRM02");
        f2 = firmDetailsRepo.save(f2);
        Long f2Id = f2.getId();

        Customer c2 = new Customer();
        c2.setFirmId(f2Id);
        c2.setName("Beta Client");
        c2.setPhone("9222222222");
        customerRepo.save(c2);

        Product p2 = Product.builder().firmId(f2Id).name("Beta Widget").sku("BW-02").price(new BigDecimal("200.00")).build();
        productRepo.save(p2);

        // Setup Firm 3
        FirmDetails f3 = new FirmDetails();
        f3.setFirmName("Gamma Enterprises");
        f3.setGstin("27GAMMAFIRM03");
        f3 = firmDetailsRepo.save(f3);
        Long f3Id = f3.getId();

        Customer c3 = new Customer();
        c3.setFirmId(f3Id);
        c3.setName("Gamma Client");
        c3.setPhone("9333333333");
        customerRepo.save(c3);

        Product p3 = Product.builder().firmId(f3Id).name("Gamma Widget").sku("GW-03").price(new BigDecimal("300.00")).build();
        productRepo.save(p3);

        // Baseline Counts
        assertEquals(1, customerRepo.countByFirmId(f1Id));
        assertEquals(1, customerRepo.countByFirmId(f2Id));
        assertEquals(1, customerRepo.countByFirmId(f3Id));

        // Export Full System
        BackupDTO fullBackup = backupService.exportAllData();
        assertNotNull(fullBackup);
        assertEquals(3, fullBackup.getCustomers().size());
        assertEquals(3, fullBackup.getProducts().size());

        // Perform 3 consecutive merge import cycles
        for (int cycle = 1; cycle <= 3; cycle++) {
            backupService.importSelectiveData(fullBackup, null, "merge", null);

            // Verify firm counts remain strictly 1
            assertEquals(1, customerRepo.countByFirmId(f1Id), "Cycle " + cycle + ": Firm 1 customer count changed");
            assertEquals(1, customerRepo.countByFirmId(f2Id), "Cycle " + cycle + ": Firm 2 customer count changed");
            assertEquals(1, customerRepo.countByFirmId(f3Id), "Cycle " + cycle + ": Firm 3 customer count changed");

            assertEquals(1, productRepo.countByFirmId(f1Id), "Cycle " + cycle + ": Firm 1 product count changed");
            assertEquals(1, productRepo.countByFirmId(f2Id), "Cycle " + cycle + ": Firm 2 product count changed");
            assertEquals(1, productRepo.countByFirmId(f3Id), "Cycle " + cycle + ": Firm 3 product count changed");

            // Verify firm isolation: Firm 1 customer must belong to Firm 1
            Customer loadedC1 = customerRepo.findByFirmIdOrderByNameAsc(f1Id).get(0);
            assertEquals("Alpha Client", loadedC1.getName());
            assertEquals(f1Id, loadedC1.getFirmId());

            Customer loadedC2 = customerRepo.findByFirmIdOrderByNameAsc(f2Id).get(0);
            assertEquals("Beta Client", loadedC2.getName());
            assertEquals(f2Id, loadedC2.getFirmId());

            Customer loadedC3 = customerRepo.findByFirmIdOrderByNameAsc(f3Id).get(0);
            assertEquals("Gamma Client", loadedC3.getName());
            assertEquals(f3Id, loadedC3.getFirmId());
        }
    }

    @Test
    @DisplayName("2. Legitimate Collision Preservation: Same Phone OR Same Name Across Distinct Records")
    public void testLegitimateCollisionsPreserved() {
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Collision Test Firm");
        firm = firmDetailsRepo.save(firm);
        Long firmId = firm.getId();

        // Case A: 2 distinct customers with identical phone number ("Rahul Sharma" vs "Pooja Sharma")
        Customer cA1 = new Customer();
        cA1.setFirmId(firmId);
        cA1.setName("Rahul Sharma");
        cA1.setPhone("9999999999");
        customerRepo.save(cA1);

        Customer cA2 = new Customer();
        cA2.setFirmId(firmId);
        cA2.setName("Pooja Sharma");
        cA2.setPhone("9999999999");
        customerRepo.save(cA2);

        // Case B: 2 distinct customers with identical name ("Amit Patil" with different phones)
        Customer cB1 = new Customer();
        cB1.setFirmId(firmId);
        cB1.setName("Amit Patil");
        cB1.setPhone("9999999999");
        customerRepo.save(cB1);

        Customer cB2 = new Customer();
        cB2.setFirmId(firmId);
        cB2.setName("Amit Patil");
        cB2.setPhone("8888888888");
        customerRepo.save(cB2);

        // Total distinct customers = 4
        assertEquals(4, customerRepo.countByFirmId(firmId));

        // Export and restore 3x in merge mode
        BackupDTO backup = backupService.exportData(firmId);
        for (int i = 1; i <= 3; i++) {
            backupService.importSelectiveData(backup, null, "merge", firmId);
            assertEquals(4, customerRepo.countByFirmId(firmId), "Iteration " + i + ": Distinct customers must remain 4");
        }

        List<Customer> customers = customerRepo.findByFirmIdOrderByNameAsc(firmId);
        assertEquals(4, customers.size());
        assertEquals(2, customers.stream().filter(c -> "Amit Patil".equals(c.getName())).count());
        assertEquals(1, customers.stream().filter(c -> "Rahul Sharma".equals(c.getName())).count());
        assertEquals(1, customers.stream().filter(c -> "Pooja Sharma".equals(c.getName())).count());
    }

    @Test
    @DisplayName("3. Update/Merge Semantics: Safe Property Filling Without Overwriting Live Database Data")
    public void testMergeUpdateSemantics() {
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Update Semantics Firm");
        firm = firmDetailsRepo.save(firm);
        Long firmId = firm.getId();

        // Customer in backup has missing email but has address
        Customer initialCust = new Customer();
        initialCust.setFirmId(firmId);
        initialCust.setName("Acme Corporation");
        initialCust.setPhone("9123456789");
        initialCust.setAddress("Original Address 100");
        initialCust = customerRepo.save(initialCust);

        // Export Backup
        BackupDTO backup = backupService.exportData(firmId);

        // In live DB, user updated email to 'live@acme.com'
        initialCust.setEmail("live@acme.com");
        customerRepo.save(initialCust);

        // Re-import backup in merge mode
        backupService.importSelectiveData(backup, null, "merge", firmId);

        // Verification: Live email is preserved, customer count is 1
        assertEquals(1, customerRepo.countByFirmId(firmId));
        Customer reloaded = customerRepo.findByFirmIdOrderByNameAsc(firmId).get(0);
        assertEquals("live@acme.com", reloaded.getEmail(), "Live email must NOT be overwritten by null in backup");
        assertEquals("Original Address 100", reloaded.getAddress());
    }
}
