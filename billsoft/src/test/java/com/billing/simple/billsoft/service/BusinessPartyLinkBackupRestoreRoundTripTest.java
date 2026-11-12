package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.repositories.*;
import com.billing.simple.billsoft.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class BusinessPartyLinkBackupRestoreRoundTripTest {

    @Autowired
    private BackupService backupService;

    @Autowired
    private FirmDetailsRepository firmRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private PartyRepository partyRepo;

    @Autowired
    private BusinessPartyLinkRepository linkRepo;

    @Autowired
    private BusinessIdentityAliasRepository aliasRepo;

    @Autowired
    private BusinessPartyLinkEventRepository linkEventRepo;

    private Long firmId;

    @BeforeEach
    void setUp() {
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("RoundTrip Test Firm");
        firm.setAddressLine1("123 Test St");
        firm.setPhone("9876543210");
        firm = firmRepo.save(firm);
        firmId = firm.getId();
        TenantContext.setCurrentFirmId(firmId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @Transactional
    @DisplayName("Verify complete backup export and restore round-trip for BusinessPartyLink, Aliases, and Audit Events")
    void testCompleteRoundTripExportAndRestore() {
        // 1. Create Customer A and Party A
        Customer customerA = Customer.builder()
                .firmId(firmId)
                .name("Acme Corp")
                .phone("9998887771")
                .email("acme@test.com")
                .build();
        customerA = customerRepo.save(customerA);

        Party partyA = Party.builder()
                .firmId(firmId)
                .name("Acme Corp")
                .phone("9998887771")
                .email("acme@test.com")
                .build();
        partyA = partyRepo.save(partyA);

        // 2. Create Active Link 1
        BusinessPartyLink link1 = BusinessPartyLink.builder()
                .firmId(firmId)
                .customerId(customerA.getId())
                .partyId(partyA.getId())
                .activeCustomerId(customerA.getId())
                .activePartyId(partyA.getId())
                .status("ACTIVE")
                .customerRoleActive(true)
                .partyRoleActive(true)
                .relationshipVersion(2)
                .linkedBy("Admin")
                .notes("First link")
                .build();
        link1 = linkRepo.save(link1);

        // 3. Create Aliases
        BusinessIdentityAlias alias1 = BusinessIdentityAlias.builder()
                .firmId(firmId)
                .linkId(link1.getId())
                .customerId(customerA.getId())
                .partyId(partyA.getId())
                .aliasValue("Acme Trading")
                .normalizedValue("acme trading")
                .aliasType("TRADE_NAME")
                .ownerRole("SHARED")
                .active(true)
                .resolutionNotes("Formal trade name")
                .build();
        aliasRepo.save(alias1);

        BusinessIdentityAlias alias2 = BusinessIdentityAlias.builder()
                .firmId(firmId)
                .linkId(link1.getId())
                .customerId(customerA.getId())
                .partyId(partyA.getId())
                .aliasValue("Acme Wholesale")
                .normalizedValue("acme wholesale")
                .aliasType("PREVIOUS_NAME")
                .ownerRole("SHARED")
                .active(true)
                .resolutionNotes("Previous canonical name")
                .build();
        aliasRepo.save(alias2);

        // 4. Create Audit Events
        BusinessPartyLinkEvent event1 = BusinessPartyLinkEvent.builder()
                .firmId(firmId)
                .linkId(link1.getId())
                .relationshipVersion(1)
                .customerId(customerA.getId())
                .partyId(partyA.getId())
                .action("LINK_CREATED")
                .actor("Admin")
                .beforeValue(null)
                .afterValue("Acme Corp")
                .reason("Initial counterpart link")
                .timestamp(LocalDateTime.now().minusHours(2))
                .build();
        linkEventRepo.save(event1);

        BusinessPartyLinkEvent event2 = BusinessPartyLinkEvent.builder()
                .firmId(firmId)
                .linkId(link1.getId())
                .relationshipVersion(2)
                .customerId(customerA.getId())
                .partyId(partyA.getId())
                .action("CANONICAL_NAME_CHANGED")
                .actor("Admin")
                .beforeValue("Acme Wholesale")
                .afterValue("Acme Corp")
                .reason("Rebranding")
                .timestamp(LocalDateTime.now().minusHours(1))
                .build();
        linkEventRepo.save(event2);

        // 5. Create Soft-Unlinked Historical Link 2 (with different customer & party)
        Customer customerB = Customer.builder()
                .firmId(firmId)
                .name("Beta Supplies")
                .phone("9998887772")
                .build();
        customerB = customerRepo.save(customerB);

        Party partyB = Party.builder()
                .firmId(firmId)
                .name("Beta Supplies")
                .phone("9998887772")
                .build();
        partyB = partyRepo.save(partyB);

        BusinessPartyLink unlinkedLink = BusinessPartyLink.builder()
                .firmId(firmId)
                .customerId(customerB.getId())
                .partyId(partyB.getId())
                .activeCustomerId(null)
                .activePartyId(null)
                .status("UNLINKED")
                .customerRoleActive(false)
                .partyRoleActive(false)
                .relationshipVersion(1)
                .unlinkedBy("Admin")
                .unlinkedAt(LocalDateTime.now().minusMinutes(30))
                .unlinkReason("Ceased joint trading operations")
                .notes("Historical unlinked record")
                .build();
        unlinkedLink = linkRepo.save(unlinkedLink);

        // 6. Export single-firm backup
        BackupDTO firmBackup = backupService.exportData(firmId);
        assertNotNull(firmBackup);
        assertNotNull(firmBackup.getBusinessPartyLinks());
        assertEquals(2, firmBackup.getBusinessPartyLinks().size(), "Both active and unlinked links must be exported");
        assertNotNull(firmBackup.getBusinessIdentityAliases());
        assertEquals(2, firmBackup.getBusinessIdentityAliases().size(), "All aliases must be exported");
        assertNotNull(firmBackup.getBusinessPartyLinkEvents());
        assertEquals(2, firmBackup.getBusinessPartyLinkEvents().size(), "All audit events must be exported");

        // 7. Export all-data backup
        BackupDTO allBackup = backupService.exportAllData();
        assertNotNull(allBackup);
        assertTrue(allBackup.getBusinessPartyLinks().size() >= 2);
        assertTrue(allBackup.getBusinessIdentityAliases().size() >= 2);
        assertTrue(allBackup.getBusinessPartyLinkEvents().size() >= 2);

        // 8. Restore the exported backup into a new cloned firm
        List<FirmDetails> restoredFirmsList = backupService.importSelectiveData(firmBackup, null, "clone", null);
        assertNotNull(restoredFirmsList);
        assertFalse(restoredFirmsList.isEmpty());
        Long targetFirmId = restoredFirmsList.get(0).getId();
        assertNotNull(targetFirmId);
        assertNotEquals(firmId, targetFirmId);

        // 9. Verify restored datasets
        List<BusinessPartyLink> restoredLinks = linkRepo.findByFirmId(targetFirmId);
        assertNotNull(restoredLinks);
        assertEquals(2, restoredLinks.size(), "Both links must be restored in target firm");

        BusinessPartyLink restoredActive = restoredLinks.stream()
                .filter(l -> "ACTIVE".equals(l.getStatus()))
                .findFirst()
                .orElse(null);
        assertNotNull(restoredActive);
        assertNotNull(restoredActive.getActiveCustomerId());
        assertNotNull(restoredActive.getActivePartyId());
        assertEquals(2, restoredActive.getRelationshipVersion());

        BusinessPartyLink restoredUnlinked = restoredLinks.stream()
                .filter(l -> "UNLINKED".equals(l.getStatus()))
                .findFirst()
                .orElse(null);
        assertNotNull(restoredUnlinked);
        assertNull(restoredUnlinked.getActiveCustomerId(), "Unlinked record must have null active pointer");
        assertNull(restoredUnlinked.getActivePartyId(), "Unlinked record must have null active pointer");
        assertEquals("Ceased joint trading operations", restoredUnlinked.getUnlinkReason());

        List<BusinessIdentityAlias> restoredAliases = aliasRepo.findByFirmId(targetFirmId);
        assertNotNull(restoredAliases);
        assertEquals(2, restoredAliases.size(), "Both aliases must be restored in target firm");
        assertTrue(restoredAliases.stream().anyMatch(a -> "Acme Trading".equals(a.getAliasValue())));
        assertTrue(restoredAliases.stream().anyMatch(a -> "Acme Wholesale".equals(a.getAliasValue())));

        List<BusinessPartyLinkEvent> restoredEvents = linkEventRepo.findByFirmId(targetFirmId);
        assertNotNull(restoredEvents);
        assertEquals(2, restoredEvents.size(), "Both audit events must be restored in target firm");
        assertTrue(restoredEvents.stream().anyMatch(e -> "LINK_CREATED".equals(e.getAction())));
        assertTrue(restoredEvents.stream().anyMatch(e -> "CANONICAL_NAME_CHANGED".equals(e.getAction())));
    }
}
