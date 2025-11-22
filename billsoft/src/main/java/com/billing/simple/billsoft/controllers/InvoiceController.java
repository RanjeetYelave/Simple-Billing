package com.billing.simple.billsoft.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
        Invoice created = service.createInvoice(request);
        return ResponseEntity.ok(created);
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
    // UPDATE FULL INVOICE (items replaced)
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
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        boolean removed = service.delete(id);
        if (!removed) return ResponseEntity.notFound().build();
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------
    // 🔥 NEW ANALYTICS: BY CUSTOMER ID
    // ------------------------------------------------------------
    @GetMapping("/analytics/customer/{customerId}")
    public ResponseEntity<?> analyticsByCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(service.getCustomerAnalytics(customerId));
    }

    // ------------------------------------------------------------
    // 🔥 NEW ANALYTICS: SEARCH BY CUSTOMER NAME
    // ------------------------------------------------------------
    @GetMapping("/analytics/search")
    public ResponseEntity<?> analyticsByName(@RequestParam String name) {
        return ResponseEntity.ok(service.getCustomerAnalyticsByName(name));
    }
}
