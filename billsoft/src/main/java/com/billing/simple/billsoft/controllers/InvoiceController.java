package com.billing.simple.billsoft.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.billing.simple.billsoft.dtos.CustomerAnalyticsResponse;
import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceUpdateRequest;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceStatus;
import com.billing.simple.billsoft.service.InvoicePdfService;
import com.billing.simple.billsoft.service.InvoiceService;

@RestController
@RequestMapping("/api/invoices")
@CrossOrigin
public class InvoiceController {

    private final InvoiceService service;
    private final InvoicePdfService pdfService;

    public InvoiceController(InvoiceService service, InvoicePdfService pdfService) {
        this.service = service;
        this.pdfService = pdfService;
    }

    // ---------------- NUMBER GENERATORS ----------------
    @GetMapping("/next-invoice-number")
    public ResponseEntity<String> nextInvoiceNumber(@RequestParam(required = false) Long firmId) {
        return ResponseEntity.ok(service.peekNextInvoiceNumber(firmId));
    }

    @GetMapping("/next-estimate-number")
    public ResponseEntity<String> nextEstimateNumber(@RequestParam(required = false) Long firmId) {
        return ResponseEntity.ok(service.peekNextEstimateNumber(firmId));
    }

    // ---------------- CREATE ----------------
    @PostMapping
    public ResponseEntity<Invoice> create(@RequestBody InvoiceRequest request) {
        if (request.getStatus() == null || request.getStatus() == InvoiceStatus.FINAL) {
            request.setStatus(Boolean.TRUE.equals(request.getPaid()) ? InvoiceStatus.PAID : InvoiceStatus.UNPAID);
        }
        return ResponseEntity.ok(service.createInvoice(request));
    }

    @PostMapping("/estimate")
    public ResponseEntity<Invoice> createEstimate(@RequestBody InvoiceRequest request) {
        request.setStatus(InvoiceStatus.ESTIMATE);
        return ResponseEntity.ok(service.createEstimate(request));
    }

    // ---------------- CONVERT ESTIMATE → INVOICE ----------------
    @PostMapping("/convert/{estimateId}")
    public ResponseEntity<Invoice> convertEstimate(
            @PathVariable Long estimateId,
            @RequestBody(required = false) InvoiceRequest overrideRequest) {

        return ResponseEntity.ok(service.convertEstimateToInvoice(estimateId, overrideRequest));
    }

    // ---------------- GET LINKED INVOICE FOR ESTIMATE ----------------
    @GetMapping("/{id}/linked-invoice")
    public ResponseEntity<Invoice> getLinkedInvoice(@PathVariable Long id) {
        Invoice inv = service.getById(id);
        if (inv == null) return ResponseEntity.notFound().build();
        if (inv.getConvertedInvoiceId() == null) return ResponseEntity.notFound().build();
        Invoice linked = service.getById(inv.getConvertedInvoiceId());
        return linked == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(linked);
    }

    // ---------------- PREVIEW ----------------
    @PostMapping("/preview")
    public ResponseEntity<Invoice> preview(@RequestBody InvoiceRequest request) {
        return ResponseEntity.ok(service.previewInvoice(request));
    }

    // ---------------- LIST (with pagination) ----------------
    @GetMapping
    public ResponseEntity<com.billing.simple.billsoft.dtos.PageResponse<Invoice>> getAll(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(required = false) Long firmId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Long targetFirmId = firmIdHeader != null ? firmIdHeader : firmId;
        org.springframework.data.domain.Pageable pageable = com.billing.simple.billsoft.util.PaginationUtils.createDefaultTransactionPageRequest(page, size, "invoiceDate");
        Page<Invoice> p = service.getPaginated(targetFirmId, pageable);
        return ResponseEntity.ok(com.billing.simple.billsoft.dtos.PageResponse.of(p));
    }

    @GetMapping("/estimates")
    public ResponseEntity<com.billing.simple.billsoft.dtos.PageResponse<Invoice>> getAllEstimates(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(required = false) Long firmId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Long targetFirmId = firmIdHeader != null ? firmIdHeader : firmId;
        org.springframework.data.domain.Pageable pageable = com.billing.simple.billsoft.util.PaginationUtils.createDefaultTransactionPageRequest(page, size, "invoiceDate");
        Page<Invoice> p = service.getPaginatedEstimates(targetFirmId, pageable);
        return ResponseEntity.ok(com.billing.simple.billsoft.dtos.PageResponse.of(p));
    }

    @GetMapping("/final")
    public ResponseEntity<com.billing.simple.billsoft.dtos.PageResponse<Invoice>> getAllFinalInvoices(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(required = false) Long firmId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Long targetFirmId = firmIdHeader != null ? firmIdHeader : firmId;
        org.springframework.data.domain.Pageable pageable = com.billing.simple.billsoft.util.PaginationUtils.createDefaultTransactionPageRequest(page, size, "invoiceDate");
        Page<Invoice> p = service.getPaginatedFinalInvoices(targetFirmId, pageable);
        return ResponseEntity.ok(com.billing.simple.billsoft.dtos.PageResponse.of(p));
    }

    // ---------------- GET BY ID ----------------
    @GetMapping("/{id}")
    public ResponseEntity<Invoice> getById(@PathVariable Long id) {
        Invoice inv = service.getById(id);
        return inv == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(inv);
    }

    // ---------------- UPDATE FULL ----------------
    @PutMapping("/{id}")
    public ResponseEntity<Invoice> updateInvoice(
            @PathVariable Long id,
            @RequestBody InvoiceUpdateRequest request) {

        Invoice updated = service.updateFullInvoice(id, request);
        return updated == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(updated);
    }

    // ---------------- DELETE ----------------
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    // ---------------- MARK PAID ----------------
    @PutMapping("/{id}/paid")
    public ResponseEntity<Invoice> markPaid(@PathVariable Long id, @RequestParam boolean paid) {
        Invoice updated = service.updatePaidFlag(id, paid);
        return updated == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(updated);
    }

    // ---------------- UPDATE STATUS (NEW) ----------------
    @PutMapping("/{id}/status")
    public ResponseEntity<Invoice> updateStatus(
            @PathVariable Long id,
            @RequestParam InvoiceStatus status) {

        Invoice updated = service.updateStatus(id, status);
        return updated == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(updated);
    }

    // ---------------- ANALYTICS ----------------
    @GetMapping("/analytics/customer/{customerId}")
    public ResponseEntity<CustomerAnalyticsResponse> analyticsByCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(service.getCustomerAnalytics(customerId));
    }

    @GetMapping("/analytics/search")
    public ResponseEntity<List<CustomerAnalyticsResponse>> analyticsByName(@RequestParam String name) {
        return ResponseEntity.ok(service.getCustomerAnalyticsByName(name));
    }

    // ---------------- PDF DOWNLOAD ----------------
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(
            @PathVariable Long id,
            @RequestParam(defaultValue = "A4") String size) {

        try {
            Invoice inv = service.getById(id);
            if (inv == null) return ResponseEntity.notFound().build();

            byte[] pdf = pdfService.generatePdf(inv, size);

            String filename = (inv.getStatus() == InvoiceStatus.ESTIMATE)
                    ? "estimate-" + inv.getEstimateNumber() + ".pdf"
                    : "invoice-" + inv.getInvoiceNumber() + ".pdf";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);

        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<com.billing.simple.billsoft.entities.InvoicePayment> recordPayment(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        try {
            java.math.BigDecimal amount = body.get("amount") != null
                    ? new java.math.BigDecimal(body.get("amount").toString())
                    : null;
            java.time.LocalDate paymentDate = body.get("paymentDate") != null
                    ? java.time.LocalDate.parse(body.get("paymentDate").toString())
                    : java.time.LocalDate.now();
            String paymentMode = body.get("paymentMode") != null ? body.get("paymentMode").toString() : "Cash";
            String referenceNumber = body.get("referenceNumber") != null ? body.get("referenceNumber").toString() : null;
            String notes = body.get("notes") != null ? body.get("notes").toString() : null;

            com.billing.simple.billsoft.entities.InvoicePayment payment =
                    service.recordPayment(id, amount, paymentDate, paymentMode, referenceNumber, notes);
            return ResponseEntity.ok(payment);
        } catch (com.billing.simple.billsoft.security.TenantSecurityException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/payments")
    public ResponseEntity<List<com.billing.simple.billsoft.entities.InvoicePayment>> getPayments(@PathVariable Long id) {
        return ResponseEntity.ok(service.getPayments(id));
    }
}
