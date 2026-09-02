package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.dtos.PartyFinancialSummary;
import com.billing.simple.billsoft.entities.Party;
import com.billing.simple.billsoft.entities.PartyPayment;
import com.billing.simple.billsoft.service.PartyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parties")
public class PartyController {

    private final PartyService partyService;

    public PartyController(PartyService partyService) {
        this.partyService = partyService;
    }

    @GetMapping
    public ResponseEntity<List<Party>> list(@RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
                                            @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = firmIdHeader != null ? firmIdHeader : firmIdParam;
        if (firmId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(partyService.getPartiesByFirm(firmId));
    }

    @GetMapping("/summaries")
    public ResponseEntity<List<PartyFinancialSummary>> listSummaries(@RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
                                                                     @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = firmIdHeader != null ? firmIdHeader : firmIdParam;
        if (firmId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(partyService.getAllPartiesWithFinancialSummaries(firmId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Party> getById(@PathVariable Long id,
                                         @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
                                         @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = firmIdHeader != null ? firmIdHeader : firmIdParam;
        return partyService.getPartyById(id, firmId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/financial-summary")
    public ResponseEntity<PartyFinancialSummary> getFinancialSummary(@PathVariable Long id,
                                                                     @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
                                                                     @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = firmIdHeader != null ? firmIdHeader : firmIdParam;
        if (firmId == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            return ResponseEntity.ok(partyService.getFinancialSummary(id, firmId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<Party> create(@RequestBody Party party,
                                        @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        if (party.getFirmId() == null && firmIdHeader != null) {
            party.setFirmId(firmIdHeader);
        }
        if (party.getFirmId() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(partyService.createParty(party));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Party> update(@PathVariable Long id,
                                        @RequestBody Party party,
                                        @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        if (party.getFirmId() == null && firmIdHeader != null) {
            party.setFirmId(firmIdHeader);
        }
        try {
            return ResponseEntity.ok(partyService.updateParty(id, party));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
                                       @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = firmIdHeader != null ? firmIdHeader : firmIdParam;
        if (firmId == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            partyService.deleteParty(id, firmId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // --- Payments Endpoints ---

    @GetMapping("/{id}/payments")
    public ResponseEntity<List<PartyPayment>> getPayments(@PathVariable Long id,
                                                          @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
                                                          @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = firmIdHeader != null ? firmIdHeader : firmIdParam;
        if (firmId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(partyService.getPaymentsByParty(id, firmId));
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<PartyPayment> recordPayment(@PathVariable Long id,
                                                      @RequestBody PartyPayment payment,
                                                      @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        payment.setPartyId(id);
        if (payment.getFirmId() == null && firmIdHeader != null) {
            payment.setFirmId(firmIdHeader);
        }
        if (payment.getFirmId() == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            return ResponseEntity.ok(partyService.recordPayment(payment));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/payments/{paymentId}")
    public ResponseEntity<Void> deletePayment(@PathVariable Long paymentId,
                                              @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
                                              @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = firmIdHeader != null ? firmIdHeader : firmIdParam;
        if (firmId == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            partyService.deletePayment(paymentId, firmId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
