package com.billing.simple.billsoft.controllers;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
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
    public FirmDetails get() {
        return service.get();
    }

    @PutMapping
    public FirmDetails update(@RequestBody FirmDetails details) {
        return service.update(details);
    }
}
