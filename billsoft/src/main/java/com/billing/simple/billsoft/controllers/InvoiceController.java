package com.billing.simple.billsoft.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceUpdateRequest;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.service.InvoiceService;

@RestController
@RequestMapping("/api/invoices")
@CrossOrigin
public class InvoiceController {

    private final InvoiceService service;

    public InvoiceController(InvoiceService service) {
        this.service = service;
    }

    // ------------------------------------------------------------
    // CREATE
    // ------------------------------------------------------------
    @PostMapping
    public ResponseEntity<Invoice> create(@RequestBody InvoiceRequest request) {
        return ResponseEntity.ok(service.createInvoice(request));
    }

    // ------------------------------------------------------------
    // LIST ALL
    // ------------------------------------------------------------
    @GetMapping
    public ResponseEntity<List<Invoice>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    // ------------------------------------------------------------
    // GET BY ID
    // ------------------------------------------------------------
    @GetMapping("/{id}")
    public ResponseEntity<Invoice> getById(@PathVariable("id") Long id) {
        Invoice inv = service.getById(id);
        if (inv == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(inv);
    }

    // ------------------------------------------------------------
    // UPDATE FULL INVOICE
    // ------------------------------------------------------------
    @PutMapping("/{id}")
    public ResponseEntity<Invoice> updateInvoice(
            @PathVariable("id") Long id,
            @RequestBody InvoiceUpdateRequest request) {

        Invoice updated = service.updateFullInvoice(id, request);
        if (updated == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(updated);
    }

    // ------------------------------------------------------------
    // DELETE
    // ------------------------------------------------------------
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        boolean removed = service.delete(id);
        if (!removed) return ResponseEntity.notFound().build();
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------
    // ANALYTICS BY CUSTOMER ID
    // ------------------------------------------------------------
    @GetMapping("/analytics/customer/{customerId}")
    public ResponseEntity<?> analyticsByCustomer(
            @PathVariable("customerId") Long customerId) {
        return ResponseEntity.ok(service.getCustomerAnalytics(customerId));
    }

    // ------------------------------------------------------------
    // ANALYTICS BY CUSTOMER NAME
    // ------------------------------------------------------------
    @GetMapping("/analytics/search")
    public ResponseEntity<?> analyticsByName(
            @RequestParam("name") String name) {
        return ResponseEntity.ok(service.getCustomerAnalyticsByName(name));
    }
}
