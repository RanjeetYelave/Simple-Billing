package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.entities.PurchaseOrder;
import com.billing.simple.billsoft.entities.PurchaseOrderStatus;
import com.billing.simple.billsoft.service.PurchaseOrderService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService poService;
    private final com.billing.simple.billsoft.service.PartyService partyService;

    public PurchaseOrderController(PurchaseOrderService poService,
                                   com.billing.simple.billsoft.service.PartyService partyService) {
        this.poService = poService;
        this.partyService = partyService;
    }

    @GetMapping
    public ResponseEntity<List<PurchaseOrder>> list(@RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
                                                    @RequestParam(value = "firmId", required = false) Long firmIdParam,
                                                    @RequestParam(value = "partyId", required = false) Long partyId) {
        Long firmId = firmIdHeader != null ? firmIdHeader : firmIdParam;
        if (firmId == null) {
            return ResponseEntity.badRequest().build();
        }
        if (partyId != null) {
            return ResponseEntity.ok(poService.getPurchaseOrdersByParty(firmId, partyId));
        }
        return ResponseEntity.ok(poService.getPurchaseOrdersByFirm(firmId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PurchaseOrder> getById(@PathVariable Long id,
                                                 @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
                                                 @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = firmIdHeader != null ? firmIdHeader : firmIdParam;
        return poService.getPurchaseOrderById(id, firmId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/next-number")
    public ResponseEntity<Map<String, String>> getNextNumber(@RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
                                                             @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = firmIdHeader != null ? firmIdHeader : firmIdParam;
        if (firmId == null) {
            return ResponseEntity.badRequest().build();
        }
        String nextNo = poService.generateNextPoNumber(firmId);
        return ResponseEntity.ok(Map.of("nextNumber", nextNo));
    }

    @PostMapping
    public ResponseEntity<PurchaseOrder> create(@RequestBody PurchaseOrder po,
                                                @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        if (po.getFirmId() == null && firmIdHeader != null) {
            po.setFirmId(firmIdHeader);
        }
        if (po.getFirmId() == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            return ResponseEntity.ok(poService.createPurchaseOrder(po));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<PurchaseOrder> update(@PathVariable Long id,
                                                @RequestBody PurchaseOrder po,
                                                @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader) {
        if (po.getFirmId() == null && firmIdHeader != null) {
            po.setFirmId(firmIdHeader);
        }
        try {
            return ResponseEntity.ok(poService.updatePurchaseOrder(id, po));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @RequestMapping(value = "/{id}/status", method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ResponseEntity<PurchaseOrder> updateStatus(@PathVariable Long id,
                                                      @RequestBody(required = false) Map<String, String> body,
                                                      @RequestParam(value = "status", required = false) String statusParam,
                                                      @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
                                                      @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = firmIdHeader != null ? firmIdHeader : firmIdParam;
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
            PurchaseOrderStatus status = PurchaseOrderStatus.valueOf(statusStr.trim().toUpperCase());
            return ResponseEntity.ok(poService.updateStatus(id, firmId, status));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
                                       @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = firmIdHeader != null ? firmIdHeader : firmIdParam;
        try {
            poService.deletePurchaseOrder(id, firmId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<PurchaseOrder> recordPayment(@PathVariable Long id,
                                                      @RequestBody Map<String, Object> body,
                                                      @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
                                                      @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = firmIdHeader != null ? firmIdHeader : firmIdParam;
        if (body == null || !body.containsKey("amount")) {
            return ResponseEntity.badRequest().build();
        }

        try {
            BigDecimal amount = new BigDecimal(body.get("amount").toString());
            LocalDate paymentDate = body.containsKey("paymentDate") && body.get("paymentDate") != null
                    ? LocalDate.parse(body.get("paymentDate").toString())
                    : LocalDate.now();
            String paymentMode = body.containsKey("paymentMode") && body.get("paymentMode") != null
                    ? body.get("paymentMode").toString()
                    : "Bank Transfer";
            String referenceNumber = body.containsKey("referenceNumber") && body.get("referenceNumber") != null
                    ? body.get("referenceNumber").toString()
                    : null;
            String notes = body.containsKey("notes") && body.get("notes") != null
                    ? body.get("notes").toString()
                    : null;

            PurchaseOrder updated = poService.recordPoPayment(id, firmId, amount, paymentDate, paymentMode, referenceNumber, notes);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/adjust-advance")
    public ResponseEntity<PurchaseOrder> adjustAdvance(@PathVariable Long id,
                                                       @RequestBody Map<String, Object> body,
                                                       @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
                                                       @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = firmIdHeader != null ? firmIdHeader : firmIdParam;
        if (firmId == null || body == null || !body.containsKey("paymentId") || !body.containsKey("amount")) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Long paymentId = Long.parseLong(body.get("paymentId").toString());
            BigDecimal amount = new BigDecimal(body.get("amount").toString());
            String notes = body.containsKey("notes") && body.get("notes") != null ? body.get("notes").toString() : null;

            PurchaseOrder updated = partyService.adjustAdvancePayment(id, paymentId, amount, notes, firmId);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/payments/{paymentId}/unadjust")
    public ResponseEntity<PurchaseOrder> unadjustPayment(@PathVariable Long paymentId,
                                                         @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
                                                         @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = firmIdHeader != null ? firmIdHeader : firmIdParam;
        if (firmId == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            PurchaseOrder updated = partyService.unadjustPayment(paymentId, firmId);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id,
                                              @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
                                              @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = firmIdHeader != null ? firmIdHeader : firmIdParam;
        try {
            byte[] pdfBytes = poService.generatePoPdf(id, firmId);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"PO-" + id + ".pdf\"");
            return ResponseEntity.ok().headers(headers).body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
