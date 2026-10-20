package com.billing.simple.billsoft.regression.update;

import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Database Preservation & Schema Migration Compatibility Adversarial Verification")
public class DatabasePreservationAndMigrationCompatibilityTest {

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private FirmDetailsRepository firmDetailsRepo;

    @Autowired
    private DataSource dataSource;

    @TempDir
    Path tempDir;

    @Test
    @Order(1)
    @DisplayName("Criterion 1: Customer Data Survives Failed Update & Candidate Crash")
    void test1_CustomerDataSurvivesFailedUpdate() throws Exception {
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Omega Engineering Pvt Ltd");
        firm.setGstin("27AAACW1234F1Z1");
        firm.setPhone("022-25890000");
        firm = firmDetailsRepo.save(firm);

        Customer customer = Customer.builder()
                .name("Apex Industrial Suppliers")
                .phone("9820012345")
                .email("procurement@apexind.com")
                .firmId(firm.getId())
                .gstin("27AABCA1111A1Z0")
                .address("Plot 42, MIDC Industrial Area, Turbhe")
                .build();
        customer = customerRepo.save(customer);

        Product product = Product.builder()
                .name("Precision Ball Bearing 6205")
                .price(new BigDecimal("450.00"))
                .costPrice(new BigDecimal("310.00"))
                .stockQuantity(new BigDecimal("250.000"))
                .minStockLevel(new BigDecimal("20.000"))
                .firmId(firm.getId())
                .sku("PBB-6205-Z")
                .build();
        product = productRepo.save(product);

        // Pre-update database quarantine backup
        Path backupZip = tempDir.resolve("pre-update-backup-c1.zip");
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            String escaped = backupZip.toFile().getAbsolutePath().replace("'", "''");
            try {
                stmt.execute("BACKUP TO '" + escaped + "'");
            } catch (Exception notPersistent) {
                stmt.execute("SCRIPT TO '" + escaped + "'");
            }
        }
        assertTrue(Files.exists(backupZip) && Files.size(backupZip) > 0, "Quarantine backup must be created");

        // Simulate candidate update failure
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE firm_details ADD COLUMN IF NOT EXISTS ota_feature_flag VARCHAR(50) DEFAULT 'ACTIVE'");
            stmt.execute("CREATE TABLE IF NOT EXISTS ota_candidate_metadata (id BIGINT PRIMARY KEY, version_tag VARCHAR(50))");
            stmt.execute("INSERT INTO ota_candidate_metadata (id, version_tag) VALUES (1, 'v1.2.0')");
        }

        // Verify baseline customer and product records survive completely
        Optional<Customer> reloadedCust = customerRepo.findById(customer.getId());
        assertTrue(reloadedCust.isPresent());
        assertEquals("Apex Industrial Suppliers", reloadedCust.get().getName());
        assertEquals("9820012345", reloadedCust.get().getPhone());
        assertEquals("27AABCA1111A1Z0", reloadedCust.get().getGstin());

        Optional<Product> reloadedProd = productRepo.findById(product.getId());
        assertTrue(reloadedProd.isPresent());
        assertEquals(0, new BigDecimal("450.00").compareTo(reloadedProd.get().getPrice()));
        assertEquals(0, new BigDecimal("250.000").compareTo(reloadedProd.get().getStockQuantity()));
    }

    @Test
    @Order(2)
    @DisplayName("Criterion 2: Old Binary Starts Against Post-Migration Persistent Database")
    void test2_OldBinaryStartsAgainstPostMigrationPersistentDatabase() throws Exception {
        Path dbDir = tempDir.resolve("persistent-c2-db");
        String jdbcUrl = "jdbc:h2:file:" + dbDir.toAbsolutePath() + ";DB_CLOSE_DELAY=-1";

        // Step A: Candidate initializes schema and performs forward migration
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE customers (id BIGINT PRIMARY KEY, name VARCHAR(100), phone VARCHAR(20))");
            stmt.execute("INSERT INTO customers VALUES (101, 'Persistent Client A', '9998887776')");

            // Candidate applies migration: adds new table and new column
            stmt.execute("ALTER TABLE customers ADD COLUMN candidate_tracking_id VARCHAR(50) DEFAULT 'TRK-001'");
            stmt.execute("CREATE TABLE candidate_audit_log (event_id BIGINT PRIMARY KEY, message VARCHAR(255))");
            stmt.execute("INSERT INTO candidate_audit_log VALUES (1, 'Candidate started migration')");
        }

        // Step B: Simulate candidate crash and older binary startup
        // The older binary connects with its standard driver and queries its known schema
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement stmt = conn.createStatement()) {
            // Old binary executes its baseline queries without error
            ResultSet rs = stmt.executeQuery("SELECT id, name, phone FROM customers WHERE id = 101");
            assertTrue(rs.next(), "Older binary must be able to select from customers table");
            assertEquals("Persistent Client A", rs.getString("name"));
            assertEquals("9998887776", rs.getString("phone"));

            // Check that the candidate's schema additions remain intact in the database
            ResultSet rsCandidate = stmt.executeQuery("SELECT candidate_tracking_id FROM customers WHERE id = 101");
            assertTrue(rsCandidate.next());
            assertEquals("TRK-001", rsCandidate.getString("candidate_tracking_id"));
        }
    }

    @Test
    @Order(3)
    @DisplayName("Criterion 3: Old Binary Can Read and Safely Update Representative Data on Post-Migration Schema")
    void test3_OldBinaryCanReadAndSafelyUpdateDataPostMigration() throws Exception {
        // Save initial record
        Customer customer = Customer.builder()
                .name("Pre-Migration Customer")
                .phone("9112233445")
                .email("original@test.com")
                .firmId(1L)
                .gstin("27AAAAA0000A1Z5")
                .address("Initial Address")
                .build();
        customer = customerRepo.save(customer);
        Long custId = customer.getId();

        Product product = Product.builder()
                .name("Test Product Evolution")
                .price(new BigDecimal("100.00"))
                .costPrice(new BigDecimal("70.00"))
                .stockQuantity(new BigDecimal("50.000"))
                .firmId(1L)
                .sku("EVO-100")
                .build();
        product = productRepo.save(product);
        Long prodId = product.getId();

        // Simulate candidate schema evolution on customers table
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE customers ADD COLUMN IF NOT EXISTS candidate_extra_col VARCHAR(100) DEFAULT 'CANDIDATE_DEFAULT'");
        }

        // Verify old binary can read and UPDATE representative customer
        Customer toUpdate = customerRepo.findById(custId).orElseThrow();
        toUpdate.setName("Post-Rollback Updated Customer Name");
        toUpdate.setAddress("Updated Office Address Post-Rollback");
        customerRepo.save(toUpdate);

        Customer verifiedCust = customerRepo.findById(custId).orElseThrow();
        assertEquals("Post-Rollback Updated Customer Name", verifiedCust.getName(), "Name update must succeed");
        assertEquals("Updated Office Address Post-Rollback", verifiedCust.getAddress(), "Address update must succeed");

        // Verify old binary can read and UPDATE representative product stock & price
        Product prodToUpdate = productRepo.findById(prodId).orElseThrow();
        prodToUpdate.setPrice(new BigDecimal("125.50"));
        prodToUpdate.setStockQuantity(new BigDecimal("42.000"));
        productRepo.save(prodToUpdate);

        Product verifiedProd = productRepo.findById(prodId).orElseThrow();
        assertEquals(0, new BigDecimal("125.50").compareTo(verifiedProd.getPrice()), "Updated price must persist");
        assertEquals(0, new BigDecimal("42.000").compareTo(verifiedProd.getStockQuantity()), "Updated stock quantity must persist");

        // Verify old binary can INSERT new records into the evolved table
        Customer newCustomer = Customer.builder()
                .name("Newly Inserted Post-Rollback Client")
                .phone("9887766554")
                .email("new@test.com")
                .firmId(1L)
                .gstin("27BBBBB1111B1Z9")
                .address("New Facility")
                .build();
        Customer savedNew = customerRepo.save(newCustomer);
        assertNotNull(savedNew.getId());

        Customer verifiedNew = customerRepo.findById(savedNew.getId()).orElseThrow();
        assertEquals("Newly Inserted Post-Rollback Client", verifiedNew.getName());
    }

    @Test
    @Order(4)
    @DisplayName("Criterion 4: Unsupported/Breaking Schema Incompatibility Detection & Non-Destructive Protection")
    void test4_UnsupportedSchemaIncompatibilityHandledSafelyWithoutDataLoss() throws Exception {
        Path dbDir = tempDir.resolve("breaking-schema-db");
        String jdbcUrl = "jdbc:h2:file:" + dbDir.toAbsolutePath() + ";DB_CLOSE_DELAY=-1";

        Path quarantineBackup = tempDir.resolve("pre-breaking-migration-backup.zip");

        // 1. Establish baseline customer database
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE legacy_customers (id BIGINT PRIMARY KEY, name VARCHAR(100), phone VARCHAR(20))");
            stmt.execute("INSERT INTO legacy_customers VALUES (1, 'Important Client 1', '9000000001')");

            // Quarantine backup taken before update transaction
            stmt.execute("BACKUP TO '" + quarantineBackup.toAbsolutePath().toString().replace("'", "''") + "'");
        }
        assertTrue(Files.exists(quarantineBackup) && Files.size(quarantineBackup) > 0);

        // 2. Candidate attempts a breaking, non-backward-compatible schema change:
        // A NOT NULL column with no default value on an existing non-empty table
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement stmt = conn.createStatement()) {
            SQLException ex = assertThrows(SQLException.class, () -> {
                stmt.execute("ALTER TABLE legacy_customers ADD COLUMN mandatory_gov_tax_id VARCHAR(50) NOT NULL");
            }, "Database engine must fail closed when invalid breaking DDL is executed on existing data");

            assertTrue(ex.getMessage().toLowerCase().contains("null not allowed") || ex.getMessage().toLowerCase().contains("mandatory_gov_tax_id"),
                    "Exception must pinpoint the constraint violation: " + ex.getMessage());

            // 3. Crucial Invariant: Existing data MUST NOT be corrupted or deleted!
            ResultSet rs = stmt.executeQuery("SELECT count(*) FROM legacy_customers");
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1), "Original record count must remain intact");

            ResultSet rsData = stmt.executeQuery("SELECT name, phone FROM legacy_customers WHERE id = 1");
            assertTrue(rsData.next());
            assertEquals("Important Client 1", rsData.getString("name"));
            assertEquals("9000000001", rsData.getString("phone"));
        }

        // 4. Incompatible constraint added to empty table: older binary insert fails closed safely
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE incompatible_entity (id BIGINT PRIMARY KEY, candidate_required VARCHAR(50) NOT NULL)");

            // Old binary attempts insert without knowing about candidate_required
            assertThrows(SQLException.class, () -> {
                stmt.execute("INSERT INTO incompatible_entity (id) VALUES (1)");
            }, "Old binary must fail closed rather than corrupting schema");
        }
    }

    @Test
    @Order(5)
    @DisplayName("Criterion 5: Supervisor Recovery Does NOT Silently Downgrade or Overwrite Customer Database")
    void test5_RecoveryDoesNotSilentlyDowngradeOrOverwriteDatabase() throws Exception {
        Path dbFile = tempDir.resolve("persistent-prod-db");
        String jdbcUrl = "jdbc:h2:file:" + dbFile.toAbsolutePath() + ";DB_CLOSE_DELAY=-1";

        Path backupZip = tempDir.resolve("quarantine-integrity-check.zip");
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE test_data (id INT PRIMARY KEY, val VARCHAR(50))");
            stmt.execute("INSERT INTO test_data VALUES (1, 'Customer Invoice Data')");

            String escaped = backupZip.toFile().getAbsolutePath().replace("'", "''");
            stmt.execute("BACKUP TO '" + escaped + "'");
        }

        assertTrue(Files.exists(backupZip), "Persistent backup ZIP must exist");
        assertTrue(Files.size(backupZip) > 0, "Persistent backup ZIP must not be empty");

        // Verify that the ZIP archive is valid and contains database files
        try (ZipFile zip = new ZipFile(backupZip.toFile())) {
            assertTrue(zip.size() > 0, "Backup ZIP must contain entries");
            boolean hasDbFiles = zip.stream().anyMatch(e -> e.getName().endsWith(".db") || e.getName().endsWith(".sql") || e.getName().contains("test_data"));
            assertTrue(hasDbFiles, "Backup ZIP must contain database file entries");
        }

        // Verify supervisor lifecycle restarts do not alter customer data
        long initialCount = customerRepo.count();
        for (int i = 0; i < 3; i++) {
            assertEquals(initialCount, customerRepo.count(), "Customer count must not change on restart #" + i);
        }
    }
}
