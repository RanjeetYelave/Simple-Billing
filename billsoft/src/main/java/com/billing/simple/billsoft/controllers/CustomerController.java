package com.billing.simple.billsoft.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.service.CustomerService;

@RestController
@RequestMapping("/api/customers")
@CrossOrigin
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Customer> create(
            @RequestParam("firmId") Long firmId,
            @RequestBody Customer customer) {
        return ResponseEntity.ok(service.create(firmId, customer));
    }

    @GetMapping
    public ResponseEntity<List<Customer>> getAll(@RequestParam("firmId") Long firmId) {
        return ResponseEntity.ok(service.getAll(firmId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> getById(@PathVariable Long id) {
        Customer c = service.getById(id);
        if (c == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok(c);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Customer> update(@PathVariable Long id, @RequestBody Customer customer) {
        Customer updated = service.update(id, customer);
        if (updated == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        boolean removed = service.delete(id);
        if (!removed)
            return ResponseEntity.notFound().build();
        return ResponseEntity.noContent().build();
    }
}
