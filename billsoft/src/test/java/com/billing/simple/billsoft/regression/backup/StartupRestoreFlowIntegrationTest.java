package com.billing.simple.billsoft.regression.backup;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.service.BackupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Tag("regression")
@Tag("integration")
@DisplayName("Group 2 Cross-Flow: Startup and First-Run Restore Compatibility Test")
public class StartupRestoreFlowIntegrationTest {

    @Autowired
    private BackupService backupService;

    @Autowired
    private FirmDetailsRepository firmDetailsRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private ProductRepository productRepo;

    @Test
    @DisplayName("On uninitialized database with 0 firms, clean_wipe mode adapts to clone and restores cleanly; rejected when firms exist")
    void testStartupFirstRunRestoreCompatibility() {
        // 1. Ensure database is factory reset (0 firms)
        backupService.factoryReset();
        assertThat(firmDetailsRepo.count()).isZero();

        // 2. Prepare backup snapshot
        BackupDTO backup = new BackupDTO();
        backup.setMetadata(Map.of("version", "2.0", "type", "SINGLE_FIRM", "sourceFirmId", 100L));

        FirmDetails firm = new FirmDetails();
        firm.setId(100L);
        firm.setFirmName("Onboarding Restored Firm");
        firm.setGstin("27AABCO1234O1Z6");
        firm.setOwnerName("Owner Name");
        backup.setFirmDetails(firm);

        Customer cust = Customer.builder()
                .firmId(100L)
                .name("First Customer")
                .phone("9123456789")
                .build();
        cust.setId(1L);
        backup.setCustomers(List.of(cust));

        Product prod = Product.builder()
                .firmId(100L)
                .name("First Product")
                .price(BigDecimal.valueOf(250.0))
                .stockQuantity(BigDecimal.valueOf(10.0))
                .inInventory(true)
                .build();
        prod.setId(1L);
        backup.setProducts(List.of(prod));

        // 3. First-run restore with mode="clean_wipe"
        List<FirmDetails> restoredFirms = backupService.importSelectiveData(backup, null, "clean_wipe", null);
        assertThat(restoredFirms).hasSize(1);
        FirmDetails newFirm = restoredFirms.get(0);
        assertThat(newFirm.getId()).isNotNull();
        assertThat(newFirm.getFirmName()).isEqualTo("Onboarding Restored Firm");

        assertThat(customerRepo.findByFirmIdOrderByNameAsc(newFirm.getId())).hasSize(1);
        assertThat(productRepo.findByFirmId(newFirm.getId())).hasSize(1);

        // 4. Now that firms exist (count > 0), subsequent call with "clean_wipe" must fail
        assertThatThrownBy(() -> backupService.importSelectiveData(backup, null, "clean_wipe", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Destructive clean_wipe mode is not permitted");
    }
}
