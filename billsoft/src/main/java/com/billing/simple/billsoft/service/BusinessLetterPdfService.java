package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.BusinessLetter;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Service
public class BusinessLetterPdfService {

    private final FirmDetailsService firmService;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private static final Color THEME_PRIMARY = new Color(79, 70, 229); // #4f46e5
    private static final Color TEXT_DARK = new Color(17, 24, 39);      // #111827
    private static final Color TEXT_MUTED = new Color(107, 114, 128);  // #6b7280

    public BusinessLetterPdfService(FirmDetailsService firmService) {
        this.firmService = firmService;
    }

    public byte[] generatePdf(BusinessLetter letter) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        Font firmTitleFont = new Font(Font.HELVETICA, 16, Font.BOLD, THEME_PRIMARY);
        Font firmSubFont = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, TEXT_MUTED);
        Font boldFont = new Font(Font.HELVETICA, 10, Font.BOLD, TEXT_DARK);
        Font normalFont = new Font(Font.HELVETICA, 9.5f, Font.NORMAL, TEXT_DARK);
        Font subjectFont = new Font(Font.HELVETICA, 10.5f, Font.BOLD, TEXT_DARK);
        Font smallFont = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, TEXT_MUTED);

        boolean isCustomSender = "CUSTOM".equalsIgnoreCase(letter.getSenderType());
        FirmDetails firm = null;

        if (!isCustomSender) {
            try {
                if (letter.getFirmId() != null) {
                    firm = firmService.get(letter.getFirmId());
                }
                if (firm == null) {
                    firm = firmService.getFirst();
                }
            } catch (Exception ignored) {}
        }

        String senderDisplayName;
        if (isCustomSender) {
            senderDisplayName = (letter.getSenderCompany() != null && !letter.getSenderCompany().trim().isEmpty())
                    ? letter.getSenderCompany().trim()
                    : ((letter.getSenderName() != null && !letter.getSenderName().trim().isEmpty()) ? letter.getSenderName().trim() : "Official Correspondence");
        } else {
            senderDisplayName = (firm != null && firm.getFirmName() != null && !firm.getFirmName().trim().isEmpty())
                    ? firm.getFirmName()
                    : "Official Correspondence";
        }

        // 1. Official Letterhead Header
        if (letter.getIncludeHeader() == null || letter.getIncludeHeader()) {
            PdfPTable headerTable = new PdfPTable(new float[]{65, 35});
            headerTable.setWidthPercentage(100);

            PdfPCell leftCell = new PdfPCell();
            leftCell.setBorder(Rectangle.NO_BORDER);

            // Optional Logo (for firm sender)
            if (!isCustomSender && firm != null && firm.getLogoBase64() != null && !firm.getLogoBase64().trim().isEmpty()) {
                try {
                    String cleanBase64 = firm.getLogoBase64();
                    if (cleanBase64.contains(",")) cleanBase64 = cleanBase64.split(",")[1];
                    byte[] imgBytes = Base64.getDecoder().decode(cleanBase64);
                    Image logo = Image.getInstance(imgBytes);
                    logo.scaleToFit(90, 45);
                    leftCell.addElement(logo);
                } catch (Exception ignored) {}
            }

            leftCell.addElement(new Paragraph(senderDisplayName, firmTitleFont));

            if (isCustomSender) {
                if (letter.getSenderName() != null && !letter.getSenderName().trim().isEmpty() && !letter.getSenderName().equals(senderDisplayName)) {
                    leftCell.addElement(new Paragraph("From: " + letter.getSenderName(), boldFont));
                }
                if (letter.getSenderAddress() != null && !letter.getSenderAddress().trim().isEmpty()) {
                    leftCell.addElement(new Paragraph(letter.getSenderAddress(), firmSubFont));
                }
            } else if (firm != null) {
                String address = "";
                if (firm.getAddressLine1() != null) address += firm.getAddressLine1();
                if (firm.getCity() != null) address += ", " + firm.getCity();
                if (firm.getState() != null) address += ", " + firm.getState();
                if (firm.getPincode() != null) address += " - " + firm.getPincode();
                if (!address.isEmpty()) leftCell.addElement(new Paragraph(address, firmSubFont));
            }
            headerTable.addCell(leftCell);

            PdfPCell rightCell = new PdfPCell();
            rightCell.setBorder(Rectangle.NO_BORDER);
            rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

            if (isCustomSender) {
                if (letter.getSenderGstin() != null && !letter.getSenderGstin().trim().isEmpty()) {
                    Paragraph pGst = new Paragraph("GSTIN: " + letter.getSenderGstin(), boldFont);
                    pGst.setAlignment(Element.ALIGN_RIGHT);
                    rightCell.addElement(pGst);
                }
                if (letter.getSenderPhone() != null && !letter.getSenderPhone().trim().isEmpty()) {
                    Paragraph pPhone = new Paragraph("Phone: " + letter.getSenderPhone(), firmSubFont);
                    pPhone.setAlignment(Element.ALIGN_RIGHT);
                    rightCell.addElement(pPhone);
                }
                if (letter.getSenderEmail() != null && !letter.getSenderEmail().trim().isEmpty()) {
                    Paragraph pEmail = new Paragraph("Email: " + letter.getSenderEmail(), firmSubFont);
                    pEmail.setAlignment(Element.ALIGN_RIGHT);
                    rightCell.addElement(pEmail);
                }
            } else if (firm != null) {
                if (firm.getGstin() != null && !firm.getGstin().isEmpty()) {
                    Paragraph pGst = new Paragraph("GSTIN: " + firm.getGstin(), boldFont);
                    pGst.setAlignment(Element.ALIGN_RIGHT);
                    rightCell.addElement(pGst);
                }
                if (firm.getPhone() != null && !firm.getPhone().isEmpty()) {
                    Paragraph pPhone = new Paragraph("Phone: " + firm.getPhone(), firmSubFont);
                    pPhone.setAlignment(Element.ALIGN_RIGHT);
                    rightCell.addElement(pPhone);
                }
                if (firm.getEmail() != null && !firm.getEmail().isEmpty()) {
                    Paragraph pEmail = new Paragraph("Email: " + firm.getEmail(), firmSubFont);
                    pEmail.setAlignment(Element.ALIGN_RIGHT);
                    rightCell.addElement(pEmail);
                }
            }
            headerTable.addCell(rightCell);
            doc.add(headerTable);

            // Dividing Line
            PdfPTable divTable = new PdfPTable(1);
            divTable.setWidthPercentage(100);
            divTable.setSpacingBefore(8f);
            divTable.setSpacingAfter(14f);
            PdfPCell divCell = new PdfPCell();
            divCell.setBorder(Rectangle.TOP);
            divCell.setBorderWidth(1.5f);
            divCell.setBorderColor(THEME_PRIMARY);
            divCell.setFixedHeight(2f);
            divTable.addCell(divCell);
            doc.add(divTable);
        } else {
            doc.add(new Paragraph("\n\n"));
        }

        // 2. Reference Number & Date Line
        PdfPTable refTable = new PdfPTable(new float[]{50, 50});
        refTable.setWidthPercentage(100);
        refTable.setSpacingAfter(14f);

        String letterNo = letter.getLetterNumber() != null ? letter.getLetterNumber() : "LTR-XXXX";
        PdfPCell refCell = new PdfPCell(new Phrase("Ref No: " + letterNo, boldFont));
        refCell.setBorder(Rectangle.NO_BORDER);
        refTable.addCell(refCell);

        String dateStr = letter.getLetterDate() != null ? letter.getLetterDate().format(DATE_FORMATTER) : LocalDate.now().format(DATE_FORMATTER);
        PdfPCell dateCell = new PdfPCell(new Phrase("Date: " + dateStr, boldFont));
        dateCell.setBorder(Rectangle.NO_BORDER);
        dateCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        refTable.addCell(dateCell);
        doc.add(refTable);

        // 3. Recipient "To:" Block
        Paragraph toP = new Paragraph("To,", boldFont);
        toP.setSpacingAfter(3f);
        doc.add(toP);

        if (letter.getRecipientName() != null && !letter.getRecipientName().isEmpty()) {
            doc.add(new Paragraph(letter.getRecipientName(), boldFont));
        }
        if (letter.getRecipientDesignation() != null && !letter.getRecipientDesignation().isEmpty()) {
            doc.add(new Paragraph(letter.getRecipientDesignation(), normalFont));
        }
        if (letter.getRecipientCompany() != null && !letter.getRecipientCompany().isEmpty()) {
            doc.add(new Paragraph(letter.getRecipientCompany(), boldFont));
        }
        if (letter.getRecipientAddress() != null && !letter.getRecipientAddress().isEmpty()) {
            doc.add(new Paragraph(letter.getRecipientAddress(), normalFont));
        }
        if (letter.getRecipientPhone() != null && !letter.getRecipientPhone().isEmpty()) {
            doc.add(new Paragraph("Phone: " + letter.getRecipientPhone(), smallFont));
        }
        if (letter.getRecipientEmail() != null && !letter.getRecipientEmail().isEmpty()) {
            doc.add(new Paragraph("Email: " + letter.getRecipientEmail(), smallFont));
        }

        doc.add(new Paragraph("\n"));

        // 4. Subject Line
        if (letter.getSubject() != null && !letter.getSubject().isEmpty()) {
            Paragraph subjP = new Paragraph("Subject: " + letter.getSubject(), subjectFont);
            subjP.setSpacingAfter(12f);
            doc.add(subjP);
        }

        // 5. Letter Body / Content
        String content = letter.getContent() != null ? letter.getContent() : "";
        String[] paragraphs = content.split("\n\n|\r\n\r\n|\n");
        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (!trimmed.isEmpty()) {
                Paragraph p = new Paragraph(trimmed, normalFont);
                p.setLeading(14f);
                p.setSpacingAfter(10f);
                doc.add(p);
            }
        }

        doc.add(new Paragraph("\n"));

        // 6. Signatory Closing Footer
        if (letter.getIncludeFooter() == null || letter.getIncludeFooter()) {
            PdfPTable signTable = new PdfPTable(new float[]{60, 40});
            signTable.setWidthPercentage(100);
            signTable.setSpacingBefore(16f);

            PdfPCell emptyC = new PdfPCell();
            emptyC.setBorder(Rectangle.NO_BORDER);
            signTable.addCell(emptyC);

            PdfPCell signCell = new PdfPCell();
            signCell.setBorder(Rectangle.NO_BORDER);
            signCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            signCell.addElement(new Paragraph("Yours faithfully,", normalFont));
            signCell.addElement(new Paragraph("For " + senderDisplayName, boldFont));

            // Space for physical signature
            signCell.addElement(new Paragraph("\n\n\n"));

            String sigName = letter.getSignatoryName() != null && !letter.getSignatoryName().isEmpty()
                    ? letter.getSignatoryName()
                    : "Authorized Signatory";
            signCell.addElement(new Paragraph(sigName, boldFont));

            if (letter.getSignatoryDesignation() != null && !letter.getSignatoryDesignation().isEmpty()) {
                signCell.addElement(new Paragraph(letter.getSignatoryDesignation(), smallFont));
            }
            signTable.addCell(signCell);
            doc.add(signTable);
        }

        doc.close();
        return baos.toByteArray();
    }
}
