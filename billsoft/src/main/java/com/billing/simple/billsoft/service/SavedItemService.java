package com.billing.simple.billsoft.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billing.simple.billsoft.dtos.PromoteSavedItemRequestDto;
import com.billing.simple.billsoft.dtos.UnifiedItemSuggestionDto;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.SavedItem;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repo.SavedItemRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;

@Service
public class SavedItemService {

    private static final Logger log = LoggerFactory.getLogger(SavedItemService.class);

    private final SavedItemRepository savedItemRepo;
    private final ProductRepository productRepo;
    private final ProductService productService;

    public SavedItemService(SavedItemRepository savedItemRepo,
                            ProductRepository productRepo,
                            ProductService productService) {
        this.savedItemRepo = savedItemRepo;
        this.productRepo = productRepo;
        this.productService = productService;
    }

    private Long resolveFirmId(Long explicitFirmId) {
        Long target = explicitFirmId != null ? explicitFirmId : TenantContext.getCurrentFirmId();
        if (target == null || target <= 0) {
            throw new TenantSecurityException("Active firm context is required");
        }
        return target;
    }

    /**
     * Fail-safe integration hook called after invoice creation/update.
     * Extracts uncatalogued invoice items (where product == null) and persists or updates SavedItem.
     * Wrapped in an explicit boundary so failure never corrupts or rolls back the parent invoice.
     */
    public void recordOrUpdateFromInvoice(Invoice invoice) {
        if (invoice == null || invoice.getItems() == null || invoice.getItems().isEmpty()) {
            return;
        }
        Long firmId = invoice.getFirmId();
        if (firmId == null) {
            return;
        }

        try {
            for (InvoiceItem item : invoice.getItems()) {
                if (item.getProduct() == null || item.getProduct().getId() == null) {
                    String name = item.getProductName();
                    if (name != null && !name.trim().isEmpty()) {
                        recordOrUpdateManualLine(firmId, name, item.getUnit(), item.getPricePerUnit(), item.getHsnCode(), item.getGstPercent());
                    }
                }
            }
        } catch (Exception ex) {
            // Fail-safe boundary: log warning but never let auxiliary SavedItem indexing disrupt billing
            log.warn("Non-fatal issue recording uncatalogued items for invoice ID {}: {}", invoice.getId(), ex.getMessage());
        }
    }

    @Transactional
    public SavedItem recordOrUpdateManualLine(Long firmId, String name, String unit, BigDecimal price, String hsn, BigDecimal gst) {
        if (firmId == null || name == null || name.trim().isEmpty()) {
            return null;
        }
        String cleanName = name.trim();
        String normalized = SavedItem.normalizeText(cleanName);
        if (normalized.isEmpty()) {
            return null;
        }

        // Check if matching Inventory Product already exists in the same firm
        List<Product> matchingProducts = productRepo.findByFirmId(firmId);
        boolean existsInInventory = matchingProducts.stream()
                .anyMatch(p -> !Boolean.TRUE.equals(p.getIsArchived()) &&
                               SavedItem.normalizeText(p.getName()).equals(normalized));

        Optional<SavedItem> existingOpt = savedItemRepo.findByFirmIdAndNormalizedNameAndIsArchivedFalse(firmId, normalized);

        if (existingOpt.isPresent()) {
            SavedItem item = existingOpt.get();
            item.setUsageCount(item.getUsageCount() != null ? item.getUsageCount() + 1 : 1);
            item.setLastUsedAt(LocalDateTime.now());
            if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                item.setSellingPrice(price);
            }
            if (unit != null && !unit.trim().isEmpty()) {
                item.setUnit(unit.trim());
            }
            if (hsn != null && !hsn.trim().isEmpty()) {
                item.setHsnCode(hsn.trim());
            }
            if (gst != null) {
                item.setGstPercentage(gst);
            }
            return savedItemRepo.save(item);
        } else {
            SavedItem newItem = SavedItem.builder()
                    .firmId(firmId)
                    .name(cleanName)
                    .normalizedName(normalized)
                    .unit(unit != null && !unit.trim().isEmpty() ? unit.trim() : "pcs")
                    .sellingPrice(price)
                    .hsnCode(hsn != null && !hsn.trim().isEmpty() ? hsn.trim() : null)
                    .gstPercentage(gst)
                    .usageCount(1)
                    .firstUsedAt(LocalDateTime.now())
                    .lastUsedAt(LocalDateTime.now())
                    .isArchived(false)
                    .build();

            // If an inventory product with the exact name already exists, record canonical link
            if (existsInInventory) {
                matchingProducts.stream()
                        .filter(p -> !Boolean.TRUE.equals(p.getIsArchived()) &&
                                     SavedItem.normalizeText(p.getName()).equals(normalized))
                        .findFirst()
                        .ifPresent(p -> newItem.setCanonicalProductId(p.getId()));
            }

            return savedItemRepo.save(newItem);
        }
    }

    /**
     * Unified autocomplete search combining Inventory Products and Unpromoted Saved Items.
     */
    public List<UnifiedItemSuggestionDto> searchUnifiedItems(Long firmId, String query) {
        Long targetFirmId = resolveFirmId(firmId);
        String q = (query != null ? query.trim().toLowerCase() : "");

        List<UnifiedItemSuggestionDto> results = new ArrayList<>();

        // 1. Fetch active Inventory Products
        List<Product> products = productRepo.findByFirmId(targetFirmId);
        for (Product p : products) {
            if (Boolean.TRUE.equals(p.getIsArchived())) continue;

            boolean matches = q.isEmpty() ||
                    (p.getName() != null && p.getName().toLowerCase().contains(q)) ||
                    (p.getSku() != null && p.getSku().toLowerCase().contains(q)) ||
                    (p.getCategory() != null && p.getCategory().toLowerCase().contains(q));

            if (matches) {
                String badge;
                if ("SERVICE".equalsIgnoreCase(p.getItemType())) {
                    badge = "Service";
                } else {
                    BigDecimal stock = p.getStockQuantity() != null ? p.getStockQuantity() : BigDecimal.ZERO;
                    badge = "Inventory · Stock: " + stock.stripTrailingZeros().toPlainString() + " " + (p.getUnit() != null ? p.getUnit() : "pcs");
                }

                results.add(UnifiedItemSuggestionDto.builder()
                        .id("PROD_" + p.getId())
                        .rawId(p.getId())
                        .source("INVENTORY")
                        .name(p.getName())
                        .sku(p.getSku())
                        .unit(p.getUnit())
                        .price(p.getPrice())
                        .costPrice(p.getCostPrice())
                        .stockQuantity(p.getStockQuantity())
                        .minStockLevel(p.getMinStockLevel())
                        .itemType(p.getItemType())
                        .hsnCode(p.getHsnCode())
                        .gstPercentage(p.getGstPercentage())
                        .category(p.getCategory())
                        .description(p.getDescription())
                        .badge(badge)
                        .canonicalProductId(p.getId())
                        .build());
            }
        }

        // 2. Fetch active Saved Items that are NOT yet promoted to an active inventory product
        List<SavedItem> savedItems = savedItemRepo.findActiveUnpromotedByFirmId(targetFirmId);
        for (SavedItem s : savedItems) {
            if (Boolean.TRUE.equals(s.getIsArchived())) continue;

            // If already promoted/linked, do not display separate duplicate suggestion
            if (s.getCanonicalProductId() != null) continue;

            boolean matches = q.isEmpty() ||
                    (s.getName() != null && s.getName().toLowerCase().contains(q)) ||
                    (s.getCategory() != null && s.getCategory().toLowerCase().contains(q)) ||
                    (s.getHsnCode() != null && s.getHsnCode().toLowerCase().contains(q));

            if (matches) {
                results.add(UnifiedItemSuggestionDto.builder()
                        .id("SAVED_" + s.getId())
                        .rawId(s.getId())
                        .source("SAVED_ITEM")
                        .name(s.getName())
                        .unit(s.getUnit())
                        .price(s.getSellingPrice())
                        .hsnCode(s.getHsnCode())
                        .gstPercentage(s.getGstPercentage())
                        .category(s.getCategory())
                        .description(s.getDescription())
                        .usageCount(s.getUsageCount())
                        .badge("Saved Item · Not in Inventory")
                        .build());
            }
        }

        // Sort: Inventory products matching query first, then frequent Saved Items
        results.sort((a, b) -> {
            boolean aInv = "INVENTORY".equals(a.getSource());
            boolean bInv = "INVENTORY".equals(b.getSource());
            if (aInv && !bInv) return -1;
            if (!aInv && bInv) return 1;
            if (!aInv && !bInv) {
                int uA = a.getUsageCount() != null ? a.getUsageCount() : 0;
                int uB = b.getUsageCount() != null ? b.getUsageCount() : 0;
                return Integer.compare(uB, uA);
            }
            return String.valueOf(a.getName()).compareToIgnoreCase(String.valueOf(b.getName()));
        });

        return results;
    }

    public List<SavedItem> getAllSavedItems(Long firmId) {
        Long targetFirmId = resolveFirmId(firmId);
        return savedItemRepo.findByFirmIdAndIsArchivedFalseOrderByUsageCountDescLastUsedAtDesc(targetFirmId);
    }

    /**
     * Promote a SavedItem to a real Inventory Product.
     */
    @Transactional
    public Product promoteToProduct(Long firmId, PromoteSavedItemRequestDto req) {
        Long targetFirmId = resolveFirmId(firmId);
        if (req == null || req.getSavedItemId() == null) {
            throw new IllegalArgumentException("SavedItem ID is required for promotion");
        }

        SavedItem savedItem = savedItemRepo.findByIdAndFirmId(req.getSavedItemId(), targetFirmId)
                .orElseThrow(() -> new IllegalArgumentException("Saved item not found for firm: " + req.getSavedItemId()));

        // Case A: User chose to link to an existing Inventory Product
        if (req.getExistingProductId() != null) {
            Product existing = productRepo.findByIdAndFirmId(req.getExistingProductId(), targetFirmId)
                    .orElseThrow(() -> new IllegalArgumentException("Target product not found: " + req.getExistingProductId()));
            savedItem.setCanonicalProductId(existing.getId());
            savedItemRepo.save(savedItem);
            return existing;
        }

        // Case B: Create new Inventory Product from SavedItem
        String productName = (req.getName() != null && !req.getName().trim().isEmpty())
                ? req.getName().trim()
                : savedItem.getName();

        Product product = Product.builder()
                .firmId(targetFirmId)
                .name(productName)
                .unit(req.getUnit() != null ? req.getUnit() : (savedItem.getUnit() != null ? savedItem.getUnit() : "pcs"))
                .price(req.getPrice() != null ? req.getPrice() : savedItem.getSellingPrice())
                .costPrice(req.getCostPrice())
                .stockQuantity(req.getStockQuantity() != null ? req.getStockQuantity() : BigDecimal.ZERO)
                .minStockLevel(req.getMinStockLevel() != null ? req.getMinStockLevel() : new BigDecimal("5.000"))
                .sku(req.getSku())
                .barcode(req.getBarcode())
                .hsnCode(req.getHsnCode() != null ? req.getHsnCode() : savedItem.getHsnCode())
                .gstPercentage(req.getGstPercentage() != null ? req.getGstPercentage() : savedItem.getGstPercentage())
                .category(req.getCategory() != null ? req.getCategory() : savedItem.getCategory())
                .itemType(req.getItemType() != null ? req.getItemType() : "GOODS")
                .description(req.getDescription() != null ? req.getDescription() : savedItem.getDescription())
                .isArchived(false)
                .build();

        Product createdProduct = productService.create(product);

        // Link SavedItem to the newly created Product
        savedItem.setCanonicalProductId(createdProduct.getId());
        savedItemRepo.save(savedItem);

        return createdProduct;
    }

    @Transactional
    public void archiveSavedItem(Long firmId, Long id) {
        Long targetFirmId = resolveFirmId(firmId);
        SavedItem item = savedItemRepo.findByIdAndFirmId(id, targetFirmId)
                .orElseThrow(() -> new IllegalArgumentException("Saved item not found: " + id));
        item.setIsArchived(true);
        savedItemRepo.save(item);
    }
}
