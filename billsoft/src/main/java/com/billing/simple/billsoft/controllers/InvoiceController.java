package com.billing.simple.billsoft.controllers;

import java.util.List;

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
    public ResponseEntity<String> nextInvoiceNumber() {
        return ResponseEntity.ok(service.generateInvoiceNumber());
    }

    @GetMapping("/next-estimate-number")
    public ResponseEntity<String> nextEstimateNumber() {
        return ResponseEntity.ok(service.generateEstimateNumber());
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

    // ---------------- PREVIEW ----------------
    @PostMapping("/preview")
    public ResponseEntity<Invoice> preview(@RequestBody InvoiceRequest request) {
        return ResponseEntity.ok(service.previewInvoice(request));
    }

    // ---------------- LIST ----------------
    @GetMapping
    public ResponseEntity<List<Invoice>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/estimates")
    public ResponseEntity<List<Invoice>> getAllEstimates() {
        return ResponseEntity.ok(service.getAllEstimates());
    }

    @GetMapping("/final")
    public ResponseEntity<List<Invoice>> getAllFinalInvoices() {
        return ResponseEntity.ok(service.getAllFinalInvoices());
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

        Invoice inv = service.getById(id);
        if (inv == null) return ResponseEntity.notFound().build();

        inv.setStatus(status);
        return ResponseEntity.ok(service.updateFullInvoice(id, new InvoiceUpdateRequest()));
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
            @PathVariable("id") Long id,
            @RequestParam(name = "size", defaultValue = "A4") String size) {

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
}
