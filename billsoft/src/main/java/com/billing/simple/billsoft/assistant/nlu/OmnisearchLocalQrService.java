package com.billing.simple.billsoft.assistant.nlu;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 100% Offline in-process QR Code Generator using bundled ZXing (3.5.3).
 * Eliminates external cloud dependencies (api.qrserver.com).
 */
@Service
public class OmnisearchLocalQrService {

    public String generateUpiQrBase64(String payeeVpa, String payeeName, BigDecimal amount, String note) {
        String safeVpa = (payeeVpa != null && !payeeVpa.isBlank()) ? payeeVpa : "merchant@upi";
        String safeName = (payeeName != null && !payeeName.isBlank()) ? payeeName : "RupeeCRM Merchant";
        String safeNote = (note != null && !note.isBlank()) ? note : "Payment via RupeeCRM";

        StringBuilder upiUri = new StringBuilder("upi://pay?");
        upiUri.append("pa=").append(URLEncoder.encode(safeVpa, StandardCharsets.UTF_8));
        upiUri.append("&pn=").append(URLEncoder.encode(safeName, StandardCharsets.UTF_8));
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            upiUri.append("&am=").append(amount.setScale(2).toPlainString());
        }
        upiUri.append("&cu=INR");
        upiUri.append("&tn=").append(URLEncoder.encode(safeNote, StandardCharsets.UTF_8));

        return generateQrBase64(upiUri.toString(), 280, 280);
    }

    public String generateQrBase64(String content, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            byte[] pngData = outputStream.toByteArray();

            return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngData);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate offline QR code", e);
        }
    }
}
