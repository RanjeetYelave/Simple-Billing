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

    private Long resolveAuthoritativeFirmId(Long clientFirmId) {
        Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null && clientFirmId != null && !authoritativeFirmId.equals(clientFirmId)) {
            throw new com.billing.simple.billsoft.security.TenantSecurityException("Cross-firm access prohibited");
        }
        Long target = authoritativeFirmId != null ? authoritativeFirmId : clientFirmId;
        if (target == null) {
            throw new com.billing.simple.billsoft.security.TenantSecurityException("Active firm context is required");
        }
        return target;
    }

    @GetMapping
    public ResponseEntity<List<Party>> list(@RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
                                            @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = resolveAuthoritativeFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        return ResponseEntity.ok(partyService.getPartiesByFirm(firmId));
    }

    @GetMapping("/summaries")
    public ResponseEntity<com.billing.simple.billsoft.dtos.PageResponse<PartyFinancialSummary>> listSummaries(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(value = "firmId", required = false) Long firmIdParam,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Long firmId = resolveAuthoritativeFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        org.springframework.data.domain.Pageable pageable = com.billing.simple.billsoft.util.PaginationUtils.createDefaultTransactionPageRequest(page, size, "name");
        return ResponseEntity.ok(partyService.getPaginatedPartiesWithFinancialSummaries(firmId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Party> getById(@PathVariable Long id,
                                         @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
                                         @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = resolveAuthoritativeFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        return partyService.getPartyById(id, firmId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/financial-summary")
    public ResponseEntity<PartyFinancialSummary> getFinancialSummary(@PathVariable Long id,
                                                                     @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
                                                                     @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = resolveAuthoritativeFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        try {
            return ResponseEntity.ok(partyService.getFinancialSummary(id, firmId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<Party> create(@RequestBody Party party,
                                        @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        Long firmId = resolveAuthoritativeFirmId(party.getFirmId() != null ? party.getFirmId() : firmIdHeader);
        party.setFirmId(firmId);
        return ResponseEntity.ok(partyService.createParty(party));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Party> update(@PathVariable Long id,
                                        @RequestBody Party party,
                                        @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        Long firmId = resolveAuthoritativeFirmId(party.getFirmId() != null ? party.getFirmId() : firmIdHeader);
        party.setFirmId(firmId);
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
        Long firmId = resolveAuthoritativeFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
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
        Long firmId = resolveAuthoritativeFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        return ResponseEntity.ok(partyService.getPaymentsByParty(id, firmId));
    }

    @GetMapping("/{id}/unallocated-payments")
    public ResponseEntity<List<PartyPayment>> getUnallocatedPayments(@PathVariable Long id,
                                                                    @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
                                                                    @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = resolveAuthoritativeFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        return ResponseEntity.ok(partyService.getUnallocatedPayments(id, firmId));
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<PartyPayment> recordPayment(@PathVariable Long id,
                                                      @RequestBody PartyPayment payment,
                                                      @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        payment.setPartyId(id);
        Long firmId = resolveAuthoritativeFirmId(payment.getFirmId() != null ? payment.getFirmId() : firmIdHeader);
        payment.setFirmId(firmId);
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
        Long firmId = resolveAuthoritativeFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        try {
            partyService.deletePayment(paymentId, firmId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
