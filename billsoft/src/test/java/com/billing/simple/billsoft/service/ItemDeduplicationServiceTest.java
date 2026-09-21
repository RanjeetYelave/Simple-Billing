package com.billing.simple.billsoft.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.billing.simple.billsoft.dtos.DuplicateCandidateDto;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.repo.ItemDuplicateDismissalRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repo.SavedItemRepository;
import com.billing.simple.billsoft.security.TenantContext;

public class ItemDeduplicationServiceTest {

    @Mock
    private ProductRepository productRepo;

    @Mock
    private SavedItemRepository savedItemRepo;

    @Mock
    private ItemDuplicateDismissalRepository dismissalRepo;

    @InjectMocks
    private ItemDeduplicationService deduplicationService;

    private Long firmId = 2001L;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentFirmId(firmId);
    }

    @AfterEach
    public void tearDown() {
        TenantContext.clear();
    }

    @Test
    public void testDetectsSimilarUnitsAndAbbreviations() {
        Product prodA = Product.builder()
                .id(1L)
                .firmId(firmId)
                .name("SS Screw 25mm")
                .unit("pcs")
                .price(new BigDecimal("5.00"))
                .gstPercentage(new BigDecimal("18.00"))
                .isArchived(false)
                .build();

        Product prodB = Product.builder()
                .id(2L)
                .firmId(firmId)
                .name("Stainless Steel Screw 25mm")
                .unit("pcs")
                .price(new BigDecimal("5.00"))
                .gstPercentage(new BigDecimal("18.00"))
                .isArchived(false)
                .build();

        when(productRepo.findByFirmId(firmId)).thenReturn(Arrays.asList(prodA, prodB));
        when(savedItemRepo.findActiveUnpromotedByFirmId(firmId)).thenReturn(Collections.emptyList());
        when(dismissalRepo.findByFirmId(firmId)).thenReturn(Collections.emptyList());

        List<DuplicateCandidateDto> candidates = deduplicationService.findDuplicateCandidates(firmId);
        assertFalse(candidates.isEmpty());

        DuplicateCandidateDto match = candidates.stream()
                .filter(c -> c.getCandidateA().getName().contains("Screw 25mm") &&
                             c.getCandidateB().getName().contains("Screw 25mm"))
                .findFirst()
                .orElse(null);

        assertNotNull(match);
        assertTrue(match.getSimilarityScore() >= 80);
        assertTrue(match.isSafe());
        assertTrue(match.getReasons().stream().anyMatch(r -> r.contains("matching keywords") || r.contains("tokens")));
    }

    @Test
    public void testDetectsInchAndQuoteUnitEquivalence() {
        Product prodA = Product.builder()
                .id(3L)
                .firmId(firmId)
                .name("PVC Pipe 1 inch")
                .unit("inch")
                .price(new BigDecimal("120.00"))
                .gstPercentage(new BigDecimal("18.00"))
                .isArchived(false)
                .build();

        Product prodB = Product.builder()
                .id(4L)
                .firmId(firmId)
                .name("PVC Pipe 1\"")
                .unit("\"")
                .price(new BigDecimal("120.00"))
                .gstPercentage(new BigDecimal("18.00"))
                .isArchived(false)
                .build();

        when(productRepo.findByFirmId(firmId)).thenReturn(Arrays.asList(prodA, prodB));
        when(savedItemRepo.findActiveUnpromotedByFirmId(firmId)).thenReturn(Collections.emptyList());
        when(dismissalRepo.findByFirmId(firmId)).thenReturn(Collections.emptyList());

        List<DuplicateCandidateDto> candidates = deduplicationService.findDuplicateCandidates(firmId);
        assertFalse(candidates.isEmpty());

        DuplicateCandidateDto match = candidates.get(0);
        assertTrue(match.getSimilarityScore() >= 85);
        assertTrue(match.isSafe());
        assertTrue(match.getReasons().stream().anyMatch(r -> r.contains("unit") || r.contains("Identical")));
    }

    @Test
    public void testNumericDimensionVariantsAreSafelyDistinguished() {
        Product prod1 = Product.builder()
                .id(5L)
                .firmId(firmId)
                .name("PVC Pipe 1 inch")
                .unit("inch")
                .price(new BigDecimal("120.00"))
                .isArchived(false)
                .build();

        Product prod2 = Product.builder()
                .id(6L)
                .firmId(firmId)
                .name("PVC Pipe 1.5 inch")
                .unit("inch")
                .price(new BigDecimal("180.00"))
                .isArchived(false)
                .build();

        Product prod3 = Product.builder()
                .id(7L)
                .firmId(firmId)
                .name("Machine Screw 25mm")
                .unit("pcs")
                .price(new BigDecimal("10.00"))
                .isArchived(false)
                .build();

        Product prod4 = Product.builder()
                .id(8L)
                .firmId(firmId)
                .name("Machine Screw 30mm")
                .unit("pcs")
                .price(new BigDecimal("12.00"))
                .isArchived(false)
                .build();

        when(productRepo.findByFirmId(firmId)).thenReturn(Arrays.asList(prod1, prod2, prod3, prod4));
        when(savedItemRepo.findActiveUnpromotedByFirmId(firmId)).thenReturn(Collections.emptyList());
        when(dismissalRepo.findByFirmId(firmId)).thenReturn(Collections.emptyList());

        List<DuplicateCandidateDto> candidates = deduplicationService.findDuplicateCandidates(firmId);

        // Check Pipe 1 inch vs 1.5 inch
        DuplicateCandidateDto pipeCandidate = candidates.stream()
                .filter(c -> (c.getCandidateA().getName().contains("1 inch") && c.getCandidateB().getName().contains("1.5 inch")) ||
                             (c.getCandidateB().getName().contains("1 inch") && c.getCandidateA().getName().contains("1.5 inch")))
                .findFirst()
                .orElse(null);

        if (pipeCandidate != null) {
            assertFalse(pipeCandidate.isSafe(), "Dimension variant 1 vs 1.5 must NOT be marked safe!");
            assertTrue(pipeCandidate.getConflicts().stream().anyMatch(cf -> cf.contains("Dimension variant") || cf.contains("price")));
        }

        // Check Screw 25mm vs 30mm
        DuplicateCandidateDto screwCandidate = candidates.stream()
                .filter(c -> (c.getCandidateA().getName().contains("25mm") && c.getCandidateB().getName().contains("30mm")) ||
                             (c.getCandidateB().getName().contains("25mm") && c.getCandidateA().getName().contains("30mm")))
                .findFirst()
                .orElse(null);

        if (screwCandidate != null) {
            assertFalse(screwCandidate.isSafe(), "Dimension variant 25mm vs 30mm must NOT be marked safe!");
        }
    }

    @Test
    public void testDismissalPersistence() {
        Product prodA = Product.builder()
                .id(9L)
                .firmId(firmId)
                .name("Item Alpha 100 Pcs")
                .unit("pcs")
                .price(new BigDecimal("10.00"))
                .isArchived(false)
                .build();

        Product prodB = Product.builder()
                .id(10L)
                .firmId(firmId)
                .name("Item Alpha 100 Piece")
                .unit("pcs")
                .price(new BigDecimal("10.00"))
                .isArchived(false)
                .build();

        when(productRepo.findByFirmId(firmId)).thenReturn(Arrays.asList(prodA, prodB));
        when(savedItemRepo.findActiveUnpromotedByFirmId(firmId)).thenReturn(Collections.emptyList());
        when(dismissalRepo.findByFirmId(firmId)).thenReturn(Collections.emptyList());

        List<DuplicateCandidateDto> beforeDismiss = deduplicationService.findDuplicateCandidates(firmId);
        assertFalse(beforeDismiss.isEmpty());

        // Dismiss the pair
        deduplicationService.dismissCandidate(firmId, "PRODUCT", prodA.getId(), "PRODUCT", prodB.getId());
        verify(dismissalRepo, times(1)).save(any());
    }
}
