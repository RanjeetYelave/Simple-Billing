package com.billing.simple.billsoft.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.billing.simple.billsoft.dtos.*;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.SavedItem;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.ItemDeduplicationService;
import com.billing.simple.billsoft.service.ItemMergeService;
import com.billing.simple.billsoft.service.SavedItemService;

@RestController
@CrossOrigin(origins = "*")
public class SavedItemController {

    private final SavedItemService savedItemService;
    private final ItemDeduplicationService deduplicationService;
    private final ItemMergeService mergeService;

    public SavedItemController(SavedItemService savedItemService,
                               ItemDeduplicationService deduplicationService,
                               ItemMergeService mergeService) {
        this.savedItemService = savedItemService;
        this.deduplicationService = deduplicationService;
        this.mergeService = mergeService;
    }

    /**
     * Unified search endpoint for invoice item autocomplete.
     * Returns both Inventory Products and frequent unpromoted Saved Items.
     */
    @GetMapping("/api/items/autocomplete")
    public ResponseEntity<List<UnifiedItemSuggestionDto>> autocompleteItems(
            @RequestParam(value = "q", required = false) String query,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmHeader) {
        Long firmId = firmHeader != null ? firmHeader : TenantContext.getCurrentFirmId();
        return ResponseEntity.ok(savedItemService.searchUnifiedItems(firmId, query));
    }

    /**
     * List all active Saved Items for the current firm.
     */
    @GetMapping("/api/saved-items")
    public ResponseEntity<List<SavedItem>> getAllSavedItems(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmHeader) {
        Long firmId = firmHeader != null ? firmHeader : TenantContext.getCurrentFirmId();
        return ResponseEntity.ok(savedItemService.getAllSavedItems(firmId));
    }

    /**
     * Promote a Saved Item to a real Inventory Product.
     */
    @PostMapping("/api/saved-items/{id}/promote")
    public ResponseEntity<Product> promoteSavedItem(
            @PathVariable("id") Long id,
            @RequestBody PromoteSavedItemRequestDto req,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmHeader) {
        Long firmId = firmHeader != null ? firmHeader : TenantContext.getCurrentFirmId();
        req.setSavedItemId(id);
        Product created = savedItemService.promoteToProduct(firmId, req);
        return ResponseEntity.ok(created);
    }

    /**
     * Archive/Delete a Saved Item.
     */
    @DeleteMapping("/api/saved-items/{id}")
    public ResponseEntity<Void> archiveSavedItem(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmHeader) {
        Long firmId = firmHeader != null ? firmHeader : TenantContext.getCurrentFirmId();
        savedItemService.archiveSavedItem(firmId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieve potential duplicate candidate pairs across active items. (Read-only)
     */
    @GetMapping("/api/items/duplicates")
    public ResponseEntity<List<DuplicateCandidateDto>> getDuplicateCandidates(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmHeader) {
        Long firmId = firmHeader != null ? firmHeader : TenantContext.getCurrentFirmId();
        return ResponseEntity.ok(deduplicationService.findDuplicateCandidates(firmId));
    }

    /**
     * Dismiss a duplicate candidate pair so it is not shown again.
     */
    @PostMapping("/api/items/duplicates/dismiss")
    public ResponseEntity<Map<String, String>> dismissDuplicate(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmHeader) {
        Long firmId = firmHeader != null ? firmHeader : TenantContext.getCurrentFirmId();
        String typeA = String.valueOf(payload.get("sourceTypeA"));
        Long idA = Long.valueOf(String.valueOf(payload.get("itemIdA")));
        String typeB = String.valueOf(payload.get("sourceTypeB"));
        Long idB = Long.valueOf(String.valueOf(payload.get("itemIdB")));

        deduplicationService.dismissCandidate(firmId, typeA, idA, typeB, idB);
        return ResponseEntity.ok(Map.of("status", "DISMISSED"));
    }

    /**
     * Single Merge Execution.
     */
    @PostMapping("/api/items/merge")
    public ResponseEntity<Map<String, Object>> mergeItems(
            @RequestBody MergeRequestDto req,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmHeader) {
        Long firmId = firmHeader != null ? firmHeader : TenantContext.getCurrentFirmId();
        Map<String, Object> result = mergeService.mergeItems(firmId, req);
        return ResponseEntity.ok(result);
    }

    /**
     * Batch Merge Execution (for 1-click safe merge or multi-item confirmed resolutions).
     */
    @PostMapping("/api/items/merge/batch")
    public ResponseEntity<Map<String, Object>> batchMerge(
            @RequestBody BatchMergeRequestDto batchReq,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmHeader) {
        Long firmId = firmHeader != null ? firmHeader : TenantContext.getCurrentFirmId();
        Map<String, Object> result = mergeService.batchMerge(firmId, batchReq);
        return ResponseEntity.ok(result);
    }
}
