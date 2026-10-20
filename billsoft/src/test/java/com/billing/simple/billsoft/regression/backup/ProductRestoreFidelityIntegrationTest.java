package com.billing.simple.billsoft.regression.backup;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Party;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.repositories.PartyRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.service.BackupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Tag("regression")
@Tag("integration")
@DisplayName("AUDIT-002: Product Restore Fidelity Integration Test")
@Transactional
public class ProductRestoreFidelityIntegrationTest {

    @Autowired
    private BackupService backupService;

    @Autowired
    private FirmDetailsRepository firmDetailsRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private PartyRepository partyRepo;

    private FirmDetails sourceFirm;

    @BeforeEach
    void setUp() {
        FirmDetails f = new FirmDetails();
        f.setFirmName("Fidelity Source Firm");
        f.setGstin("27AABCF1234F1Z1");
        sourceFirm = firmDetailsRepo.save(f);
    }

    @Test
    @DisplayName("Product restore preserves inInventory, isArchived, preferredPartyId, and canonicalProductId")
    void testProductFidelityAttributesRestored() {
        // 1. Create a supplier party
        Party supplier = partyRepo.save(Party.builder()
                .firmId(sourceFirm.getId())
                .name("Primary Vendor")
                .phone("9876543210")
                .build());

        // 2. Create canonical master product
        Product canonical = productRepo.save(Product.builder()
                .firmId(sourceFirm.getId())
                .name("Master Brand Soap")
                .sku("SOAP-CANONICAL")
                .price(BigDecimal.valueOf(50.0))
                .stockQuantity(BigDecimal.valueOf(100.0))
                .inInventory(true)
                .isArchived(false)
                .build());

        // 3. Create non-inventory service product
        Product serviceProduct = productRepo.save(Product.builder()
                .firmId(sourceFirm.getId())
                .name("Delivery Service")
                .sku("SRV-DELIV")
                .price(BigDecimal.valueOf(30.0))
                .stockQuantity(BigDecimal.ZERO)
                .inInventory(false)
                .isArchived(false)
                .build());

        // 4. Create archived variant product linked to supplier and canonical product
        Product variantArchived = productRepo.save(Product.builder()
                .firmId(sourceFirm.getId())
                .name("Old Soap Variant")
                .sku("SOAP-OLD-VAR")
                .price(BigDecimal.valueOf(45.0))
                .stockQuantity(BigDecimal.valueOf(5.0))
                .inInventory(true)
                .isArchived(true)
                .preferredPartyId(supplier.getId())
                .canonicalProductId(canonical.getId())
                .build());

        // Export data
        BackupDTO backup = backupService.exportData(sourceFirm.getId());
        assertThat(backup.getProducts()).hasSize(3);

        // Verify exported fidelity
        Product exportedService = backup.getProducts().stream()
                .filter(p -> "SRV-DELIV".equals(p.getSku())).findFirst().orElseThrow();
        assertThat(exportedService.getInInventory()).isFalse();

        Product exportedVariant = backup.getProducts().stream()
                .filter(p -> "SOAP-OLD-VAR".equals(p.getSku())).findFirst().orElseThrow();
        assertThat(exportedVariant.getIsArchived()).isTrue();
        assertThat(exportedVariant.getPreferredPartyId()).isEqualTo(supplier.getId());
        assertThat(exportedVariant.getCanonicalProductId()).isEqualTo(canonical.getId());

        // 5. Restore into a clone firm
        List<FirmDetails> restoredFirms = backupService.importSelectiveData(backup, Set.of(sourceFirm.getId()), "clone", null);
        assertThat(restoredFirms).isNotEmpty();
        Long newFirmId = restoredFirms.get(0).getId();
        assertThat(newFirmId).isNotEqualTo(sourceFirm.getId());

        // Verify products in new firm
        List<Product> restoredProducts = productRepo.findByFirmId(newFirmId);
        assertThat(restoredProducts).hasSize(3);

        // Verify non-inventory service product
        Product restoredServiceProd = restoredProducts.stream()
                .filter(p -> "SRV-DELIV".equals(p.getSku())).findFirst().orElseThrow();
        assertThat(restoredServiceProd.getInInventory()).isFalse();
        assertThat(restoredServiceProd.getIsArchived()).isFalse();

        // Verify archived variant with remapped FKs
        Product restoredVariantProd = restoredProducts.stream()
                .filter(p -> "SOAP-OLD-VAR".equals(p.getSku())).findFirst().orElseThrow();
        assertThat(restoredVariantProd.getIsArchived()).isTrue();
        assertThat(restoredVariantProd.getInInventory()).isTrue();

        // Remapped supplier check
        Party restoredSupplier = partyRepo.findByFirmIdOrderByNameAsc(newFirmId).stream()
                .filter(prt -> "Primary Vendor".equals(prt.getName())).findFirst().orElseThrow();
        assertThat(restoredVariantProd.getPreferredPartyId()).isEqualTo(restoredSupplier.getId());

        // Remapped canonical product check
        Product restoredCanonical = restoredProducts.stream()
                .filter(p -> "SOAP-CANONICAL".equals(p.getSku())).findFirst().orElseThrow();
        assertThat(restoredVariantProd.getCanonicalProductId()).isEqualTo(restoredCanonical.getId());
    }

    @Test
    @DisplayName("Product restore correctly resolves forward references (variant before canonical) and defaults legacy null flags")
    void testForwardReferenceAndLegacyBackupProductFidelity() {
        // Prepare backup where Product A (variant) appears BEFORE Product B (canonical)
        BackupDTO backup = new BackupDTO();
        backup.setMetadata(Map.of("version", "1.0", "type", "SINGLE_FIRM", "sourceFirmId", sourceFirm.getId()));
        backup.setFirmDetails(sourceFirm);

        Product variantBefore = Product.builder()
                .firmId(sourceFirm.getId())
                .name("Forward Variant")
                .sku("FWD-VAR")
                .canonicalProductId(999L) // points to canonical product appearing later in the list
                .inInventory(null)        // legacy backup missing inInventory
                .isArchived(null)         // legacy backup missing isArchived
                .build();
        variantBefore.setId(888L);

        Product canonicalAfter = Product.builder()
                .firmId(sourceFirm.getId())
                .name("Forward Canonical")
                .sku("FWD-CANON")
                .inInventory(true)
                .isArchived(false)
                .build();
        canonicalAfter.setId(999L);

        backup.setProducts(List.of(variantBefore, canonicalAfter));

        List<FirmDetails> restoredFirms = backupService.importSelectiveData(backup, Set.of(sourceFirm.getId()), "clone", null);
        assertThat(restoredFirms).isNotEmpty();
        Long newFirmId = restoredFirms.get(0).getId();

        List<Product> restoredProducts = productRepo.findByFirmId(newFirmId);
        Product restoredCanonical = restoredProducts.stream()
                .filter(p -> "FWD-CANON".equals(p.getSku())).findFirst().orElseThrow();
        Product restoredVariant = restoredProducts.stream()
                .filter(p -> "FWD-VAR".equals(p.getSku())).findFirst().orElseThrow();

        // 1. Verify source ID was not reused
        assertThat(restoredVariant.getId()).isNotEqualTo(888L);
        assertThat(restoredCanonical.getId()).isNotEqualTo(999L);

        // 2. Verify forward reference resolved to the newly generated canonical product ID
        assertThat(restoredVariant.getCanonicalProductId()).isEqualTo(restoredCanonical.getId());

        // 3. Verify legacy defaults applied: inInventory defaults to true, isArchived defaults to false
        assertThat(restoredVariant.getInInventory()).isTrue();
        assertThat(restoredVariant.getIsArchived()).isFalse();
    }
}
