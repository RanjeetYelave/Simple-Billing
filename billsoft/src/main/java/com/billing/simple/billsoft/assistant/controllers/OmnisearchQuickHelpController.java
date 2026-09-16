package com.billing.simple.billsoft.assistant.controllers;

import com.billing.simple.billsoft.assistant.dto.OmnisearchQuickHelpDTOs.*;
import com.billing.simple.billsoft.assistant.service.OmnisearchQuickHelpService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Authoritative Read-Only REST Controller for OmniSearch Quick Help.
 */
@RestController
@RequestMapping("/api/omnisearch")
@CrossOrigin
public class OmnisearchQuickHelpController {

    private final OmnisearchQuickHelpService quickHelpService;

    public OmnisearchQuickHelpController(OmnisearchQuickHelpService quickHelpService) {
        this.quickHelpService = quickHelpService;
    }

    @GetMapping("/customer")
    public ResponseEntity<QuickHelpResponse<CustomerQuickHelpDTO>> getCustomer(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String query
    ) {
        String target = query != null ? query : (name != null ? name : phone);
        return ResponseEntity.ok(quickHelpService.getCustomerQuickHelp(target));
    }

    @GetMapping("/vendor")
    public ResponseEntity<QuickHelpResponse<VendorQuickHelpDTO>> getVendor(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String query
    ) {
        String target = query != null ? query : name;
        return ResponseEntity.ok(quickHelpService.getVendorQuickHelp(target));
    }

    @GetMapping("/invoice")
    public ResponseEntity<QuickHelpResponse<InvoiceDetailDTO>> getInvoice(
            @RequestParam(required = false) String number,
            @RequestParam(required = false) String query
    ) {
        String target = query != null ? query : number;
        return ResponseEntity.ok(quickHelpService.getInvoiceQuickHelp(target));
    }

    @GetMapping("/sales")
    public ResponseEntity<QuickHelpResponse<SalesSummaryDTO>> getSales(
            @RequestParam(required = false, defaultValue = "today") String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(quickHelpService.getSalesSummary(range, from, to));
    }

    @GetMapping("/expenses")
    public ResponseEntity<QuickHelpResponse<ExpenseSummaryDTO>> getExpenses(
            @RequestParam(required = false, defaultValue = "today") String range,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(quickHelpService.getExpenseSummary(range, category, from, to));
    }

    @GetMapping("/inventory")
    public ResponseEntity<QuickHelpResponse<InventorySummaryDTO>> getInventory(
            @RequestParam(required = false) String query,
            @RequestParam(required = false, defaultValue = "false") boolean lowStockOnly
    ) {
        return ResponseEntity.ok(quickHelpService.getInventoryQuickHelp(query, lowStockOnly));
    }

    @GetMapping("/hr")
    public ResponseEntity<QuickHelpResponse<HrStaffSummaryDTO>> getHrStaff(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String query
    ) {
        String target = query != null ? query : name;
        return ResponseEntity.ok(quickHelpService.getHrStaffQuickHelp(target));
    }

    @GetMapping("/aggregate")
    public ResponseEntity<QuickHelpResponse<MacroBusinessSummaryDTO>> getMacroSummary() {
        return ResponseEntity.ok(quickHelpService.getMacroBusinessSummary());
    }

    @GetMapping("/salaries")
    public ResponseEntity<QuickHelpResponse<SalaryQuickHelpDTO>> getSalaries(
            @RequestParam(required = false) String employee,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String period
    ) {
        String target = query != null ? query : employee;
        return ResponseEntity.ok(quickHelpService.getSalaryQuickHelp(target, period));
    }

    @GetMapping("/advances")
    public ResponseEntity<QuickHelpResponse<AdvanceQuickHelpDTO>> getAdvances(
            @RequestParam(required = false) String employee,
            @RequestParam(required = false) String query
    ) {
        String target = query != null ? query : employee;
        return ResponseEntity.ok(quickHelpService.getAdvanceQuickHelp(target));
    }

    @GetMapping("/returns")
    public ResponseEntity<QuickHelpResponse<SalesReturnQuickHelpDTO>> getReturns(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String period
    ) {
        return ResponseEntity.ok(quickHelpService.getSalesReturnQuickHelp(query, period));
    }

    @GetMapping("/collections")
    public ResponseEntity<QuickHelpResponse<CollectionReceiptQuickHelpDTO>> getCollections(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String mode
    ) {
        return ResponseEntity.ok(quickHelpService.getCollectionReceiptQuickHelp(query, period, mode));
    }

    @GetMapping("/vendor-payouts")
    public ResponseEntity<QuickHelpResponse<VendorPayoutQuickHelpDTO>> getVendorPayouts(
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String period
    ) {
        String target = query != null ? query : vendor;
        return ResponseEntity.ok(quickHelpService.getVendorPayoutQuickHelp(target, period));
    }

    @GetMapping("/purchase-orders")
    public ResponseEntity<QuickHelpResponse<PurchaseOrderQuickHelpDTO>> getPurchaseOrders(
            @RequestParam(required = false) String query
    ) {
        return ResponseEntity.ok(quickHelpService.getPurchaseOrderQuickHelp(query));
    }

    @GetMapping("/stock-movements")
    public ResponseEntity<QuickHelpResponse<StockMovementQuickHelpDTO>> getStockMovements(
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String query
    ) {
        String target = query != null ? query : product;
        return ResponseEntity.ok(quickHelpService.getStockMovementQuickHelp(target));
    }

    @GetMapping("/ledger")
    public ResponseEntity<QuickHelpResponse<LedgerStatementQuickHelpDTO>> getLedger(
            @RequestParam(required = false, defaultValue = "CUSTOMER") String type,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String query
    ) {
        String target = query != null ? query : name;
        return ResponseEntity.ok(quickHelpService.getLedgerQuickHelp(type, target));
    }

    @GetMapping("/search")
    public ResponseEntity<QuickHelpResponse<UnifiedSearchResultDTO>> searchAll(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String query
    ) {
        String target = query != null ? query : q;
        return ResponseEntity.ok(quickHelpService.searchAllEntities(target));
    }
}

