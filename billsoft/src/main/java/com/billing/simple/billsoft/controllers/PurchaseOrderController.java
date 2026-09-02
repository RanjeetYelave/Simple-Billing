package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.entities.PurchaseOrder;
import com.billing.simple.billsoft.entities.PurchaseOrderStatus;
import com.billing.simple.billsoft.service.PurchaseOrderService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService poService;

    public PurchaseOrderController(PurchaseOrderService poService) {
        this.poService = poService;
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

    @PatchMapping("/{id}/status")
    public ResponseEntity<PurchaseOrder> updateStatus(@PathVariable Long id,
                                                      @RequestBody Map<String, String> body,
                                                      @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
                                                      @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = firmIdHeader != null ? firmIdHeader : firmIdParam;
        if (firmId == null || !body.containsKey("status")) {
            return ResponseEntity.badRequest().build();
        }
        try {
            PurchaseOrderStatus status = PurchaseOrderStatus.valueOf(body.get("status").toUpperCase());
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
        if (firmId == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            poService.deletePurchaseOrder(id, firmId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
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
