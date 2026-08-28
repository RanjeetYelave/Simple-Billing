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

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<CustomerStatementResponse> customerStatement(
            @RequestParam(name = "firmId", required = false) Long firmId,
            @PathVariable("customerId") Long customerId,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(statementService.getCustomerStatement(firmId, customerId, from, to));
    }

    @GetMapping("/customer/{customerId}/pdf")
    public ResponseEntity<byte[]> customerStatementPdf(
            @RequestParam(name = "firmId", required = false) Long firmId,
            @PathVariable("customerId") Long customerId,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) throws Exception {
        byte[] pdf = statementService.generateCustomerStatementPdf(firmId, customerId, from, to);

        Customer cust = customerRepo.findById(customerId).orElse(null);
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
            @RequestParam(name = "firmId", required = false) Long firmId,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(statementService.getFirmStatement(firmId, from, to));
    }

    @GetMapping("/firm/pdf")
    public ResponseEntity<byte[]> firmStatementPdf(
            @RequestParam(name = "firmId", required = false) Long firmId,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) throws Exception {
        byte[] pdf = statementService.generateFirmStatementPdf(firmId, from, to);

        FirmDetails firm = firmId != null ? firmRepo.findById(firmId).orElse(null) : null;
        if (firm == null) {
            List<FirmDetails> list = firmRepo.findAll();
            if (!list.isEmpty()) firm = list.get(0);
        }
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
}
