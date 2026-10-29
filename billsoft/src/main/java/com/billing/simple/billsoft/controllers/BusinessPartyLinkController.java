package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.dtos.*;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.billing.simple.billsoft.service.BusinessPartyLinkService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/party-links")
@CrossOrigin
public class BusinessPartyLinkController {

    private final BusinessPartyLinkService linkService;

    public BusinessPartyLinkController(BusinessPartyLinkService linkService) {
        this.linkService = linkService;
    }

    private void assertFirmAuthorization(Long firmIdHeader) {
        Long authoritativeFirmId = TenantContext.getCurrentFirmId();
        if (firmIdHeader != null && authoritativeFirmId != null && !authoritativeFirmId.equals(firmIdHeader)) {
            throw new TenantSecurityException("Cross-firm access prohibited");
        }
    }

    @PostMapping
    public ResponseEntity<BusinessPartyLinkResponse> link(
            @RequestBody BusinessPartyLinkRequest request,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        assertFirmAuthorization(firmIdHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(linkService.link(request));
    }

    @DeleteMapping
    public ResponseEntity<Void> unlink(
            @RequestParam Long customerId,
            @RequestParam Long partyId,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        assertFirmAuthorization(firmIdHeader);
        linkService.unlink(customerId, partyId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> unlinkById(
            @PathVariable Long id,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        assertFirmAuthorization(firmIdHeader);
        linkService.unlink(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<BusinessPartyLinkResponse> getByCustomer(
            @PathVariable Long customerId,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        assertFirmAuthorization(firmIdHeader);
        BusinessPartyLinkResponse res = linkService.getLinkedPartyForCustomer(customerId);
        if (res == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(res);
    }

    @GetMapping("/party/{partyId}")
    public ResponseEntity<BusinessPartyLinkResponse> getByParty(
            @PathVariable Long partyId,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        assertFirmAuthorization(firmIdHeader);
        BusinessPartyLinkResponse res = linkService.getLinkedCustomerForParty(partyId);
        if (res == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(res);
    }

    @GetMapping("/candidates")
    public ResponseEntity<List<PartyLinkCandidateDto>> searchCandidates(
            @RequestParam(defaultValue = "PARTY") String type,
            @RequestParam(required = false, defaultValue = "") String query,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        assertFirmAuthorization(firmIdHeader);
        return ResponseEntity.ok(linkService.searchCandidates(type, query));
    }

    @GetMapping("/map")
    public ResponseEntity<Map<String, Object>> getMap(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(required = false) Long firmId) {
        Long targetFirmId = firmIdHeader != null ? firmIdHeader : firmId;
        assertFirmAuthorization(targetFirmId);
        return ResponseEntity.ok(linkService.getFirmLinksMap(targetFirmId));
    }

    @GetMapping("/unified-statement")
    public ResponseEntity<UnifiedCounterpartyStatementResponse> getUnifiedStatement(
            @RequestParam Long customerId,
            @RequestParam Long partyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        assertFirmAuthorization(firmIdHeader);
        return ResponseEntity.ok(linkService.getUnifiedStatement(customerId, partyId, from, to));
    }
}
