package com.billing.simple.billsoft.controllers;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.billing.simple.billsoft.dtos.CustomerStatementResponse;
import com.billing.simple.billsoft.dtos.FirmStatementResponse;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.service.StatementService;

@RestController
@RequestMapping("/api/statements")
@CrossOrigin
public class StatementsController {

    private final StatementService statementService;
    private final CustomerRepository customerRepo;
    private final FirmDetailsRepository firmRepo;

    public StatementsController(
            StatementService statementService,
            CustomerRepository customerRepo,
            FirmDetailsRepository firmRepo
    ) {
        this.statementService = statementService;
        this.customerRepo = customerRepo;
        this.firmRepo = firmRepo;
    }

    private Long parseFirmId(String firmIdParam) {
        if (firmIdParam == null || firmIdParam.isBlank()) {
            return null;
        }
        String trimmed = firmIdParam.trim();
        if (trimmed.contains("[object") || trimmed.contains("Object")) {
            throw new IllegalArgumentException("Invalid firmId: " + firmIdParam);
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid firmId: " + firmIdParam);
        }
    }

    private void validatePrintOptions(String format, String theme, String color) {
        if (format != null && (format.contains("[object") || format.contains("Object"))) {
            throw new IllegalArgumentException("Invalid format: " + format);
        }
        if (theme != null && (theme.contains("[object") || theme.contains("Object"))) {
            throw new IllegalArgumentException("Invalid theme: " + theme);
        }
        if (color != null && (color.contains("[object") || color.contains("Object"))) {
            throw new IllegalArgumentException("Invalid color: " + color);
        }
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

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<CustomerStatementResponse> customerStatement(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(name = "firmId", required = false) String firmIdParam,
            @PathVariable("customerId") Long customerId,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Long clientFirmId = parseFirmId(firmIdParam);
        Long effFirmId = resolveAuthoritativeFirmId(firmIdHeader != null ? firmIdHeader : clientFirmId);
        return ResponseEntity.ok(statementService.getCustomerStatement(effFirmId, customerId, from, to));
    }

    @GetMapping("/customer/{customerId}/pdf")
    public ResponseEntity<byte[]> customerStatementPdf(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(name = "firmId", required = false) String firmIdParam,
            @PathVariable("customerId") Long customerId,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "theme", required = false) String theme,
            @RequestParam(name = "color", required = false) String color,
            @RequestParam(name = "format", required = false) String format
    ) throws Exception {
        validatePrintOptions(format, theme, color);
        Long clientFirmId = parseFirmId(firmIdParam);
        Long effFirmId = resolveAuthoritativeFirmId(firmIdHeader != null ? firmIdHeader : clientFirmId);
        byte[] pdf = statementService.generateCustomerStatementPdf(effFirmId, customerId, from, to, format, theme, color);

        Customer cust = customerRepo.findByIdAndFirmId(customerId, effFirmId).orElse(null);
        String custName = (cust != null && cust.getName() != null && !cust.getName().trim().isEmpty())
                ? cust.getName().trim().replaceAll("[^a-zA-Z0-9_-]", "_")
                : "Customer_" + customerId;
        String dateStr = (from != null || to != null)
                ? (from != null ? from.toString() : "Start") + "_to_" + (to != null ? to.toString() : LocalDate.now().toString())
                : "All_Time";
        String filename = "Cust_" + custName + "_" + dateStr + ".pdf";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdf);
    }

    @GetMapping("/firm")
    public ResponseEntity<FirmStatementResponse> firmStatement(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(name = "firmId", required = false) String firmIdParam,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Long clientFirmId = parseFirmId(firmIdParam);
        Long effFirmId = resolveAuthoritativeFirmId(firmIdHeader != null ? firmIdHeader : clientFirmId);
        return ResponseEntity.ok(statementService.getFirmStatement(effFirmId, from, to));
    }

    @GetMapping("/firm/pdf")
    public ResponseEntity<byte[]> firmStatementPdf(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(name = "firmId", required = false) String firmIdParam,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "theme", required = false) String theme,
            @RequestParam(name = "color", required = false) String color,
            @RequestParam(name = "format", required = false) String format
    ) throws Exception {
        validatePrintOptions(format, theme, color);
        Long clientFirmId = parseFirmId(firmIdParam);
        Long effFirmId = resolveAuthoritativeFirmId(firmIdHeader != null ? firmIdHeader : clientFirmId);
        byte[] pdf = statementService.generateFirmStatementPdf(effFirmId, from, to, format, theme, color);

        FirmDetails firm = firmRepo.findById(effFirmId).orElse(null);
        String firmName = (firm != null && firm.getFirmName() != null && !firm.getFirmName().trim().isEmpty())
                ? firm.getFirmName().trim().replaceAll("[^a-zA-Z0-9_-]", "_")
                : "Firm_Statement";
        String dateStr = (from != null || to != null)
                ? (from != null ? from.toString() : "Start") + "_to_" + (to != null ? to.toString() : LocalDate.now().toString())
                : "All_Time";
        String filename = firmName + "_" + dateStr + ".pdf";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdf);
    }

    @GetMapping("/party/{partyId}")
    public ResponseEntity<com.billing.simple.billsoft.dtos.PartyStatementResponse> partyStatement(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(name = "firmId", required = false) String firmIdParam,
            @PathVariable("partyId") Long partyId,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Long clientFirmId = parseFirmId(firmIdParam);
        Long effFirmId = resolveAuthoritativeFirmId(firmIdHeader != null ? firmIdHeader : clientFirmId);
        return ResponseEntity.ok(statementService.getPartyStatement(effFirmId, partyId, from, to));
    }

    @GetMapping("/party/{partyId}/pdf")
    public ResponseEntity<byte[]> partyStatementPdf(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(name = "firmId", required = false) String firmIdParam,
            @PathVariable("partyId") Long partyId,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "theme", required = false) String theme,
            @RequestParam(name = "color", required = false) String color,
            @RequestParam(name = "format", required = false) String format
    ) throws Exception {
        validatePrintOptions(format, theme, color);
        Long clientFirmId = parseFirmId(firmIdParam);
        Long effFirmId = resolveAuthoritativeFirmId(firmIdHeader != null ? firmIdHeader : clientFirmId);
        byte[] pdf = statementService.generatePartyStatementPdf(effFirmId, partyId, from, to, format, theme, color);

        String dateStr = (from != null || to != null)
                ? (from != null ? from.toString() : "Start") + "_to_" + (to != null ? to.toString() : LocalDate.now().toString())
                : "All_Time";
        String filename = "Party_Statement_" + partyId + "_" + dateStr + ".pdf";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdf);
    }
}
