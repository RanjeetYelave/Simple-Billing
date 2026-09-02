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

    @GetMapping
    public ResponseEntity<List<BusinessLetter>> list(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(value = "firmId", required = false) Long firmIdParam,
            @RequestParam(value = "recipientType", required = false) LetterRecipientType recipientType,
            @RequestParam(value = "partyId", required = false) Long partyId,
            @RequestParam(value = "customerId", required = false) Long customerId,
            @RequestParam(value = "status", required = false) LetterStatus status,
            @RequestParam(value = "start", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(value = "end", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        Long firmId = firmIdHeader != null ? firmIdHeader : firmIdParam;
        if (firmId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(letterService.getLettersByFirm(firmId, recipientType, partyId, customerId, status, start, end));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BusinessLetter> getById(
            @PathVariable Long id,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(value = "firmId", required = false) Long firmIdParam) {

        Long firmId = firmIdHeader != null ? firmIdHeader : firmIdParam;
        return letterService.getLetterById(id, firmId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/next-number")
    public ResponseEntity<Map<String, String>> getNextNumber(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(value = "firmId", required = false) Long firmIdParam) {

        Long firmId = firmIdHeader != null ? firmIdHeader : firmIdParam;
        if (firmId == null) {
            return ResponseEntity.badRequest().build();
        }
        String nextNo = letterService.generateNextLetterNumber(firmId);
        return ResponseEntity.ok(Map.of("nextNumber", nextNo));
    }

    @PostMapping
    public ResponseEntity<BusinessLetter> create(
            @RequestBody BusinessLetter letter,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {

        if (letter.getFirmId() == null && firmIdHeader != null) {
            letter.setFirmId(firmIdHeader);
        }
        if (letter.getFirmId() == null) {
            return ResponseEntity.badRequest().build();
        }
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

        if (letter.getFirmId() == null && firmIdHeader != null) {
            letter.setFirmId(firmIdHeader);
        }
        try {
            return ResponseEntity.ok(letterService.updateLetter(id, letter));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<BusinessLetter> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(value = "firmId", required = false) Long firmIdParam) {

        Long firmId = firmIdHeader != null ? firmIdHeader : firmIdParam;
        if (firmId == null || !body.containsKey("status")) {
            return ResponseEntity.badRequest().build();
        }
        try {
            LetterStatus status = LetterStatus.valueOf(body.get("status").toUpperCase());
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

        Long firmId = firmIdHeader != null ? firmIdHeader : firmIdParam;
        if (firmId == null) {
            return ResponseEntity.badRequest().build();
        }
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
            @RequestParam(value = "firmId", required = false) Long firmIdParam) {

        Long firmId = firmIdHeader != null ? firmIdHeader : firmIdParam;
        try {
            byte[] pdfBytes = letterService.generateLetterPdf(id, firmId);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Letter-" + id + ".pdf\"");
            return ResponseEntity.ok().headers(headers).body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
