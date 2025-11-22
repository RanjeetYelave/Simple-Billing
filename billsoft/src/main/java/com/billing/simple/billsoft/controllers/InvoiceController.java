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
import com.billing.simple.billsoft.service.InvoicePdfService;
import com.billing.simple.billsoft.service.InvoiceService;

@RestController
@RequestMapping("/api/invoices")
@CrossOrigin
public class InvoiceController {

    private final InvoiceService service;
    private final InvoicePdfService pdfService;   // ✅ ADDED

    public InvoiceController(InvoiceService service, InvoicePdfService pdfService) {
        this.service = service;
        this.pdfService = pdfService; // ✅ ADDED
    }

    // CREATE
    @PostMapping
    public ResponseEntity<Invoice> create(@RequestBody InvoiceRequest request) {
        Invoice created = service.createInvoice(request);
        return ResponseEntity.ok(created);
    }

    // LIST ALL
    @GetMapping
    public ResponseEntity<List<Invoice>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    // GET BY ID
    @GetMapping("/{id}")
    public ResponseEntity<Invoice> getById(@PathVariable("id") Long id) {
        Invoice inv = service.getById(id);
        if (inv == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(inv);
    }

    // UPDATE FULL
    @PutMapping("/{id}")
    public ResponseEntity<Invoice> updateInvoice(
            @PathVariable("id") Long id,
            @RequestBody InvoiceUpdateRequest request) {

        Invoice updated = service.updateFullInvoice(id, request);
        if (updated == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(updated);
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        boolean removed = service.delete(id);
        if (!removed) return ResponseEntity.notFound().build();
        return ResponseEntity.noContent().build();
    }

    // MARK PAID / UNPAID
    @PutMapping("/{id}/paid")
    public ResponseEntity<Invoice> markPaid(
            @PathVariable("id") Long id,
            @RequestParam("paid") boolean paid) {

        Invoice updated = service.updatePaidFlag(id, paid);
        if (updated == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(updated);
    }

    // CUSTOMER ANALYTICS
    @GetMapping("/analytics/customer/{customerId}")
    public ResponseEntity<CustomerAnalyticsResponse> analyticsByCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(service.getCustomerAnalytics(customerId));
    }

    // CUSTOMER SEARCH ANALYTICS
    @GetMapping("/analytics/search")
    public ResponseEntity<List<CustomerAnalyticsResponse>> analyticsByName(@RequestParam("name") String name) {
        return ResponseEntity.ok(service.getCustomerAnalyticsByName(name));
    }

    // -------------------------------------------------------
    // PDF DOWNLOAD
    // -------------------------------------------------------
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        try {
            Invoice invoice = service.getById(id);  // ✅ FIXED (use service)
            if (invoice == null) return ResponseEntity.notFound().build();

            byte[] pdf = pdfService.generatePdf(invoice); // ✅ FIXED

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=invoice-" + invoice.getInvoiceNumber() + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);

        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
