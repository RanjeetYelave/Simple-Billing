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

    @PostMapping("/enable-customer-role")
    public ResponseEntity<BusinessPartyLinkResponse> enableCustomerRole(
            @RequestBody EnableCustomerRoleRequest request,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        assertFirmAuthorization(firmIdHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(linkService.enableCustomerRole(request));
    }

    @PostMapping("/enable-party-role")
    public ResponseEntity<BusinessPartyLinkResponse> enablePartyRole(
            @RequestBody EnablePartyRoleRequest request,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        assertFirmAuthorization(firmIdHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(linkService.enablePartyRole(request));
    }

    @PutMapping("/{id}/canonical-name")
    public ResponseEntity<BusinessPartyLinkResponse> updateCanonicalName(
            @PathVariable Long id,
            @RequestBody CanonicalNameUpdateRequest request,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        assertFirmAuthorization(firmIdHeader);
        return ResponseEntity.ok(linkService.updateCanonicalName(id, request));
    }

    @PostMapping("/{id}/unlink")
    public ResponseEntity<BusinessPartyLinkResponse> softUnlink(
            @PathVariable Long id,
            @RequestBody UnlinkRequest request,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        assertFirmAuthorization(firmIdHeader);
        return ResponseEntity.ok(linkService.softUnlink(id, request));
    }

    @PutMapping("/{id}/sync")
    public ResponseEntity<BusinessPartyLinkResponse> syncProfiles(
            @PathVariable Long id,
            @RequestBody SyncProfileRequest request,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        assertFirmAuthorization(firmIdHeader);
        return ResponseEntity.ok(linkService.syncProfiles(id, request));
    }

    @GetMapping("/preview-customer-role")
    public ResponseEntity<RoleMatchPreviewResponse> previewEnableCustomerRole(
            @RequestParam Long partyId,
            @RequestParam(required = false) String gstin,
            @RequestParam(required = false) String phone,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        assertFirmAuthorization(firmIdHeader);
        return ResponseEntity.ok(linkService.previewEnableCustomerRole(partyId, gstin, phone));
    }

    @GetMapping("/preview-party-role")
    public ResponseEntity<RoleMatchPreviewResponse> previewEnablePartyRole(
            @RequestParam Long customerId,
            @RequestParam(required = false) String gstin,
            @RequestParam(required = false) String phone,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        assertFirmAuthorization(firmIdHeader);
        return ResponseEntity.ok(linkService.previewEnablePartyRole(customerId, gstin, phone));
    }

    @PutMapping("/{id}/disable-role")
    public ResponseEntity<BusinessPartyLinkResponse> disableRole(
            @PathVariable Long id,
            @RequestBody RoleLifecycleRequest request,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        assertFirmAuthorization(firmIdHeader);
        return ResponseEntity.ok(linkService.disableRole(id, request));
    }

    @PutMapping("/{id}/reenable-role")
    public ResponseEntity<BusinessPartyLinkResponse> reenableRole(
            @PathVariable Long id,
            @RequestBody RoleLifecycleRequest request,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        assertFirmAuthorization(firmIdHeader);
        return ResponseEntity.ok(linkService.reenableRole(id, request));
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

    @GetMapping("/search")
    public ResponseEntity<List<UnifiedCounterpartySearchResult>> searchUnified(
            @RequestParam(defaultValue = "CUSTOMER") String targetRole,
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false, defaultValue = "25") Integer limit,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        assertFirmAuthorization(firmIdHeader);
        return ResponseEntity.ok(linkService.searchUnified(targetRole, q, limit));
    }

    @GetMapping("/{id}/aliases")
    public ResponseEntity<List<AliasDto>> getAliases(
            @PathVariable Long id,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        assertFirmAuthorization(firmIdHeader);
        return ResponseEntity.ok(linkService.getAliases(id));
    }

    @PostMapping("/{id}/aliases")
    public ResponseEntity<AliasDto> addAlias(
            @PathVariable Long id,
            @RequestBody CreateAliasRequest request,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        assertFirmAuthorization(firmIdHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(linkService.addAlias(id, request));
    }

    @DeleteMapping("/{id}/aliases/{aliasId}")
    public ResponseEntity<Void> removeAlias(
            @PathVariable Long id,
            @PathVariable Long aliasId,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String actor,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        assertFirmAuthorization(firmIdHeader);
        linkService.removeAlias(id, aliasId, reason, actor);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<List<AuditTrailEventDto>> getAuditEvents(
            @PathVariable Long id,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        assertFirmAuthorization(firmIdHeader);
        return ResponseEntity.ok(linkService.getAuditEvents(id));
    }

    @GetMapping("/admin-pin/status")
    public ResponseEntity<Map<String, Boolean>> getAdminPinStatus(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(required = false) Long firmId) {
        Long authoritativeFirmId = firmIdHeader != null ? firmIdHeader : firmId;
        assertFirmAuthorization(authoritativeFirmId);
        return ResponseEntity.ok(Map.of("configured", linkService.isAdminPinConfigured(authoritativeFirmId)));
    }

    @PostMapping("/admin-pin/set")
    public ResponseEntity<Map<String, String>> setAdminPin(
            @RequestBody AdminPinSetRequest request,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(required = false) Long firmId) {
        Long authoritativeFirmId = firmIdHeader != null ? firmIdHeader : firmId;
        assertFirmAuthorization(authoritativeFirmId);
        linkService.setAdminPin(authoritativeFirmId, request);
        return ResponseEntity.ok(Map.of("message", "Administrator PIN configured successfully"));
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
