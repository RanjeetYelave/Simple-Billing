package com.billing.simple.billsoft.controllers;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.billing.simple.billsoft.dtos.CustomerStatementResponse;
import com.billing.simple.billsoft.dtos.FirmStatementResponse;
import com.billing.simple.billsoft.service.StatementService;

@RestController
@RequestMapping("/api/statements")
@CrossOrigin
public class StatementsController {

    private final StatementService statementService;

    public StatementsController(StatementService statementService) {
        this.statementService = statementService;
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<CustomerStatementResponse> customerStatement(
            @PathVariable("customerId") Long customerId,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(statementService.getCustomerStatement(customerId, from, to));
    }

    @GetMapping("/customer/{customerId}/pdf")
    public ResponseEntity<byte[]> customerStatementPdf(
            @PathVariable("customerId") Long customerId,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) throws Exception {
        byte[] pdf = statementService.generateCustomerStatementPdf(customerId, from, to);
        String filename = "statement-customer-" + customerId + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/firm")
    public ResponseEntity<FirmStatementResponse> firmStatement(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(statementService.getFirmStatement(from, to));
    }

    @GetMapping("/firm/pdf")
    public ResponseEntity<byte[]> firmStatementPdf(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) throws Exception {
        byte[] pdf = statementService.generateFirmStatementPdf(from, to);
        String filename = "statement-firm.pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
