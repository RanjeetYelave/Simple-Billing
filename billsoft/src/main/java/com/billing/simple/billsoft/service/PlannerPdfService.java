package com.billing.simple.billsoft.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import com.billing.simple.billsoft.entities.Expense;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.SavingRecord;
import com.billing.simple.billsoft.enums.InvoiceColorPalette;
import com.billing.simple.billsoft.enums.InvoiceTheme;
import com.billing.simple.billsoft.enums.PrintFormat;
import com.billing.simple.billsoft.dtos.InvoicePrintOptions;
import com.billing.simple.billsoft.service.pdf.core.DocumentThemeHelper;
import com.billing.simple.billsoft.service.pdf.core.UniversalPageNumberHelper;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

@Service
public class PlannerPdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DecimalFormat CURRENCY_FMT = new DecimalFormat("#,##0.00");

    private String fmt(BigDecimal val) {
        if (val == null) return "0.00";
        return CURRENCY_FMT.format(val);
    }

    private void addKpiBox(PdfPTable table, String label, String value, String sub, Color bgColor, Font lFont, Font vFont, Font sFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bgColor);
        cell.setPadding(6);
        cell.setBorderColor(new Color(226, 232, 240));

        Paragraph pLabel = new Paragraph(label, lFont);
        Paragraph pVal = new Paragraph(value, vFont);
        Paragraph pSub = new Paragraph(sub, sFont);

        cell.addElement(pLabel);
        cell.addElement(pVal);
        cell.addElement(pSub);
        table.addCell(cell);
    }

    public byte[] generateExpenseStatementPdf(
            List<Expense> expenses,
            LocalDate from,
            LocalDate to,
            String categoryFilter,
            FirmDetails firm,
            String size,
            String format,
            String theme,
            String color) throws Exception {

        InvoicePrintOptions options = InvoicePrintOptions.resolve(size, format, theme, color, firm);
        InvoiceTheme themeEnum = options.getTheme();
        InvoiceColorPalette palette = options.getColorPalette();
        Color primaryColor = palette.getPrimaryColor();
        Color lightTint = palette.getLightTintColor();

        Rectangle pageSize = PageSize.A4;
        float scale = 1.0f;
        if (options.getFormat() == PrintFormat.A4_LANDSCAPE) {
            pageSize = new Rectangle(842.0f, 595.0f);
            scale = 1.05f;
        } else if (options.getFormat() == PrintFormat.A5) {
            pageSize = PageSize.A5;
            scale = 0.85f;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(pageSize, 36 * scale, 36 * scale, 36 * scale, 36 * scale);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        writer.setPageEvent(new UniversalPageNumberHelper(8f * scale));
        doc.open();

        Font titleFont = new Font(Font.HELVETICA, 16 * scale, Font.BOLD, primaryColor);
        Font bold = new Font(Font.HELVETICA, 9.5f * scale, Font.BOLD, DocumentThemeHelper.TEXT_DARK);
        Font normal = new Font(Font.HELVETICA, 8.5f * scale, Font.NORMAL, DocumentThemeHelper.TEXT_DARK);
        Font subText = new Font(Font.HELVETICA, 8f * scale, Font.NORMAL, DocumentThemeHelper.TEXT_MUTED);

        if (themeEnum == InvoiceTheme.PROFESSIONAL) {
            doc.add(DocumentThemeHelper.createProfessionalBanner("EXPENSE STATEMENT", primaryColor, scale));
        }

        // Header Table
        PdfPTable headerTable = new PdfPTable(new float[]{60, 40});
        headerTable.setWidthPercentage(100);

        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);

        if (firm != null && firm.getLogoBase64() != null && !firm.getLogoBase64().isBlank()) {
            Image logo = DocumentThemeHelper.loadLogoImage(firm.getLogoBase64(), 90 * scale, 45 * scale);
            if (logo != null) leftCell.addElement(logo);
        }

        String fName = (firm != null && firm.getFirmName() != null && !firm.getFirmName().isBlank())
                ? firm.getFirmName() : "RupeeCRM Business";
        leftCell.addElement(new Paragraph(fName, new Font(Font.HELVETICA, 14 * scale, Font.BOLD, DocumentThemeHelper.TEXT_DARK)));
        if (firm != null) {
            if (firm.getAddressLine1() != null) leftCell.addElement(new Paragraph(firm.getAddressLine1(), subText));
            if (firm.getPhone() != null && !firm.getPhone().isBlank()) leftCell.addElement(new Paragraph("Phone: " + firm.getPhone(), subText));
            if (firm.getGstin() != null && !firm.getGstin().isBlank()) leftCell.addElement(new Paragraph("GSTIN: " + firm.getGstin(), subText));
        }
        headerTable.addCell(leftCell);

        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        if (themeEnum != InvoiceTheme.PROFESSIONAL) {
            Paragraph pTitle = new Paragraph("EXPENSE STATEMENT", titleFont);
            pTitle.setAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(pTitle);
        }

        String periodLabel = (from != null || to != null)
                ? (from != null ? from.format(DATE_FMT) : "Beginning") + " to " + (to != null ? to.format(DATE_FMT) : "Today")
                : "All Historical Records";
        Paragraph pPeriod = new Paragraph("Period: " + periodLabel, normal);
        pPeriod.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(pPeriod);

        if (categoryFilter != null && !categoryFilter.isBlank() && !"ALL".equalsIgnoreCase(categoryFilter)) {
            Paragraph pCat = new Paragraph("Category: " + categoryFilter, subText);
            pCat.setAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(pCat);
        }

        Paragraph pGen = new Paragraph("Generated: " + LocalDate.now().format(DATE_FMT), subText);
        pGen.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(pGen);

        headerTable.addCell(rightCell);
        doc.add(headerTable);
        doc.add(new Paragraph(" "));

        // Calculations & KPI Cards
        BigDecimal totalAmount = BigDecimal.ZERO;
        int count = expenses != null ? expenses.size() : 0;
        if (expenses != null) {
            for (Expense e : expenses) {
                if (e.getAmount() != null) {
                    totalAmount = totalAmount.add(e.getAmount());
                }
            }
        }
        BigDecimal avgAmount = count > 0 ? totalAmount.divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;

        PdfPTable kpiTable = new PdfPTable(3);
        kpiTable.setWidthPercentage(100);
        kpiTable.setSpacingAfter(10 * scale);

        Font kpiValFont = new Font(Font.HELVETICA, 11 * scale, Font.BOLD, primaryColor);
        addKpiBox(kpiTable, "TOTAL EXPENSES", "₹ " + fmt(totalAmount), "Period sum", lightTint, subText, kpiValFont, subText);
        addKpiBox(kpiTable, "RECORD COUNT", String.valueOf(count), "Total vouchers", new Color(240, 249, 255), subText, new Font(Font.HELVETICA, 11 * scale, Font.BOLD, DocumentThemeHelper.TEXT_DARK), subText);
        addKpiBox(kpiTable, "AVERAGE / EXPENSE", "₹ " + fmt(avgAmount), "Mean voucher value", new Color(240, 253, 244), subText, new Font(Font.HELVETICA, 11 * scale, Font.BOLD, DocumentThemeHelper.TEXT_DARK), subText);
        doc.add(kpiTable);

        // Data Table
        PdfPTable table = new PdfPTable(new float[]{14, 22, 34, 15, 15});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);

        Font thFont = new Font(Font.HELVETICA, 8.5f * scale, Font.BOLD, (themeEnum == InvoiceTheme.MINIMAL) ? primaryColor : Color.WHITE);
        String[] headers = {"Date", "Category", "Description / Title", "Payment Mode", "Amount (₹)"};
        for (String h : headers) {
            int align = h.contains("Amount") ? Element.ALIGN_RIGHT : (h.contains("Date") ? Element.ALIGN_CENTER : Element.ALIGN_LEFT);
            table.addCell(DocumentThemeHelper.createHeaderCell(h, themeEnum, primaryColor, thFont, align, scale));
        }

        if (expenses == null || expenses.isEmpty()) {
            PdfPCell emptyCell = new PdfPCell(new Phrase("No expense records found for the selected criteria", normal));
            emptyCell.setColspan(5);
            emptyCell.setPadding(12 * scale);
            emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(emptyCell);
        } else {
            for (int i = 0; i < expenses.size(); i++) {
                Expense e = expenses.get(i);
                boolean isEven = (i % 2 == 1);
                String dateStr = e.getExpenseDate() != null ? e.getExpenseDate().format(DATE_FMT) : "-";
                String catStr = e.getCategory() != null ? e.getCategory() : "General";
                String titleStr = e.getTitle() != null ? e.getTitle() : "-";
                if (e.getNotes() != null && !e.getNotes().isBlank()) {
                    titleStr += "\n(" + e.getNotes() + ")";
                }
                String modeStr = e.getPaymentMode() != null ? e.getPaymentMode() : "-";
                String amtStr = fmt(e.getAmount());

                table.addCell(DocumentThemeHelper.createDataCell(new Phrase(dateStr, normal), themeEnum, isEven, Element.ALIGN_CENTER, scale));
                table.addCell(DocumentThemeHelper.createDataCell(new Phrase(catStr, normal), themeEnum, isEven, Element.ALIGN_LEFT, scale));
                table.addCell(DocumentThemeHelper.createDataCell(new Phrase(titleStr, normal), themeEnum, isEven, Element.ALIGN_LEFT, scale));
                table.addCell(DocumentThemeHelper.createDataCell(new Phrase(modeStr, normal), themeEnum, isEven, Element.ALIGN_LEFT, scale));
                table.addCell(DocumentThemeHelper.createDataCell(new Phrase(amtStr, bold), themeEnum, isEven, Element.ALIGN_RIGHT, scale));
            }

            // Summary row
            PdfPCell totalLabelCell = new PdfPCell(new Phrase("TOTAL EXPENSES", bold));
            totalLabelCell.setColspan(4);
            totalLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalLabelCell.setPadding(6 * scale);
            totalLabelCell.setBackgroundColor(lightTint);
            table.addCell(totalLabelCell);

            PdfPCell totalValCell = new PdfPCell(new Phrase("₹ " + fmt(totalAmount), bold));
            totalValCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalValCell.setPadding(6 * scale);
            totalValCell.setBackgroundColor(lightTint);
            table.addCell(totalValCell);
        }

        doc.add(table);
        doc.close();
        return baos.toByteArray();
    }

    public byte[] generateSavingsLedgerPdf(
            List<SavingRecord> savings,
            LocalDate from,
            LocalDate to,
            String categoryFilter,
            FirmDetails firm,
            String size,
            String format,
            String theme,
            String color) throws Exception {

        InvoicePrintOptions options = InvoicePrintOptions.resolve(size, format, theme, color, firm);
        InvoiceTheme themeEnum = options.getTheme();
        InvoiceColorPalette palette = options.getColorPalette();
        Color primaryColor = palette.getPrimaryColor();
        Color lightTint = palette.getLightTintColor();

        Rectangle pageSize = PageSize.A4;
        float scale = 1.0f;
        if (options.getFormat() == PrintFormat.A4_LANDSCAPE) {
            pageSize = new Rectangle(842.0f, 595.0f);
            scale = 1.05f;
        } else if (options.getFormat() == PrintFormat.A5) {
            pageSize = PageSize.A5;
            scale = 0.85f;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(pageSize, 36 * scale, 36 * scale, 36 * scale, 36 * scale);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        writer.setPageEvent(new UniversalPageNumberHelper(8f * scale));
        doc.open();

        Font titleFont = new Font(Font.HELVETICA, 16 * scale, Font.BOLD, primaryColor);
        Font bold = new Font(Font.HELVETICA, 9.5f * scale, Font.BOLD, DocumentThemeHelper.TEXT_DARK);
        Font normal = new Font(Font.HELVETICA, 8.5f * scale, Font.NORMAL, DocumentThemeHelper.TEXT_DARK);
        Font subText = new Font(Font.HELVETICA, 8f * scale, Font.NORMAL, DocumentThemeHelper.TEXT_MUTED);

        if (themeEnum == InvoiceTheme.PROFESSIONAL) {
            doc.add(DocumentThemeHelper.createProfessionalBanner("SAVINGS & INVESTMENT LEDGER", primaryColor, scale));
        }

        // Header Table
        PdfPTable headerTable = new PdfPTable(new float[]{60, 40});
        headerTable.setWidthPercentage(100);

        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);

        if (firm != null && firm.getLogoBase64() != null && !firm.getLogoBase64().isBlank()) {
            Image logo = DocumentThemeHelper.loadLogoImage(firm.getLogoBase64(), 90 * scale, 45 * scale);
            if (logo != null) leftCell.addElement(logo);
        }

        String fName = (firm != null && firm.getFirmName() != null && !firm.getFirmName().isBlank())
                ? firm.getFirmName() : "RupeeCRM Business";
        leftCell.addElement(new Paragraph(fName, new Font(Font.HELVETICA, 14 * scale, Font.BOLD, DocumentThemeHelper.TEXT_DARK)));
        if (firm != null) {
            if (firm.getAddressLine1() != null) leftCell.addElement(new Paragraph(firm.getAddressLine1(), subText));
            if (firm.getPhone() != null && !firm.getPhone().isBlank()) leftCell.addElement(new Paragraph("Phone: " + firm.getPhone(), subText));
            if (firm.getGstin() != null && !firm.getGstin().isBlank()) leftCell.addElement(new Paragraph("GSTIN: " + firm.getGstin(), subText));
        }
        headerTable.addCell(leftCell);

        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        if (themeEnum != InvoiceTheme.PROFESSIONAL) {
            Paragraph pTitle = new Paragraph("SAVINGS & INVESTMENT LEDGER", titleFont);
            pTitle.setAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(pTitle);
        }

        String periodLabel = (from != null || to != null)
                ? (from != null ? from.format(DATE_FMT) : "Beginning") + " to " + (to != null ? to.format(DATE_FMT) : "Today")
                : "All Historical Records";
        Paragraph pPeriod = new Paragraph("Period: " + periodLabel, normal);
        pPeriod.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(pPeriod);

        if (categoryFilter != null && !categoryFilter.isBlank() && !"ALL".equalsIgnoreCase(categoryFilter)) {
            Paragraph pCat = new Paragraph("Category: " + categoryFilter, subText);
            pCat.setAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(pCat);
        }

        Paragraph pGen = new Paragraph("Generated: " + LocalDate.now().format(DATE_FMT), subText);
        pGen.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(pGen);

        headerTable.addCell(rightCell);
        doc.add(headerTable);
        doc.add(new Paragraph(" "));

        // Calculations & KPI Cards
        BigDecimal totalAmount = BigDecimal.ZERO;
        int count = savings != null ? savings.size() : 0;
        if (savings != null) {
            for (SavingRecord s : savings) {
                if (s.getAmount() != null) {
                    totalAmount = totalAmount.add(s.getAmount());
                }
            }
        }
        BigDecimal avgAmount = count > 0 ? totalAmount.divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;

        PdfPTable kpiTable = new PdfPTable(3);
        kpiTable.setWidthPercentage(100);
        kpiTable.setSpacingAfter(10 * scale);

        Font kpiValFont = new Font(Font.HELVETICA, 11 * scale, Font.BOLD, primaryColor);
        addKpiBox(kpiTable, "TOTAL INVESTED / SAVED", "₹ " + fmt(totalAmount), "Period sum", lightTint, subText, kpiValFont, subText);
        addKpiBox(kpiTable, "CONTRIBUTIONS", String.valueOf(count), "Total deposits", new Color(240, 249, 255), subText, new Font(Font.HELVETICA, 11 * scale, Font.BOLD, DocumentThemeHelper.TEXT_DARK), subText);
        addKpiBox(kpiTable, "AVERAGE DEPOSIT", "₹ " + fmt(avgAmount), "Mean contribution", new Color(240, 253, 244), subText, new Font(Font.HELVETICA, 11 * scale, Font.BOLD, new Color(22, 101, 52)), subText);
        doc.add(kpiTable);

        // Data Table
        PdfPTable table = new PdfPTable(new float[]{14, 22, 34, 15, 15});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);

        Font thFont = new Font(Font.HELVETICA, 8.5f * scale, Font.BOLD, (themeEnum == InvoiceTheme.MINIMAL) ? primaryColor : Color.WHITE);
        String[] headers = {"Date", "Category / Asset", "Goal / Description", "Payment Mode", "Amount (₹)"};
        for (String h : headers) {
            int align = h.contains("Amount") ? Element.ALIGN_RIGHT : (h.contains("Date") ? Element.ALIGN_CENTER : Element.ALIGN_LEFT);
            table.addCell(DocumentThemeHelper.createHeaderCell(h, themeEnum, primaryColor, thFont, align, scale));
        }

        if (savings == null || savings.isEmpty()) {
            PdfPCell emptyCell = new PdfPCell(new Phrase("No savings or investment records found for the selected criteria", normal));
            emptyCell.setColspan(5);
            emptyCell.setPadding(12 * scale);
            emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(emptyCell);
        } else {
            for (int i = 0; i < savings.size(); i++) {
                SavingRecord s = savings.get(i);
                boolean isEven = (i % 2 == 1);
                String dateStr = s.getSavingDate() != null ? s.getSavingDate().format(DATE_FMT) : "-";
                String catStr = s.getCategory() != null ? s.getCategory() : "General Savings";
                String titleStr = s.getTitle() != null ? s.getTitle() : "-";
                if (s.getNotes() != null && !s.getNotes().isBlank()) {
                    titleStr += "\n(" + s.getNotes() + ")";
                }
                String modeStr = s.getPaymentMode() != null ? s.getPaymentMode() : "-";
                String amtStr = fmt(s.getAmount());

                table.addCell(DocumentThemeHelper.createDataCell(new Phrase(dateStr, normal), themeEnum, isEven, Element.ALIGN_CENTER, scale));
                table.addCell(DocumentThemeHelper.createDataCell(new Phrase(catStr, normal), themeEnum, isEven, Element.ALIGN_LEFT, scale));
                table.addCell(DocumentThemeHelper.createDataCell(new Phrase(titleStr, normal), themeEnum, isEven, Element.ALIGN_LEFT, scale));
                table.addCell(DocumentThemeHelper.createDataCell(new Phrase(modeStr, normal), themeEnum, isEven, Element.ALIGN_LEFT, scale));
                table.addCell(DocumentThemeHelper.createDataCell(new Phrase(amtStr, bold), themeEnum, isEven, Element.ALIGN_RIGHT, scale));
            }

            // Summary row
            PdfPCell totalLabelCell = new PdfPCell(new Phrase("TOTAL SAVINGS & INVESTMENTS", bold));
            totalLabelCell.setColspan(4);
            totalLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalLabelCell.setPadding(6 * scale);
            totalLabelCell.setBackgroundColor(lightTint);
            table.addCell(totalLabelCell);

            PdfPCell totalValCell = new PdfPCell(new Phrase("₹ " + fmt(totalAmount), bold));
            totalValCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalValCell.setPadding(6 * scale);
            totalValCell.setBackgroundColor(lightTint);
            table.addCell(totalValCell);
        }

        doc.add(table);
        doc.close();
        return baos.toByteArray();
    }
}
