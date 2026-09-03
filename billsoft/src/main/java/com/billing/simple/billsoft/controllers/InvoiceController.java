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
        return ResponseEntity.ok(service.generateInvoiceNumber(firmId));
    }

    @GetMapping("/next-estimate-number")
    public ResponseEntity<String> nextEstimateNumber(@RequestParam(required = false) Long firmId) {
        return ResponseEntity.ok(service.generateEstimateNumber(firmId));
    }

    // ---------------- CREATE ----------------
    @PostMapping
    public ResponseEntity<Invoice> create(@RequestBody InvoiceRequest request) {
        request.setStatus(InvoiceStatus.FINAL);
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
    public ResponseEntity<List<Invoice>> getAll(
            @RequestParam(required = false) Long firmId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if (page >= 0) {
            return ResponseEntity.ok(service.getAll(firmId, PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "invoiceDate"))));
        }
        return ResponseEntity.ok(service.getAll(firmId));
    }

    @GetMapping("/estimates")
    public ResponseEntity<List<Invoice>> getAllEstimates(
            @RequestParam(required = false) Long firmId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if (page >= 0) {
            return ResponseEntity.ok(service.getAllEstimates(firmId, PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "invoiceDate"))));
        }
        return ResponseEntity.ok(service.getAllEstimates(firmId));
    }

    @GetMapping("/final")
    public ResponseEntity<List<Invoice>> getAllFinalInvoices(
            @RequestParam(required = false) Long firmId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if (page >= 0) {
            return ResponseEntity.ok(service.getAllFinalInvoices(firmId, PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "invoiceDate"))));
        }
        return ResponseEntity.ok(service.getAllFinalInvoices(firmId));
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
        } catch (IllegalArgumentException e) {
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
