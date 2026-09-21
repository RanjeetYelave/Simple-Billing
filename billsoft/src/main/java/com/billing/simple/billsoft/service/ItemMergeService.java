package com.billing.simple.billsoft.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billing.simple.billsoft.dtos.BatchMergeRequestDto;
import com.billing.simple.billsoft.dtos.MergeRequestDto;
import com.billing.simple.billsoft.entities.ItemMergeAudit;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.SavedItem;
import com.billing.simple.billsoft.entities.StockMovement;
import com.billing.simple.billsoft.repo.ItemMergeAuditRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repo.SavedItemRepository;
import com.billing.simple.billsoft.repo.StockMovementRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ItemMergeService {

    private final ProductRepository productRepo;
    private final SavedItemRepository savedItemRepo;
    private final StockMovementRepository stockMovementRepo;
    private final ItemMergeAuditRepository mergeAuditRepo;
    private final ObjectMapper objectMapper;

    public ItemMergeService(ProductRepository productRepo,
                            SavedItemRepository savedItemRepo,
                            StockMovementRepository stockMovementRepo,
                            ItemMergeAuditRepository mergeAuditRepo) {
        this.productRepo = productRepo;
        this.savedItemRepo = savedItemRepo;
        this.stockMovementRepo = stockMovementRepo;
        this.mergeAuditRepo = mergeAuditRepo;
        this.objectMapper = new ObjectMapper();
    }

    private Long resolveFirmId(Long explicitFirmId) {
        Long target = explicitFirmId != null ? explicitFirmId : TenantContext.getCurrentFirmId();
        if (target == null || target <= 0) {
            throw new TenantSecurityException("Active firm context is required");
        }
        return target;
    }

    /**
     * Executes a single merge transactionally.
     */
    @Transactional
    public Map<String, Object> mergeItems(Long firmId, MergeRequestDto req) {
        Long targetFirmId = resolveFirmId(firmId);
        if (req == null || req.getPrimaryId() == null || req.getSecondaryId() == null) {
            throw new IllegalArgumentException("Primary and Secondary IDs are required for merge");
        }
        if (req.getPrimaryId().equals(req.getSecondaryId()) &&
            Objects.equals(req.getPrimaryType(), req.getSecondaryType())) {
            throw new IllegalArgumentException("Cannot merge an item with itself");
        }

        String pType = req.getPrimaryType() != null ? req.getPrimaryType().toUpperCase() : "PRODUCT";
        String sType = req.getSecondaryType() != null ? req.getSecondaryType().toUpperCase() : "PRODUCT";

        Map<String, Object> result = new HashMap<>();

        if ("PRODUCT".equals(pType) && "PRODUCT".equals(sType)) {
            result = mergeProductToProduct(targetFirmId, req);
        } else if ("PRODUCT".equals(pType) && "SAVED_ITEM".equals(sType)) {
            result = mergeSavedItemToProduct(targetFirmId, req.getPrimaryId(), req.getSecondaryId(), req);
        } else if ("SAVED_ITEM".equals(pType) && "PRODUCT".equals(sType)) {
            // User selected SavedItem as primary, Product as secondary -> normalize to Product as canonical
            result = mergeSavedItemToProduct(targetFirmId, req.getSecondaryId(), req.getPrimaryId(), req);
        } else if ("SAVED_ITEM".equals(pType) && "SAVED_ITEM".equals(sType)) {
            result = mergeSavedItemToSavedItem(targetFirmId, req);
        } else {
            throw new IllegalArgumentException("Unsupported merge types: " + pType + " -> " + sType);
        }

        return result;
    }

    /**
     * Product <- Product Merge
     */
    private Map<String, Object> mergeProductToProduct(Long firmId, MergeRequestDto req) {
        Product primary = productRepo.findByIdAndFirmId(req.getPrimaryId(), firmId)
                .orElseThrow(() -> new IllegalArgumentException("Primary product not found: " + req.getPrimaryId()));
        Product secondary = productRepo.findByIdAndFirmId(req.getSecondaryId(), firmId)
                .orElseThrow(() -> new IllegalArgumentException("Secondary product not found: " + req.getSecondaryId()));

        // Multi-tenant validation
        if (!firmId.equals(primary.getFirmId()) || !firmId.equals(secondary.getFirmId())) {
            throw new TenantSecurityException("Cross-firm product merge is strictly prohibited");
        }

        BigDecimal stockA = primary.getStockQuantity() != null ? primary.getStockQuantity() : BigDecimal.ZERO;
        BigDecimal stockB = secondary.getStockQuantity() != null ? secondary.getStockQuantity() : BigDecimal.ZERO;
        BigDecimal newStock = stockA;
        BigDecimal transferredStock = BigDecimal.ZERO;

        String stockStrategy = req.getStockStrategy() != null ? req.getStockStrategy().toUpperCase() : "ADD";
        if ("ADD".equals(stockStrategy)) {
            newStock = stockA.add(stockB);
            transferredStock = stockB;
        } else if ("KEEP_PRIMARY".equals(stockStrategy)) {
            newStock = stockA;
        } else if ("KEEP_SECONDARY".equals(stockStrategy)) {
            newStock = stockB;
            transferredStock = stockB.subtract(stockA);
        } else if ("MANUAL".equals(stockStrategy) && req.getManualStockQuantity() != null) {
            newStock = req.getManualStockQuantity();
            transferredStock = newStock.subtract(stockA);
        }

        // Record stock movements in the audit ledger if stock transferred
        if (transferredStock.compareTo(BigDecimal.ZERO) != 0 && !"SERVICE".equalsIgnoreCase(primary.getItemType())) {
            // Outflow from Secondary
            StockMovement outMovement = StockMovement.builder()
                    .productId(secondary.getId())
                    .productName(secondary.getName())
                    .firmId(firmId)
                    .movementType("MERGE_TRANSFER_OUT")
                    .quantityChange(stockB.negate())
                    .previousStock(stockB)
                    .newStock(BigDecimal.ZERO)
                    .referenceType("MERGE")
                    .referenceId("TO_PROD_" + primary.getId())
                    .note("Merged into primary product: " + primary.getName())
                    .createdAt(LocalDateTime.now())
                    .build();
            stockMovementRepo.save(outMovement);

            // Inflow to Primary
            StockMovement inMovement = StockMovement.builder()
                    .productId(primary.getId())
                    .productName(primary.getName())
                    .firmId(firmId)
                    .movementType("MERGE_TRANSFER_IN")
                    .quantityChange(transferredStock)
                    .previousStock(stockA)
                    .newStock(newStock)
                    .referenceType("MERGE")
                    .referenceId("FROM_PROD_" + secondary.getId())
                    .note("Stock transferred from merged duplicate: " + secondary.getName())
                    .createdAt(LocalDateTime.now())
                    .build();
            stockMovementRepo.save(inMovement);
        }

        // Apply field resolution
        if ("USE_SECONDARY".equalsIgnoreCase(req.getNameResolution()) && secondary.getName() != null) {
            primary.setName(secondary.getName());
        }
        if ("USE_SECONDARY".equalsIgnoreCase(req.getSellingPriceResolution()) && secondary.getPrice() != null) {
            primary.setPrice(secondary.getPrice());
        } else if ("CUSTOM".equalsIgnoreCase(req.getSellingPriceResolution()) && req.getCustomSellingPrice() != null) {
            primary.setPrice(req.getCustomSellingPrice());
        }
        if ("USE_SECONDARY".equalsIgnoreCase(req.getPurchasePriceResolution()) && secondary.getCostPrice() != null) {
            primary.setCostPrice(secondary.getCostPrice());
        } else if ("CUSTOM".equalsIgnoreCase(req.getPurchasePriceResolution()) && req.getCustomPurchasePrice() != null) {
            primary.setCostPrice(req.getCustomPurchasePrice());
        }
        if ("USE_SECONDARY".equalsIgnoreCase(req.getGstResolution()) && secondary.getGstPercentage() != null) {
            primary.setGstPercentage(secondary.getGstPercentage());
        }
        if ("USE_SECONDARY".equalsIgnoreCase(req.getUnitResolution()) && secondary.getUnit() != null) {
            primary.setUnit(secondary.getUnit());
        }
        if ("USE_SECONDARY".equalsIgnoreCase(req.getCategoryResolution()) && secondary.getCategory() != null) {
            primary.setCategory(secondary.getCategory());
        }

        primary.setStockQuantity(newStock);
        productRepo.save(primary);

        // Mark secondary product as archived with redirect link
        secondary.setIsArchived(true);
        secondary.setCanonicalProductId(primary.getId());
        secondary.setStockQuantity(BigDecimal.ZERO);
        productRepo.save(secondary);

        // Redirect any SavedItems pointing to secondary to now point to primary
        List<SavedItem> pointingToSecondary = savedItemRepo.findByFirmIdAndIsArchivedFalse(firmId);
        for (SavedItem s : pointingToSecondary) {
            if (Objects.equals(s.getCanonicalProductId(), secondary.getId())) {
                s.setCanonicalProductId(primary.getId());
                savedItemRepo.save(s);
            }
        }

        // Record Audit Ledger Entry
        logMergeAudit(firmId, "PRODUCT", primary.getId(), primary.getName(),
                "PRODUCT", secondary.getId(), secondary.getName(), transferredStock, req);

        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "SUCCESS");
        resp.put("canonicalProductId", primary.getId());
        resp.put("primaryName", primary.getName());
        resp.put("newStock", newStock);
        return resp;
    }

    /**
     * Product <- SavedItem Merge
     */
    private Map<String, Object> mergeSavedItemToProduct(Long firmId, Long productId, Long savedItemId, MergeRequestDto req) {
        Product primary = productRepo.findByIdAndFirmId(productId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        SavedItem secondary = savedItemRepo.findByIdAndFirmId(savedItemId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("SavedItem not found: " + savedItemId));

        if (!firmId.equals(primary.getFirmId()) || !firmId.equals(secondary.getFirmId())) {
            throw new TenantSecurityException("Cross-firm item merge is strictly prohibited");
        }

        // Link SavedItem to canonical product
        secondary.setCanonicalProductId(primary.getId());
        secondary.setIsArchived(false); // keep for canonical mapping without showing as duplicate
        savedItemRepo.save(secondary);

        // Record Audit Ledger Entry
        logMergeAudit(firmId, "PRODUCT", primary.getId(), primary.getName(),
                "SAVED_ITEM", secondary.getId(), secondary.getName(), BigDecimal.ZERO, req);

        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "SUCCESS");
        resp.put("canonicalProductId", primary.getId());
        resp.put("primaryName", primary.getName());
        return resp;
    }

    /**
     * SavedItem <- SavedItem Merge
     */
    private Map<String, Object> mergeSavedItemToSavedItem(Long firmId, MergeRequestDto req) {
        SavedItem primary = savedItemRepo.findByIdAndFirmId(req.getPrimaryId(), firmId)
                .orElseThrow(() -> new IllegalArgumentException("Primary saved item not found: " + req.getPrimaryId()));
        SavedItem secondary = savedItemRepo.findByIdAndFirmId(req.getSecondaryId(), firmId)
                .orElseThrow(() -> new IllegalArgumentException("Secondary saved item not found: " + req.getSecondaryId()));

        if (!firmId.equals(primary.getFirmId()) || !firmId.equals(secondary.getFirmId())) {
            throw new TenantSecurityException("Cross-firm item merge is strictly prohibited");
        }

        // Consolidate usage counts
        int usageA = primary.getUsageCount() != null ? primary.getUsageCount() : 1;
        int usageB = secondary.getUsageCount() != null ? secondary.getUsageCount() : 1;
        primary.setUsageCount(usageA + usageB);

        // Keep most recent lastUsedAt
        if (secondary.getLastUsedAt() != null &&
            (primary.getLastUsedAt() == null || secondary.getLastUsedAt().isAfter(primary.getLastUsedAt()))) {
            primary.setLastUsedAt(secondary.getLastUsedAt());
        }

        primary.setUpdatedAt(LocalDateTime.now());
        savedItemRepo.save(primary);

        // Archive secondary
        secondary.setIsArchived(true);
        savedItemRepo.save(secondary);

        // Record Audit Ledger Entry
        logMergeAudit(firmId, "SAVED_ITEM", primary.getId(), primary.getName(),
                "SAVED_ITEM", secondary.getId(), secondary.getName(), BigDecimal.ZERO, req);

        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "SUCCESS");
        resp.put("canonicalSavedItemId", primary.getId());
        resp.put("primaryName", primary.getName());
        resp.put("totalUsageCount", primary.getUsageCount());
        return resp;
    }

    /**
     * Batch Merge for Multiple Candidate Pairs (Safe 1-click batch merge or batch resolved list).
     */
    @Transactional
    public Map<String, Object> batchMerge(Long firmId, BatchMergeRequestDto batchReq) {
        Long targetFirmId = resolveFirmId(firmId);
        if (batchReq == null || batchReq.getMerges() == null || batchReq.getMerges().isEmpty()) {
            throw new IllegalArgumentException("No merges supplied in batch request");
        }

        int successCount = 0;
        List<Map<String, Object>> details = new ArrayList<>();

        for (MergeRequestDto req : batchReq.getMerges()) {
            try {
                Map<String, Object> res = mergeItems(targetFirmId, req);
                details.add(res);
                successCount++;
            } catch (Exception ex) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "FAILED");
                err.put("primaryId", req.getPrimaryId());
                err.put("secondaryId", req.getSecondaryId());
                err.put("error", ex.getMessage());
                details.add(err);
            }
        }

        Map<String, Object> batchResponse = new HashMap<>();
        batchResponse.put("totalRequested", batchReq.getMerges().size());
        batchResponse.put("successfulMerges", successCount);
        batchResponse.put("results", details);
        return batchResponse;
    }

    private void logMergeAudit(Long firmId, String pType, Long pId, String pName,
                               String sType, Long sId, String sName,
                               BigDecimal stockTransferred, MergeRequestDto req) {
        try {
            String details = objectMapper.writeValueAsString(req);
            ItemMergeAudit audit = ItemMergeAudit.builder()
                    .firmId(firmId)
                    .primaryType(pType)
                    .primaryId(pId)
                    .primaryName(pName)
                    .secondaryType(sType)
                    .secondaryId(sId)
                    .secondaryName(sName)
                    .stockTransferred(stockTransferred)
                    .detailsJson(details)
                    .mergedAt(LocalDateTime.now())
                    .build();
            mergeAuditRepo.save(audit);
        } catch (Exception ignored) {
        }
    }
}
