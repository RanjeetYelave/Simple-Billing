package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.dtos.debtor.*;
import com.billing.simple.billsoft.entities.CustomerCollectionActivity;
import com.billing.simple.billsoft.entities.CustomerPromiseToPay;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.billing.simple.billsoft.service.DebtorService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/debtors")
@CrossOrigin
public class DebtorController {

    private final DebtorService debtorService;

    public DebtorController(DebtorService debtorService) {
        this.debtorService = debtorService;
    }

    private Long resolveFirmId(Long clientFirmId) {
        Long authoritativeFirmId = TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null && clientFirmId != null && !authoritativeFirmId.equals(clientFirmId)) {
            throw new TenantSecurityException("Cross-firm access prohibited");
        }
        Long target = authoritativeFirmId != null ? authoritativeFirmId : clientFirmId;
        if (target == null) {
            throw new TenantSecurityException("Active firm context is required");
        }
        return target;
    }

    @GetMapping("/overview")
    public ResponseEntity<DebtorsOverviewDto> getOverview(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(name = "firmId", required = false) Long firmIdParam
    ) {
        Long effFirmId = resolveFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        return ResponseEntity.ok(debtorService.getOverview(effFirmId));
    }

    @GetMapping
    public ResponseEntity<List<DebtorListItemDto>> getDebtors(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(name = "firmId", required = false) Long firmIdParam,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "filter", required = false, defaultValue = "all") String filter,
            @RequestParam(name = "sortBy", required = false, defaultValue = "default") String sortBy,
            @RequestParam(name = "sortDir", required = false, defaultValue = "desc") String sortDir
    ) {
        Long effFirmId = resolveFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        return ResponseEntity.ok(debtorService.getDebtors(effFirmId, search, filter, sortBy, sortDir));
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<DebtorDetailDto> getDebtorDetail(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(name = "firmId", required = false) Long firmIdParam,
            @PathVariable("customerId") Long customerId
    ) {
        Long effFirmId = resolveFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        return ResponseEntity.ok(debtorService.getDebtorDetail(effFirmId, customerId));
    }

    @GetMapping("/{customerId}/overdue-invoices")
    public ResponseEntity<List<DebtorOverdueInvoiceDto>> getCustomerOverdueInvoices(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(name = "firmId", required = false) Long firmIdParam,
            @PathVariable("customerId") Long customerId
    ) {
        Long effFirmId = resolveFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        return ResponseEntity.ok(debtorService.getCustomerOverdueInvoices(effFirmId, customerId));
    }

    @GetMapping("/overdue-invoices")
    public ResponseEntity<List<DebtorOverdueInvoiceDto>> getFirmOverdueInvoices(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(name = "firmId", required = false) Long firmIdParam
    ) {
        Long effFirmId = resolveFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        return ResponseEntity.ok(debtorService.getFirmOverdueInvoices(effFirmId));
    }

    @GetMapping("/{customerId}/timeline")
    public ResponseEntity<List<CustomerCollectionActivity>> getCustomerTimeline(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(name = "firmId", required = false) Long firmIdParam,
            @PathVariable("customerId") Long customerId
    ) {
        Long effFirmId = resolveFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        return ResponseEntity.ok(debtorService.getCustomerTimeline(effFirmId, customerId));
    }

    @GetMapping("/{customerId}/payment-history")
    public ResponseEntity<List<DebtorPaymentHistoryItemDto>> getCustomerPaymentHistory(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(name = "firmId", required = false) Long firmIdParam,
            @PathVariable("customerId") Long customerId
    ) {
        Long effFirmId = resolveFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        return ResponseEntity.ok(debtorService.getCustomerPaymentHistory(effFirmId, customerId));
    }

    @GetMapping("/follow-ups")
    public ResponseEntity<List<DebtorFollowUpDto>> getFollowUps(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(name = "firmId", required = false) Long firmIdParam,
            @RequestParam(name = "status", required = false, defaultValue = "ALL") String status
    ) {
        Long effFirmId = resolveFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        return ResponseEntity.ok(debtorService.getFollowUps(effFirmId, status));
    }

    @PostMapping("/promises")
    public ResponseEntity<CustomerPromiseToPay> createPromise(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(name = "firmId", required = false) Long firmIdParam,
            @RequestBody CreatePromiseRequest request,
            Principal principal
    ) {
        Long effFirmId = resolveFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        String username = principal != null ? principal.getName() : "User";
        return ResponseEntity.ok(debtorService.createPromise(effFirmId, request, username));
    }

    @PutMapping("/promises/{id}/status")
    public ResponseEntity<CustomerPromiseToPay> updatePromiseStatus(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(name = "firmId", required = false) Long firmIdParam,
            @PathVariable("id") Long promiseId,
            @RequestBody UpdatePromiseStatusRequest request
    ) {
        Long effFirmId = resolveFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        return ResponseEntity.ok(debtorService.updatePromiseStatus(effFirmId, promiseId, request));
    }

    @PostMapping("/activity")
    public ResponseEntity<CustomerCollectionActivity> logActivity(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(name = "firmId", required = false) Long firmIdParam,
            @RequestBody LogActivityRequest request,
            Principal principal
    ) {
        Long effFirmId = resolveFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        String username = principal != null ? principal.getName() : "User";
        return ResponseEntity.ok(debtorService.logActivity(effFirmId, request, username));
    }

    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(name = "firmId", required = false) Long firmIdParam
    ) {
        Long effFirmId = resolveFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        byte[] csv = debtorService.exportDebtorsCsv(effFirmId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Debtors_Register_" + LocalDate.now() + ".csv\"");
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(name = "firmId", required = false) Long firmIdParam
    ) {
        Long effFirmId = resolveFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        byte[] xls = debtorService.exportDebtorsExcel(effFirmId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.ms-excel; charset=UTF-8"));
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Debtors_Register_" + LocalDate.now() + ".csv\"");
        return ResponseEntity.ok().headers(headers).body(xls);
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(name = "firmId", required = false) Long firmIdParam
    ) throws Exception {
        Long effFirmId = resolveFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        byte[] pdf = debtorService.exportDebtorsPdf(effFirmId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Debtors_Aging_Report_" + LocalDate.now() + ".pdf\"");
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}
