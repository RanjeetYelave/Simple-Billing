package com.billing.simple.billsoft.service.pdf.core;

import java.awt.Color;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.enums.InvoiceColorPalette;
import com.billing.simple.billsoft.enums.InvoiceTheme;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;

public class DocumentThemeHelper {

    public static final Color TEXT_DARK = new Color(17, 24, 39);      // #111827
    public static final Color TEXT_MUTED = new Color(107, 114, 128);  // #6b7280
    public static final Color BORDER_COLOR = new Color(209, 213, 219);// #d1d5db
    public static final Color BORDER_LIGHT = new Color(229, 231, 235);// #e5e7eb
    public static final Color ZEBRA_BG = new Color(249, 250, 251);    // #f9fafb

    public static Image loadLogoImage(String base64Logo, float maxW, float maxH) {
        if (base64Logo == null || base64Logo.trim().isEmpty()) {
            return null;
        }
        try {
            String b64 = base64Logo.trim();
            if (b64.startsWith("data:")) {
                int idx = b64.indexOf("base64,");
                if (idx >= 0) b64 = b64.substring(idx + 7);
            }
            byte[] bytes = Base64.getDecoder().decode(b64);
            Image logo = Image.getInstance(bytes);
            logo.scaleToFit(maxW, maxH);
            return logo;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static PdfPTable createProfessionalBanner(String docTitle, Color primaryColor, float scale) {
        PdfPTable bannerTbl = new PdfPTable(1);
        bannerTbl.setWidthPercentage(100);
        Font bannerTitleFont = new Font(Font.HELVETICA, 13f * scale, Font.BOLD, Color.WHITE);
        PdfPCell bannerCell = new PdfPCell(new Phrase(docTitle != null ? docTitle.toUpperCase() : "OFFICIAL DOCUMENT", bannerTitleFont));
        bannerCell.setBackgroundColor(primaryColor);
        bannerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        bannerCell.setPadding(6f * scale);
        bannerCell.setBorder(Rectangle.NO_BORDER);
        bannerTbl.addCell(bannerCell);
        bannerTbl.setSpacingAfter(8f * scale);
        return bannerTbl;
    }

    public static PdfPCell createHeaderCell(String text, InvoiceTheme theme, Color primaryColor, Font font, int alignment, float scale) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5f * scale);

        if (theme == InvoiceTheme.PROFESSIONAL) {
            cell.setBackgroundColor(primaryColor);
            cell.setBorder(Rectangle.BOX);
            cell.setBorderColor(primaryColor.darker());
        } else if (theme == InvoiceTheme.MODERN) {
            cell.setBackgroundColor(primaryColor);
            cell.setBorder(Rectangle.NO_BORDER);
        } else if (theme == InvoiceTheme.MINIMAL) {
            cell.setBackgroundColor(Color.WHITE);
            cell.setBorder(Rectangle.BOTTOM);
            cell.setBorderColor(primaryColor);
            cell.setBorderWidth(1.2f);
        } else {
            // CLASSIC
            cell.setBackgroundColor(primaryColor);
            cell.setBorder(Rectangle.BOX);
            cell.setBorderColor(BORDER_COLOR);
        }
        return cell;
    }

    public static PdfPCell createDataCell(Phrase phrase, InvoiceTheme theme, boolean isEvenRow, int alignment, float scale) {
        PdfPCell cell = new PdfPCell(phrase);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(4.5f * scale);

        if (theme == InvoiceTheme.MINIMAL) {
            cell.setBorder(Rectangle.BOTTOM);
            cell.setBorderColor(BORDER_LIGHT);
            cell.setBackgroundColor(Color.WHITE);
        } else if (theme == InvoiceTheme.MODERN) {
            cell.setBorder(Rectangle.BOTTOM);
            cell.setBorderColor(BORDER_LIGHT);
            cell.setBackgroundColor(isEvenRow ? ZEBRA_BG : Color.WHITE);
        } else if (theme == InvoiceTheme.PROFESSIONAL) {
            cell.setBorder(Rectangle.BOX);
            cell.setBorderColor(BORDER_COLOR);
            cell.setBackgroundColor(isEvenRow ? ZEBRA_BG : Color.WHITE);
        } else {
            // CLASSIC
            cell.setBorder(Rectangle.BOX);
            cell.setBorderColor(BORDER_COLOR);
            cell.setBackgroundColor(isEvenRow ? ZEBRA_BG : Color.WHITE);
        }
        return cell;
    }

    public static Image generateQrCode(String data, int width, int height) {
        if (data == null || data.trim().isEmpty()) return null;
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 0);
            BitMatrix bitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, width, height, hints);

            int matrixW = bitMatrix.getWidth();
            int matrixH = bitMatrix.getHeight();
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(matrixW, matrixH, java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < matrixW; x++) {
                for (int y = 0; y < matrixH; y++) {
                    img.setRGB(x, y, bitMatrix.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "PNG", baos);
            return Image.getInstance(baos.toByteArray());
        } catch (Exception ignored) {
            return null;
        }
    }
}
