package com.billing.simple.billsoft.regression.backup;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.service.BackupService;
import com.billing.simple.billsoft.service.CustomerService;
import com.billing.simple.billsoft.service.ProductService;
import com.billing.simple.billsoft.util.PasswordUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("regression")
@Tag("integration")
@DisplayName("Backup, Restore & Factory Reset Regression Tests")
class BackupAndResetRegressionTest {

    @Autowired
    private BackupService backupService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private ProductService productService;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private AppConfigRepository configRepo;

    @Autowired
    private ObjectMapper objectMapper;

    private final Long testFirmId = 1L;

    @BeforeEach
    void setUp() {
        backupService.factoryReset();
    }

    @Test
    @DisplayName("Should export complete database snapshot to valid BackupDTO JSON")
    void shouldExportDatabaseBackup() {
        customerService.create(Customer.builder()
                .name("Backup Test Customer")
                .phone("9988001122")
                .firmId(testFirmId)
                .build());

        productService.create(Product.builder()
                .name("Backup Test Product")
                .price(BigDecimal.valueOf(100.00))
                .stockQuantity(BigDecimal.valueOf(10.0))
                .firmId(testFirmId)
                .build());

        BackupDTO backup = backupService.exportData(testFirmId);

        assertThat(backup).isNotNull();
        assertThat(backup.getCustomers()).hasSize(1);
        assertThat(backup.getCustomers().get(0).getName()).isEqualTo("Backup Test Customer");
        assertThat(backup.getProducts()).hasSize(1);
        assertThat(backup.getProducts().get(0).getName()).isEqualTo("Backup Test Product");
    }

    @Test
    @DisplayName("Should import backup in MERGE mode and restore entities")
    void shouldImportBackupMerge() {
        BackupDTO backup = new BackupDTO();
        backup.setMetadata(java.util.Map.of("version", "1.0", "appVersion", "v1.0.0"));
        Customer c = Customer.builder()
                .name("Imported Partner")
                .phone("9123456780")
                .firmId(testFirmId)
                .build();
        backup.setCustomers(java.util.List.of(c));

        backupService.importData(backup, testFirmId, true);

        var customers = customerService.getAll(testFirmId);
        assertThat(customers).extracting(Customer::getName).contains("Imported Partner");
    }

    @Test
    @DisplayName("Should perform clean factory reset")
    void shouldPerformFactoryReset() {
        customerService.create(Customer.builder()
                .name("Pre-reset Customer")
                .phone("9999999999")
                .firmId(testFirmId)
                .build());

        assertThat(customerService.getAll(testFirmId)).hasSize(1);

        backupService.factoryReset();

        assertThat(customerService.getAll(testFirmId)).isEmpty();
    }
}
