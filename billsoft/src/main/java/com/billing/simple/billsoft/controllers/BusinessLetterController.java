package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.entities.BusinessLetter;
import com.billing.simple.billsoft.entities.LetterRecipientType;
import com.billing.simple.billsoft.entities.LetterStatus;
import com.billing.simple.billsoft.service.BusinessLetterService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/letters")
public class BusinessLetterController {

    private final BusinessLetterService letterService;

    public BusinessLetterController(BusinessLetterService letterService) {
        this.letterService = letterService;
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
    public ResponseEntity<?> list(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(value = "firmId", required = false) Long firmIdParam,
            @RequestParam(value = "recipientType", required = false) LetterRecipientType recipientType,
            @RequestParam(value = "partyId", required = false) Long partyId,
            @RequestParam(value = "customerId", required = false) Long customerId,
            @RequestParam(value = "status", required = false) LetterStatus status,
            @RequestParam(value = "start", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(value = "end", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {

        Long firmId;
        try {
            firmId = resolveAuthoritativeFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        } catch (com.billing.simple.billsoft.security.TenantSecurityException e) {
            return ResponseEntity.badRequest().build();
        }
        if (page != null || size != null) {
            org.springframework.data.domain.Pageable pageable = com.billing.simple.billsoft.util.PaginationUtils.createDefaultTransactionPageRequest(
                    page != null ? page : 0, size != null ? size : 25, "letterDate");
            return ResponseEntity.ok(letterService.getPaginatedLetters(firmId, pageable));
        }
        return ResponseEntity.ok(letterService.getLettersByFirm(firmId, recipientType, partyId, customerId, status, start, end));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BusinessLetter> getById(
            @PathVariable Long id,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(value = "firmId", required = false) Long firmIdParam) {

        Long firmId = resolveAuthoritativeFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        return letterService.getLetterById(id, firmId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/next-number")
    public ResponseEntity<Map<String, String>> getNextNumber(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(value = "firmId", required = false) Long firmIdParam) {

        Long firmId = resolveAuthoritativeFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        String nextNo = letterService.generateNextLetterNumber(firmId);
        return ResponseEntity.ok(Map.of("nextNumber", nextNo));
    }

    @PostMapping
    public ResponseEntity<BusinessLetter> create(
            @RequestBody BusinessLetter letter,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {

        Long firmId = resolveAuthoritativeFirmId(letter.getFirmId() != null ? letter.getFirmId() : firmIdHeader);
        letter.setFirmId(firmId);
        try {
            return ResponseEntity.ok(letterService.createLetter(letter));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<BusinessLetter> update(
            @PathVariable Long id,
            @RequestBody BusinessLetter letter,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {

        Long firmId = resolveAuthoritativeFirmId(letter.getFirmId() != null ? letter.getFirmId() : firmIdHeader);
        letter.setFirmId(firmId);
        try {
            return ResponseEntity.ok(letterService.updateLetter(id, letter));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @RequestMapping(value = "/{id}/status", method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ResponseEntity<BusinessLetter> updateStatus(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(value = "status", required = false) String statusParam,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(value = "firmId", required = false) Long firmIdParam) {

        Long firmId = resolveAuthoritativeFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        String statusStr = null;
        if (body != null && body.containsKey("status")) {
            statusStr = body.get("status");
        } else if (statusParam != null) {
            statusStr = statusParam;
        }

        if (statusStr == null || statusStr.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            LetterStatus status = LetterStatus.valueOf(statusStr.trim().toUpperCase());
            return ResponseEntity.ok(letterService.updateStatus(id, firmId, status));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(value = "firmId", required = false) Long firmIdParam) {

        Long firmId = resolveAuthoritativeFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        try {
            letterService.deleteLetter(id, firmId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(
            @PathVariable Long id,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(value = "firmId", required = false) Long firmIdParam,
            @RequestParam(value = "theme", required = false) String theme,
            @RequestParam(value = "color", required = false) String color,
            @RequestParam(value = "format", required = false) String format) {

        Long firmId = resolveAuthoritativeFirmId(firmIdHeader != null ? firmIdHeader : firmIdParam);
        try {
            byte[] pdfBytes = letterService.generateLetterPdf(id, firmId, theme, color, format);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Letter-" + id + ".pdf\"");
            return ResponseEntity.ok().headers(headers).body(pdfBytes);
        } catch (IllegalArgumentException | com.billing.simple.billsoft.security.TenantSecurityException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
