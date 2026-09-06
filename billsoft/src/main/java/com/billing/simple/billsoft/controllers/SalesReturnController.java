package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.dtos.SalesReturnRequest;
import com.billing.simple.billsoft.entities.SalesReturn;
import com.billing.simple.billsoft.service.InvoiceService;
import com.billing.simple.billsoft.service.SalesReturnPdfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class SalesReturnController {

    private static final Logger log = LoggerFactory.getLogger(SalesReturnController.class);

    private final InvoiceService invoiceService;
    private final SalesReturnPdfService pdfService;

    public SalesReturnController(InvoiceService invoiceService, SalesReturnPdfService pdfService) {
        this.invoiceService = invoiceService;
        this.pdfService = pdfService;
    }

    @PostMapping("/invoices/{id}/returns")
    public ResponseEntity<?> createSalesReturn(
            @PathVariable Long id,
            @RequestBody SalesReturnRequest request
    ) {
        try {
            SalesReturn created = invoiceService.createSalesReturn(id, request);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Invalid sales return request for invoice {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating sales return for invoice " + id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage() != null ? e.getMessage() : "Failed to create sales return"));
        }
    }

    @GetMapping("/invoices/{id}/returns")
    public ResponseEntity<List<SalesReturn>> getReturnsForInvoice(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceService.getSalesReturnsForInvoice(id));
    }

    @GetMapping("/returns")
    public ResponseEntity<List<SalesReturn>> getAllReturns(@RequestParam(required = false) Long firmId) {
        return ResponseEntity.ok(invoiceService.getAllSalesReturns(firmId));
    }

    @GetMapping("/returns/{id}")
    public ResponseEntity<SalesReturn> getReturnById(@PathVariable Long id) {
        SalesReturn ret = invoiceService.getSalesReturnById(id);
        if (ret == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(ret);
    }

    @GetMapping("/returns/{id}/pdf")
    public ResponseEntity<byte[]> getReturnPdf(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "A4") String size
    ) {
        SalesReturn ret = invoiceService.getSalesReturnById(id);
        if (ret == null) return ResponseEntity.notFound().build();

        try {
            byte[] pdfBytes = pdfService.generatePdf(ret, size);
            String filename = "CreditNote-" + ret.getReturnNumber() + ".pdf";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping({"/invoices/next-return-number", "/returns/next-number"})
    public ResponseEntity<Map<String, String>> getNextReturnNumber(@RequestParam(required = false) Long firmId) {
        String nextNo = invoiceService.generateReturnNumber(firmId);
        return ResponseEntity.ok(Map.of("returnNumber", nextNo, "nextNumber", nextNo));
    }
}
