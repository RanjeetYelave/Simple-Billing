package com.billing.simple.billsoft.assistant.controllers;

import com.billing.simple.billsoft.assistant.dto.OmnisearchNluDTOs.NluEvaluationResponse;
import com.billing.simple.billsoft.assistant.dto.OmnisearchNluDTOs.NluQueryRequest;
import com.billing.simple.billsoft.assistant.nlu.OmnisearchNluService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authoritative NLU REST API Endpoint.
 * Strictly read-only query evaluation.
 */
@RestController
@RequestMapping({"/api/omnisearch", "/api/omnisearch/nlu"})
public class OmnisearchNluController {

    private final OmnisearchNluService nluService;

    public OmnisearchNluController(OmnisearchNluService nluService) {
        this.nluService = nluService;
    }

    @PostMapping({"/evaluate", "/nlu/evaluate"})
    public ResponseEntity<NluEvaluationResponse> evaluatePost(@RequestBody NluQueryRequest request) {
        String query = request != null ? request.getQuery() : "";
        NluEvaluationResponse response = nluService.evaluateQuery(query);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/qr", produces = org.springframework.http.MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getQrImage(@RequestParam("data") String data,
                                            @RequestParam(value = "size", defaultValue = "260") int size) {
        try {
            com.google.zxing.qrcode.QRCodeWriter qrCodeWriter = new com.google.zxing.qrcode.QRCodeWriter();
            com.google.zxing.common.BitMatrix bitMatrix = qrCodeWriter.encode(data, com.google.zxing.BarcodeFormat.QR_CODE, size, size);
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            com.google.zxing.client.j2se.MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .body(outputStream.toByteArray());
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
