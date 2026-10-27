package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.InvoicePrintOptions;
import com.billing.simple.billsoft.entities.Employee;
import com.billing.simple.billsoft.entities.EmployeeAdvance;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.SalaryRecord;
import com.billing.simple.billsoft.enums.InvoiceColorPalette;
import com.billing.simple.billsoft.enums.InvoiceTheme;
import com.billing.simple.billsoft.enums.PrintFormat;
import com.billing.simple.billsoft.service.pdf.core.DocumentThemeHelper;
import com.billing.simple.billsoft.service.pdf.core.UniversalPageNumberHelper;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class EmployeePdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private final FirmDetailsService firmService;

    public EmployeePdfService(FirmDetailsService firmService) {
        this.firmService = firmService;
    }

    /**
     * Generate a payslip PDF for a given salary record, including YTD summary.
     */
    public byte[] generatePayslip(Employee emp, SalaryRecord salary, FirmDetails firm, List<SalaryRecord> allEmpSalaries) throws Exception {
        return generatePayslip(emp, salary, firm, allEmpSalaries, null, null, null, null);
    }

    public byte[] generatePayslip(Employee emp, SalaryRecord salary, FirmDetails firm, List<SalaryRecord> allEmpSalaries,
                                  String size, String format, String theme, String color) throws Exception {
        InvoicePrintOptions options = InvoicePrintOptions.resolve(size, format, theme, color, firm);

        if (options.getFormat() == PrintFormat.THERMAL_80MM) {
            return generateThermalPayslip(emp, salary, firm);
        }

        InvoiceTheme themeEnum = options.getTheme();
        InvoiceColorPalette palette = options.getColorPalette();
        Color primaryColor = palette.getPrimaryColor();
        Color lightTint = palette.getLightTintColor();

        Rectangle pageSize = (options.getFormat() == PrintFormat.A5) ? PageSize.A5 : PageSize.A4;
        float scale = (options.getFormat() == PrintFormat.A5) ? 0.85f : 1.0f;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(pageSize, 36 * scale, 36 * scale, 36 * scale, 36 * scale);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        writer.setPageEvent(new UniversalPageNumberHelper(8f * scale));
        doc.open();

        Font titleFont = new Font(Font.HELVETICA, 16 * scale, Font.BOLD, primaryColor);
        Font hFont = new Font(Font.HELVETICA, 10.5f * scale, Font.BOLD, DocumentThemeHelper.TEXT_DARK);
        Font normal = new Font(Font.HELVETICA, 9.5f * scale, Font.NORMAL, DocumentThemeHelper.TEXT_DARK);
        Font bold = new Font(Font.HELVETICA, 9.5f * scale, Font.BOLD, DocumentThemeHelper.TEXT_DARK);
        Font small = new Font(Font.HELVETICA, 8.5f * scale, Font.NORMAL, DocumentThemeHelper.TEXT_MUTED);

        if (themeEnum == InvoiceTheme.PROFESSIONAL) {
            doc.add(DocumentThemeHelper.createProfessionalBanner("EMPLOYEE SALARY SLIP", primaryColor, scale));
        }

        // --- Header: Company Info ---
        PdfPTable header = new PdfPTable(new float[]{2f, 1.6f});
        header.setWidthPercentage(100);
        header.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);

        // Logo
        if (firm != null && firm.getLogoBase64() != null && !firm.getLogoBase64().isBlank()) {
            Image logo = DocumentThemeHelper.loadLogoImage(firm.getLogoBase64(), 100f * scale, 55f * scale);
            if (logo != null) left.addElement(logo);
        }

        String firmName = (firm != null && firm.getFirmName() != null && !firm.getFirmName().isBlank())
                ? firm.getFirmName() : "Firm Name";
        left.addElement(new Paragraph(firmName, titleFont));
        if (firm != null) {
            if (firm.getAddressLine1() != null && !firm.getAddressLine1().isBlank())
                left.addElement(new Paragraph(firm.getAddressLine1(), small));
            StringBuilder cityLine = new StringBuilder();
            if (firm.getCity() != null && !firm.getCity().isBlank()) cityLine.append(firm.getCity());
            if (firm.getState() != null && !firm.getState().isBlank()) {
                if (cityLine.length() > 0) cityLine.append(" - ");
                cityLine.append(firm.getState());
            }
            if (firm.getPincode() != null && !firm.getPincode().isBlank()) {
                if (cityLine.length() > 0) cityLine.append(" ");
                cityLine.append(firm.getPincode());
            }
            if (cityLine.length() > 0) left.addElement(new Paragraph(cityLine.toString(), small));
        }
        header.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        if (themeEnum != InvoiceTheme.PROFESSIONAL) {
            Paragraph payslipTitle = new Paragraph("PAYSLIP", titleFont);
            payslipTitle.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(payslipTitle);
        }
        Paragraph pMonth = new Paragraph("Month: " + salary.getMonthYear(), bold);
        pMonth.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(pMonth);

        Paragraph pDate = new Paragraph("Date: " + (salary.getPaymentDate() != null ? salary.getPaymentDate().format(DATE_FMT) : "-"), small);
        pDate.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(pDate);
        header.addCell(right);

        doc.add(header);
        doc.add(new Paragraph(" "));

        // --- Employee Details Card ---
        PdfPTable empSection = new PdfPTable(new float[]{1f, 1f});
        empSection.setWidthPercentage(100);
        empSection.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        PdfPCell empLeft = new PdfPCell();
        empLeft.setBorder(Rectangle.NO_BORDER);
        empLeft.addElement(new Paragraph("Employee Details", hFont));
        empLeft.addElement(new Paragraph("Name: " + emp.getName(), normal));
        if (emp.getRole() != null && !emp.getRole().isBlank())
            empLeft.addElement(new Paragraph("Role: " + emp.getRole(), normal));
        if (emp.getPhone() != null && !emp.getPhone().isBlank())
            empLeft.addElement(new Paragraph("Phone: " + emp.getPhone(), normal));
        empSection.addCell(empLeft);

        PdfPCell empRight = new PdfPCell();
        empRight.setBorder(Rectangle.NO_BORDER);
        empRight.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph empIdP = new Paragraph("Employee ID: " + emp.getId(), normal);
        empIdP.setAlignment(Element.ALIGN_RIGHT);
        empRight.addElement(empIdP);
        if (emp.getDateOfJoining() != null) {
            Paragraph dojP = new Paragraph("DOJ: " + emp.getDateOfJoining().format(DATE_FMT), normal);
            dojP.setAlignment(Element.ALIGN_RIGHT);
            empRight.addElement(dojP);
        }
        empSection.addCell(empRight);

        doc.add(empSection);
        doc.add(new Paragraph(" "));

        // --- Earnings & Deductions Table ---
        PdfPTable details = new PdfPTable(new float[]{3f, 1.2f, 1.2f});
        details.setWidthPercentage(100);
        details.setHeaderRows(1);

        Font thFont = new Font(Font.HELVETICA, 9 * scale, Font.BOLD, (themeEnum == InvoiceTheme.MINIMAL) ? primaryColor : Color.WHITE);
        String[] detailHeads = {"Description", "Days/Units", "Amount (₹)"};
        for (String h : detailHeads) {
            int align = h.contains("Amount") ? Element.ALIGN_RIGHT : (h.contains("Days") ? Element.ALIGN_CENTER : Element.ALIGN_LEFT);
            details.addCell(DocumentThemeHelper.createHeaderCell(h, themeEnum, primaryColor, thFont, align, scale));
        }

        int totalDays = 30;
        String my = salary.getMonthYear();
        int targetYear = LocalDate.now().getYear();
        if (my != null && my.contains("-")) {
            try {
                String[] parts = my.split("-");
                if (parts.length == 2) {
                    int mo = Integer.parseInt(parts[0].trim());
                    targetYear = Integer.parseInt(parts[1].trim());
                    java.time.YearMonth ym = java.time.YearMonth.of(targetYear, mo);
                    totalDays = ym.lengthOfMonth();
                }
            } catch (Exception ignored) {}
        }
        int workingDays = totalDays - (salary.getDaysAbsent() != null ? salary.getDaysAbsent() : 0);

        boolean rowAlt = false;
        details.addCell(DocumentThemeHelper.createDataCell(new Phrase("Base Salary", normal), themeEnum, rowAlt, Element.ALIGN_LEFT, scale));
        details.addCell(DocumentThemeHelper.createDataCell(new Phrase(workingDays + "/" + totalDays, normal), themeEnum, rowAlt, Element.ALIGN_CENTER, scale));
        details.addCell(DocumentThemeHelper.createDataCell(new Phrase(formatAmount(salary.getBaseSalaryAtTime()), normal), themeEnum, rowAlt, Element.ALIGN_RIGHT, scale));

        if (salary.getBonusAmount() != null && salary.getBonusAmount() > 0) {
            rowAlt = !rowAlt;
            details.addCell(DocumentThemeHelper.createDataCell(new Phrase("Bonus / Overtime", normal), themeEnum, rowAlt, Element.ALIGN_LEFT, scale));
            details.addCell(DocumentThemeHelper.createDataCell(new Phrase("-", normal), themeEnum, rowAlt, Element.ALIGN_CENTER, scale));
            details.addCell(DocumentThemeHelper.createDataCell(new Phrase(formatAmount(salary.getBonusAmount()), normal), themeEnum, rowAlt, Element.ALIGN_RIGHT, scale));
        }

        if (salary.getLeaveDeductionAmount() != null && salary.getLeaveDeductionAmount() > 0) {
            rowAlt = !rowAlt;
            details.addCell(DocumentThemeHelper.createDataCell(new Phrase("Leave Deduction (" + (salary.getUnpaidLeaves() != null ? salary.getUnpaidLeaves() : 0) + " days)", normal), themeEnum, rowAlt, Element.ALIGN_LEFT, scale));
            details.addCell(DocumentThemeHelper.createDataCell(new Phrase(String.valueOf(salary.getUnpaidLeaves()), normal), themeEnum, rowAlt, Element.ALIGN_CENTER, scale));
            details.addCell(DocumentThemeHelper.createDataCell(new Phrase("- " + formatAmount(salary.getLeaveDeductionAmount()), normal), themeEnum, rowAlt, Element.ALIGN_RIGHT, scale));
        }

        if (salary.getAdvanceDeducted() != null && salary.getAdvanceDeducted() > 0) {
            rowAlt = !rowAlt;
            details.addCell(DocumentThemeHelper.createDataCell(new Phrase("Advance Deduction", normal), themeEnum, rowAlt, Element.ALIGN_LEFT, scale));
            details.addCell(DocumentThemeHelper.createDataCell(new Phrase("-", normal), themeEnum, rowAlt, Element.ALIGN_CENTER, scale));
            details.addCell(DocumentThemeHelper.createDataCell(new Phrase("- " + formatAmount(salary.getAdvanceDeducted()), normal), themeEnum, rowAlt, Element.ALIGN_RIGHT, scale));
        }

        // Net Paid Highlight Row
        PdfPCell netLabel = new PdfPCell(new Phrase("NET PAID", bold));
        netLabel.setPadding(8 * scale);
        netLabel.setBackgroundColor(lightTint);
        netLabel.setBorderColor(DocumentThemeHelper.BORDER_LIGHT);
        details.addCell(netLabel);

        PdfPCell netDays = new PdfPCell(new Phrase("", bold));
        netDays.setBackgroundColor(lightTint);
        netDays.setBorderColor(DocumentThemeHelper.BORDER_LIGHT);
        details.addCell(netDays);

        PdfPCell netAmt = new PdfPCell(new Phrase("₹ " + formatAmount(salary.getNetPaid()), new Font(Font.HELVETICA, 11 * scale, Font.BOLD, primaryColor)));
        netAmt.setPadding(8 * scale);
        netAmt.setBackgroundColor(lightTint);
        netAmt.setBorderColor(DocumentThemeHelper.BORDER_LIGHT);
        netAmt.setHorizontalAlignment(Element.ALIGN_RIGHT);
        details.addCell(netAmt);

        doc.add(details);
        doc.add(new Paragraph(" "));

        // In Words
        doc.add(new Paragraph("Amount in words: " + numberToWords(salary.getNetPaid()), small));
        doc.add(new Paragraph(" "));

        // --- YTD Summary Table ---
        double ytdGross = 0;
        double ytdDeductions = 0;
        double ytdNet = 0;
        int ytdCount = 0;

        if (allEmpSalaries != null) {
            for (SalaryRecord s : allEmpSalaries) {
                String sMy = s.getMonthYear();
                if (sMy != null && sMy.contains("-")) {
                    try {
                        String[] p = sMy.split("-");
                        if (p.length == 2 && Integer.parseInt(p[1].trim()) == targetYear) {
                            ytdGross += (s.getBaseSalaryAtTime() != null ? s.getBaseSalaryAtTime() : 0) + (s.getBonusAmount() != null ? s.getBonusAmount() : 0);
                            ytdDeductions += (s.getLeaveDeductionAmount() != null ? s.getLeaveDeductionAmount() : 0) + (s.getAdvanceDeducted() != null ? s.getAdvanceDeducted() : 0);
                            ytdNet += (s.getNetPaid() != null ? s.getNetPaid() : 0);
                            ytdCount++;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        doc.add(new Paragraph("Year-to-Date (YTD) Financial Summary (" + targetYear + ")", hFont));
        PdfPTable ytdTable = new PdfPTable(new float[]{2f, 1.5f, 1.5f, 1.5f});
        ytdTable.setWidthPercentage(100);
        ytdTable.setHeaderRows(1);

        String[] ytdHeads = {"Period", "YTD Gross Earnings", "YTD Deductions", "YTD Net Paid"};
        for (String h : ytdHeads) {
            ytdTable.addCell(DocumentThemeHelper.createHeaderCell(h, themeEnum, primaryColor, thFont, Element.ALIGN_CENTER, scale));
        }

        ytdTable.addCell(DocumentThemeHelper.createDataCell(new Phrase("Jan - " + salary.getMonthYear() + " (" + ytdCount + " mos)", normal), themeEnum, false, Element.ALIGN_CENTER, scale));
        ytdTable.addCell(DocumentThemeHelper.createDataCell(new Phrase("₹ " + formatAmount(ytdGross), normal), themeEnum, false, Element.ALIGN_RIGHT, scale));
        ytdTable.addCell(DocumentThemeHelper.createDataCell(new Phrase("₹ " + formatAmount(ytdDeductions), normal), themeEnum, false, Element.ALIGN_RIGHT, scale));
        ytdTable.addCell(DocumentThemeHelper.createDataCell(new Phrase("₹ " + formatAmount(ytdNet), bold), themeEnum, false, Element.ALIGN_RIGHT, scale));

        doc.add(ytdTable);
        doc.add(new Paragraph(" "));

        // Footer signature
        PdfPTable sig = new PdfPTable(new float[]{1f, 1f});
        sig.setWidthPercentage(100);
        PdfPCell leftSig = new PdfPCell(new Phrase("\n\nFor " + firmName, normal));
        leftSig.setBorder(Rectangle.NO_BORDER);
        sig.addCell(leftSig);
        PdfPCell rightSig = new PdfPCell(new Phrase("\n\nAuthorised Signatory", normal));
        rightSig.setBorder(Rectangle.NO_BORDER);
        rightSig.setHorizontalAlignment(Element.ALIGN_RIGHT);
        sig.addCell(rightSig);
        doc.add(sig);

        doc.close();
        return baos.toByteArray();
    }

    private byte[] generateThermalPayslip(Employee emp, SalaryRecord salary, FirmDetails firm) throws Exception {
        float calculatedHeight = 280f;
        if (salary.getBonusAmount() != null && salary.getBonusAmount() > 0) calculatedHeight += 16f;
        if (salary.getLeaveDeductionAmount() != null && salary.getLeaveDeductionAmount() > 0) calculatedHeight += 16f;
        if (salary.getAdvanceDeducted() != null && salary.getAdvanceDeducted() > 0) calculatedHeight += 16f;
        Rectangle pageSize = new Rectangle(226.77f, calculatedHeight);
        Document doc = new Document(pageSize, 8, 8, 8, 8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, baos);
        doc.open();

        Font bold = new Font(Font.HELVETICA, 8.5f, Font.BOLD);
        Font normal = new Font(Font.HELVETICA, 7.5f, Font.NORMAL);
        Font title = new Font(Font.HELVETICA, 10f, Font.BOLD);

        String firmName = (firm != null && firm.getFirmName() != null && !firm.getFirmName().isBlank())
                ? firm.getFirmName() : "RupeeCRM";
        Paragraph pFirm = new Paragraph(firmName, title);
        pFirm.setAlignment(Element.ALIGN_CENTER);
        doc.add(pFirm);

        Paragraph pSub = new Paragraph("PAYSLIP - " + salary.getMonthYear(), bold);
        pSub.setAlignment(Element.ALIGN_CENTER);
        doc.add(pSub);
        doc.add(new Paragraph("--------------------------------------------------", normal));

        doc.add(new Paragraph("Emp: " + emp.getName() + " (ID: " + emp.getId() + ")", bold));
        if (emp.getRole() != null) doc.add(new Paragraph("Role: " + emp.getRole(), normal));
        doc.add(new Paragraph("Payment Date: " + (salary.getPaymentDate() != null ? salary.getPaymentDate().format(DATE_FMT) : "-"), normal));
        doc.add(new Paragraph("--------------------------------------------------", normal));

        PdfPTable table = new PdfPTable(new float[]{65, 35});
        table.setWidthPercentage(100);

        table.addCell(thermalCell("Base Salary", normal, Element.ALIGN_LEFT));
        table.addCell(thermalCell("₹" + formatAmount(salary.getBaseSalaryAtTime()), normal, Element.ALIGN_RIGHT));

        if (salary.getBonusAmount() != null && salary.getBonusAmount() > 0) {
            table.addCell(thermalCell("Bonus/OT", normal, Element.ALIGN_LEFT));
            table.addCell(thermalCell("₹" + formatAmount(salary.getBonusAmount()), normal, Element.ALIGN_RIGHT));
        }
        if (salary.getLeaveDeductionAmount() != null && salary.getLeaveDeductionAmount() > 0) {
            table.addCell(thermalCell("Leave Ded.", normal, Element.ALIGN_LEFT));
            table.addCell(thermalCell("-₹" + formatAmount(salary.getLeaveDeductionAmount()), normal, Element.ALIGN_RIGHT));
        }
        if (salary.getAdvanceDeducted() != null && salary.getAdvanceDeducted() > 0) {
            table.addCell(thermalCell("Advance Ded.", normal, Element.ALIGN_LEFT));
            table.addCell(thermalCell("-₹" + formatAmount(salary.getAdvanceDeducted()), normal, Element.ALIGN_RIGHT));
        }

        table.addCell(thermalCell("NET PAID", bold, Element.ALIGN_LEFT));
        table.addCell(thermalCell("₹" + formatAmount(salary.getNetPaid()), bold, Element.ALIGN_RIGHT));

        doc.add(table);
        doc.add(new Paragraph("--------------------------------------------------", normal));
        Paragraph pSig = new Paragraph("Authorized Signatory\n" + firmName, normal);
        pSig.setAlignment(Element.ALIGN_CENTER);
        doc.add(pSig);

        doc.close();
        return baos.toByteArray();
    }

    private PdfPCell thermalCell(String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(align);
        c.setPadding(2);
        return c;
    }

    /**
     * Generate an Employee Statement PDF showing advances, salaries, and YTD summary.
     */
    public byte[] generateEmployeeStatement(Employee emp, List<SalaryRecord> salaries,
                                             List<EmployeeAdvance> advances,
                                             LocalDate from, LocalDate to,
                                             FirmDetails firm) throws Exception {
        return generateEmployeeStatement(emp, salaries, advances, from, to, firm, null, null, null, null);
    }

    public byte[] generateEmployeeStatement(Employee emp, List<SalaryRecord> salaries,
                                             List<EmployeeAdvance> advances,
                                             LocalDate from, LocalDate to,
                                             FirmDetails firm,
                                             String size, String format, String theme, String color) throws Exception {
        InvoicePrintOptions options = InvoicePrintOptions.resolve(size, format, theme, color, firm);
        InvoiceTheme themeEnum = options.getTheme();
        InvoiceColorPalette palette = options.getColorPalette();
        Color primaryColor = palette.getPrimaryColor();
        Color lightTint = palette.getLightTintColor();

        Rectangle pageSize = (options.getFormat() == PrintFormat.A5) ? PageSize.A5 : PageSize.A4;
        float scale = (options.getFormat() == PrintFormat.A5) ? 0.85f : 1.0f;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(pageSize, 36 * scale, 36 * scale, 36 * scale, 36 * scale);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        writer.setPageEvent(new UniversalPageNumberHelper(8f * scale));
        doc.open();

        Font titleFont = new Font(Font.HELVETICA, 16 * scale, Font.BOLD, primaryColor);
        Font hFont = new Font(Font.HELVETICA, 10.5f * scale, Font.BOLD, DocumentThemeHelper.TEXT_DARK);
        Font normal = new Font(Font.HELVETICA, 9.5f * scale, Font.NORMAL, DocumentThemeHelper.TEXT_DARK);
        Font bold = new Font(Font.HELVETICA, 9.5f * scale, Font.BOLD, DocumentThemeHelper.TEXT_DARK);
        Font small = new Font(Font.HELVETICA, 8.5f * scale, Font.NORMAL, DocumentThemeHelper.TEXT_MUTED);

        if (themeEnum == InvoiceTheme.PROFESSIONAL) {
            doc.add(DocumentThemeHelper.createProfessionalBanner("EMPLOYEE FINANCIAL STATEMENT", primaryColor, scale));
        }

        String firmName = (firm != null && firm.getFirmName() != null && !firm.getFirmName().isBlank())
                ? firm.getFirmName() : "Firm Name";

        Paragraph title = new Paragraph(firmName + " - Employee Statement", titleFont);
        if (themeEnum != InvoiceTheme.PROFESSIONAL) {
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);
        }

        doc.add(new Paragraph("Employee: " + emp.getName() + " | Role: " + (emp.getRole() != null ? emp.getRole() : "-"), normal));
        if (from != null && to != null) {
            doc.add(new Paragraph("Period: " + from.format(DATE_FMT) + " to " + to.format(DATE_FMT), small));
        }
        doc.add(new Paragraph("Generated: " + LocalDate.now().format(DATE_FMT), small));
        doc.add(new Paragraph(" "));

        Font thFont = new Font(Font.HELVETICA, 9 * scale, Font.BOLD, (themeEnum == InvoiceTheme.MINIMAL) ? primaryColor : Color.WHITE);

        // Salary History
        doc.add(new Paragraph("Salary Payment History", hFont));
        PdfPTable salTable = new PdfPTable(new float[]{1.5f, 1f, 1f, 1f, 1f});
        salTable.setWidthPercentage(100);
        salTable.setHeaderRows(1);
        String[] salHeads = {"Month", "Base (₹)", "Bonus (₹)", "Deductions (₹)", "Net Paid (₹)"};
        for (String h : salHeads) {
            int align = h.contains("Month") ? Element.ALIGN_CENTER : Element.ALIGN_RIGHT;
            salTable.addCell(DocumentThemeHelper.createHeaderCell(h, themeEnum, primaryColor, thFont, align, scale));
        }
        if (salaries.isEmpty()) {
            PdfPCell cell = new PdfPCell(new Phrase("No salary records found", normal));
            cell.setColspan(5);
            cell.setPadding(10 * scale);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            salTable.addCell(cell);
        } else {
            for (int i = 0; i < salaries.size(); i++) {
                SalaryRecord s = salaries.get(i);
                boolean alt = (i % 2 == 1);
                salTable.addCell(DocumentThemeHelper.createDataCell(new Phrase(s.getMonthYear() != null ? s.getMonthYear() : "-", normal), themeEnum, alt, Element.ALIGN_CENTER, scale));
                salTable.addCell(DocumentThemeHelper.createDataCell(new Phrase(formatAmount(s.getBaseSalaryAtTime()), normal), themeEnum, alt, Element.ALIGN_RIGHT, scale));
                salTable.addCell(DocumentThemeHelper.createDataCell(new Phrase(formatAmount(s.getBonusAmount()), normal), themeEnum, alt, Element.ALIGN_RIGHT, scale));
                double ded = (s.getLeaveDeductionAmount() != null ? s.getLeaveDeductionAmount() : 0) +
                        (s.getAdvanceDeducted() != null ? s.getAdvanceDeducted() : 0);
                salTable.addCell(DocumentThemeHelper.createDataCell(new Phrase(formatAmount(ded), normal), themeEnum, alt, Element.ALIGN_RIGHT, scale));
                salTable.addCell(DocumentThemeHelper.createDataCell(new Phrase(formatAmount(s.getNetPaid()), bold), themeEnum, alt, Element.ALIGN_RIGHT, scale));
            }
        }
        doc.add(salTable);
        doc.add(new Paragraph(" "));

        // Advance Ledger
        doc.add(new Paragraph("Advance / Loan Ledger", hFont));
        PdfPTable advTable = new PdfPTable(new float[]{1.5f, 2f, 1f, 1.2f});
        advTable.setWidthPercentage(100);
        advTable.setHeaderRows(1);
        String[] advHeads = {"Date", "Description", "Amount (₹)", "Type"};
        for (String h : advHeads) {
            int align = h.contains("Amount") ? Element.ALIGN_RIGHT : (h.contains("Date") || h.contains("Type") ? Element.ALIGN_CENTER : Element.ALIGN_LEFT);
            advTable.addCell(DocumentThemeHelper.createHeaderCell(h, themeEnum, primaryColor, thFont, align, scale));
        }
        if (advances.isEmpty()) {
            PdfPCell cell = new PdfPCell(new Phrase("No advance records", normal));
            cell.setColspan(4);
            cell.setPadding(10 * scale);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            advTable.addCell(cell);
        } else {
            for (int i = 0; i < advances.size(); i++) {
                EmployeeAdvance a = advances.get(i);
                boolean alt = (i % 2 == 1);
                advTable.addCell(DocumentThemeHelper.createDataCell(new Phrase(a.getDate() != null ? a.getDate().format(DATE_FMT) : "-", normal), themeEnum, alt, Element.ALIGN_CENTER, scale));
                advTable.addCell(DocumentThemeHelper.createDataCell(new Phrase(a.getDescription() != null ? a.getDescription() : "-", normal), themeEnum, alt, Element.ALIGN_LEFT, scale));
                advTable.addCell(DocumentThemeHelper.createDataCell(new Phrase(formatAmount(a.getAmount()), normal), themeEnum, alt, Element.ALIGN_RIGHT, scale));
                advTable.addCell(DocumentThemeHelper.createDataCell(new Phrase(a.getAmount() > 0 ? "Advance Given" : "Deduction", normal), themeEnum, alt, Element.ALIGN_CENTER, scale));
            }
        }
        doc.add(advTable);

        // Current balance
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Current Advance Balance: ₹ " + formatAmount(emp.getCurrentAdvanceBalance()), bold));

        doc.close();
        return baos.toByteArray();
    }

    private static String formatAmount(Double d) {
        if (d == null) return "0.00";
        return String.format("%.2f", d);
    }

    /**
     * Simple number to words converter (Indian numbering).
     */
    private static String numberToWords(Double amount) {
        if (amount == null) return "Zero Rupees";
        long wholePart = (long) Math.floor(Math.abs(amount));
        int decimalPart = (int) Math.round((Math.abs(amount) - wholePart) * 100);
        String words = wholePart == 0 ? "Zero" : convertToIndianWords(wholePart);
        String result = words + " Rupees";
        if (decimalPart > 0) {
            result += " and " + convertToIndianWords(decimalPart) + " Paise";
        }
        if (amount < 0) result = "Negative " + result;
        return result;
    }

    private static String convertToIndianWords(long n) {
        String[] units = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
                "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen",
                "Eighteen", "Nineteen"};
        String[] tens = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

        if (n < 20) return units[(int) n];
        if (n < 100) return tens[(int) (n / 10)] + (n % 10 > 0 ? " " + units[(int) (n % 10)] : "");
        if (n < 1000) return units[(int) (n / 100)] + " Hundred" + (n % 100 > 0 ? " " + convertToIndianWords(n % 100) : "");
        if (n < 100000) return convertToIndianWords(n / 1000) + " Thousand" + (n % 1000 > 0 ? " " + convertToIndianWords(n % 1000) : "");
        if (n < 10000000) return convertToIndianWords(n / 100000) + " Lakh" + (n % 100000 > 0 ? " " + convertToIndianWords(n % 100000) : "");
        return convertToIndianWords(n / 10000000) + " Crore" + (n % 10000000 > 0 ? " " + convertToIndianWords(n % 10000000) : "");
    }
}