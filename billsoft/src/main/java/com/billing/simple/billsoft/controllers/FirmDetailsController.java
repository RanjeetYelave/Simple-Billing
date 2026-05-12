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
import org.springframework.web.bind.annotation.RestController;

import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.service.FirmDetailsService;

@RestController
@RequestMapping("/api/firm")
@CrossOrigin
public class FirmDetailsController {

    private final FirmDetailsService service;

    public FirmDetailsController(FirmDetailsService service) {
        this.service = service;
    }

    @GetMapping
    public List<FirmDetails> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<FirmDetails> create() {
        return ResponseEntity.ok(service.create());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FirmDetails> get(@PathVariable Long id) {
        FirmDetails f = service.get(id);
        return f == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(f);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FirmDetails> update(@PathVariable Long id, @RequestBody FirmDetails details) {
        return ResponseEntity.ok(service.update(id, details));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
