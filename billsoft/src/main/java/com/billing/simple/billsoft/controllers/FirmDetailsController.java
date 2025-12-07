package com.billing.simple.billsoft.controllers;

import org.springframework.web.bind.annotation.*;

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

    /**
     * Load firm by ID (client must send firmId stored after login)
     * GET /api/firm?firmId=123
     */
    @GetMapping
    public FirmDetails get(@RequestParam("firmId") Long firmId) {
        return service.get(firmId);
    }

    /**
     * Update firm profile for this firm only
     * PUT /api/firm?firmId=123
     */
    @PutMapping
    public FirmDetails update(@RequestParam("firmId") Long firmId,
                              @RequestBody FirmDetails details) {
        return service.update(firmId, details);
    }
}
