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
            if (firm.getAddressLine2() != null && !firm.getAddressLine2().isBlank())
                left.addElement(new Paragraph(firm.getAddressLine2(), small));
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
            if (firm.getGstin() != null && !firm.getGstin().isBlank())
                left.addElement(new Paragraph("GSTIN: " + firm.getGstin(), small));
            StringBuilder contactLine = new StringBuilder();
            if (firm.getPhone() != null && !firm.getPhone().isBlank()) contactLine.append("Phone: ").append(firm.getPhone());
            if (firm.getEmail() != null && !firm.getEmail().isBlank()) {
                if (contactLine.length() > 0) contactLine.append(" | ");
                contactLine.append("Email: ").append(firm.getEmail());
            }
            if (contactLine.length() > 0) left.addElement(new Paragraph(contactLine.toString(), small));
        }
        header.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        if (themeEnum != InvoiceTheme.PROFESSIONAL) {
            Paragraph payslipTitle = new Paragraph("PAYSLIP", new Font(Font.HELVETICA, 16 * scale, Font.BOLD, primaryColor));
            payslipTitle.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(payslipTitle);
        }
        String myFormatted = formatMonthYear(salary.getMonthYear());
        Paragraph pMonth = new Paragraph("Salary Month: " + myFormatted, bold);
        pMonth.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(pMonth);

        String refNo = "SLIP-" + (salary.getId() != null ? salary.getId() : "1");
        Paragraph pRef = new Paragraph("Ref No: " + refNo, small);
        pRef.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(pRef);

        Paragraph pDate = new Paragraph("Disbursal Date: " + (salary.getPaymentDate() != null ? salary.getPaymentDate().format(DATE_FMT) : "-"), small);
        pDate.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(pDate);

        Paragraph pStatus = new Paragraph("Payment Status: PAID", new Font(Font.HELVETICA, 8.5f * scale, Font.BOLD, new Color(22, 101, 52)));
        pStatus.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(pStatus);
        header.addCell(right);

        doc.add(header);

        // --- Calculate calendar days and working days ---
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
        int daysAbsent = salary.getDaysAbsent() != null ? salary.getDaysAbsent() : 0;
        int workingDays = Math.max(0, totalDays - daysAbsent);
        int unpaidLeaves = salary.getUnpaidLeaves() != null ? salary.getUnpaidLeaves() : 0;

        // --- Employee Details Card ---
        PdfPTable empSection = new PdfPTable(new float[]{1.2f, 2f, 1.2f, 2f});
        empSection.setWidthPercentage(100);
        empSection.setSpacingBefore(8 * scale);
        empSection.setSpacingAfter(8 * scale);

        PdfPCell empHeaderCell = new PdfPCell(new Phrase("EMPLOYEE INFORMATION", new Font(Font.HELVETICA, 8.5f * scale, Font.BOLD, primaryColor)));
        empHeaderCell.setColspan(4);
        empHeaderCell.setBackgroundColor(lightTint);
        empHeaderCell.setPadding(4.5f * scale);
        empHeaderCell.setBorderColor(DocumentThemeHelper.BORDER_COLOR);
        empSection.addCell(empHeaderCell);

        addInfoCell(empSection, "Employee Name:", bold, emp.getName() != null ? emp.getName() : "-", normal, scale);
        addInfoCell(empSection, "Employee ID:", bold, "EMP-" + String.format("%04d", emp.getId() != null ? emp.getId() : 1), normal, scale);

        String designation = emp.getDesignation() != null && !emp.getDesignation().isBlank()
                ? emp.getDesignation() : (emp.getRole() != null && !emp.getRole().isBlank() ? emp.getRole() : "Staff");
        String department = emp.getDepartment() != null && !emp.getDepartment().isBlank() ? emp.getDepartment() : "Operations";
        addInfoCell(empSection, "Designation:", bold, designation, normal, scale);
        addInfoCell(empSection, "Department:", bold, department, normal, scale);

        String doj = emp.getDateOfJoining() != null ? emp.getDateOfJoining().format(DATE_FMT) : "—";
        String contact = emp.getPhone() != null && !emp.getPhone().isBlank() ? emp.getPhone() : (emp.getEmail() != null ? emp.getEmail() : "—");
        addInfoCell(empSection, "Date of Joining:", bold, doj, normal, scale);
        addInfoCell(empSection, "Contact:", bold, contact, normal, scale);

        String bankInfo = (emp.getBankName() != null && !emp.getBankName().isBlank()) ? emp.getBankName() : "Bank Transfer";
        String bankAcc = maskBankAccount(emp.getBankAccountNumber());
        addInfoCell(empSection, "Payment Mode:", bold, bankInfo, normal, scale);
        addInfoCell(empSection, "Bank Account:", bold, bankAcc, normal, scale);

        addInfoCell(empSection, "Calendar Days:", bold, String.valueOf(totalDays), normal, scale);
        addInfoCell(empSection, "Days Worked:", bold, workingDays + " Days" + (unpaidLeaves > 0 ? " (" + unpaidLeaves + " Unpaid)" : ""), normal, scale);

        doc.add(empSection);

        // --- Earnings & Deductions Structured 2-Column Table ---
        PdfPTable details = new PdfPTable(new float[]{2.4f, 1.2f, 2.4f, 1.2f});
        details.setWidthPercentage(100);
        details.setHeaderRows(1);
        details.setSpacingBefore(4 * scale);
        details.setSpacingAfter(4 * scale);

        Font thFont = new Font(Font.HELVETICA, 9 * scale, Font.BOLD, (themeEnum == InvoiceTheme.MINIMAL) ? primaryColor : Color.WHITE);
        details.addCell(DocumentThemeHelper.createHeaderCell("EARNINGS", themeEnum, primaryColor, thFont, Element.ALIGN_LEFT, scale));
        details.addCell(DocumentThemeHelper.createHeaderCell("AMOUNT (₹)", themeEnum, primaryColor, thFont, Element.ALIGN_RIGHT, scale));
        details.addCell(DocumentThemeHelper.createHeaderCell("DEDUCTIONS", themeEnum, primaryColor, thFont, Element.ALIGN_LEFT, scale));
        details.addCell(DocumentThemeHelper.createHeaderCell("AMOUNT (₹)", themeEnum, primaryColor, thFont, Element.ALIGN_RIGHT, scale));

        double baseSal = salary.getBaseSalaryAtTime() != null ? salary.getBaseSalaryAtTime() : 0.0;
        double bonus = salary.getBonusAmount() != null ? salary.getBonusAmount() : 0.0;
        double grossEarnings = baseSal + bonus;

        double leaveDed = salary.getLeaveDeductionAmount() != null ? salary.getLeaveDeductionAmount() : 0.0;
        double advDed = salary.getAdvanceDeducted() != null ? salary.getAdvanceDeducted() : 0.0;
        double totalDeductions = leaveDed + advDed;

        // Row 1: Basic Salary & Leave Deductions
        details.addCell(DocumentThemeHelper.createDataCell(new Phrase("Basic / Monthly Salary", normal), themeEnum, false, Element.ALIGN_LEFT, scale));
        details.addCell(DocumentThemeHelper.createDataCell(new Phrase(formatIndian(baseSal), normal), themeEnum, false, Element.ALIGN_RIGHT, scale));
        String leaveDesc = unpaidLeaves > 0 ? "Leave Deductions (" + unpaidLeaves + " days)" : "Leave Deductions";
        details.addCell(DocumentThemeHelper.createDataCell(new Phrase(leaveDesc, normal), themeEnum, false, Element.ALIGN_LEFT, scale));
        details.addCell(DocumentThemeHelper.createDataCell(new Phrase(leaveDed > 0 ? "- " + formatIndian(leaveDed) : "0.00", normal), themeEnum, false, Element.ALIGN_RIGHT, scale));

        // Row 2: Bonus & Advance Deductions
        String bonusLabel = bonus > 0 ? "Bonus / Incentives / OT" : "—";
        String bonusVal = bonus > 0 ? formatIndian(bonus) : "—";
        String advLabel = advDed > 0 ? "Advance / Loan Deduction" : "—";
        String advVal = advDed > 0 ? "- " + formatIndian(advDed) : "—";

        details.addCell(DocumentThemeHelper.createDataCell(new Phrase(bonusLabel, normal), themeEnum, true, Element.ALIGN_LEFT, scale));
        details.addCell(DocumentThemeHelper.createDataCell(new Phrase(bonusVal, normal), themeEnum, true, Element.ALIGN_RIGHT, scale));
        details.addCell(DocumentThemeHelper.createDataCell(new Phrase(advLabel, normal), themeEnum, true, Element.ALIGN_LEFT, scale));
        details.addCell(DocumentThemeHelper.createDataCell(new Phrase(advVal, normal), themeEnum, true, Element.ALIGN_RIGHT, scale));

        // Row 3: Totals
        PdfPCell grossLabel = new PdfPCell(new Phrase("TOTAL GROSS EARNINGS", bold));
        grossLabel.setPadding(6 * scale);
        grossLabel.setBackgroundColor(lightTint);
        grossLabel.setBorderColor(DocumentThemeHelper.BORDER_LIGHT);
        details.addCell(grossLabel);

        PdfPCell grossAmt = new PdfPCell(new Phrase("₹ " + formatIndian(grossEarnings), bold));
        grossAmt.setPadding(6 * scale);
        grossAmt.setBackgroundColor(lightTint);
        grossAmt.setBorderColor(DocumentThemeHelper.BORDER_LIGHT);
        grossAmt.setHorizontalAlignment(Element.ALIGN_RIGHT);
        details.addCell(grossAmt);

        PdfPCell totDedLabel = new PdfPCell(new Phrase("TOTAL DEDUCTIONS", bold));
        totDedLabel.setPadding(6 * scale);
        totDedLabel.setBackgroundColor(lightTint);
        totDedLabel.setBorderColor(DocumentThemeHelper.BORDER_LIGHT);
        details.addCell(totDedLabel);

        PdfPCell totDedAmt = new PdfPCell(new Phrase("₹ " + formatIndian(totalDeductions), bold));
        totDedAmt.setPadding(6 * scale);
        totDedAmt.setBackgroundColor(lightTint);
        totDedAmt.setBorderColor(DocumentThemeHelper.BORDER_LIGHT);
        totDedAmt.setHorizontalAlignment(Element.ALIGN_RIGHT);
        details.addCell(totDedAmt);

        doc.add(details);

        // --- Prominent Net Salary Payable Block ---
        PdfPTable netTable = new PdfPTable(new float[]{1.5f, 1f});
        netTable.setWidthPercentage(100);
        netTable.setSpacingBefore(8 * scale);
        netTable.setSpacingAfter(8 * scale);

        PdfPCell netLeft = new PdfPCell();
        netLeft.setBackgroundColor(lightTint);
        netLeft.setBorder(Rectangle.BOX);
        netLeft.setBorderColor(primaryColor);
        netLeft.setBorderWidth(1.2f);
        netLeft.setPadding(8 * scale);

        Font netTitleFont = new Font(Font.HELVETICA, 11 * scale, Font.BOLD, primaryColor);
        Font inWordsFont = new Font(Font.HELVETICA, 8.5f * scale, Font.ITALIC, DocumentThemeHelper.TEXT_DARK);
        netLeft.addElement(new Paragraph("NET SALARY PAYABLE", netTitleFont));
        netLeft.addElement(new Paragraph("Amount in Words: " + numberToWords(salary.getNetPaid()), inWordsFont));
        netTable.addCell(netLeft);

        PdfPCell netRight = new PdfPCell();
        netRight.setBackgroundColor(lightTint);
        netRight.setBorder(Rectangle.BOX);
        netRight.setBorderColor(primaryColor);
        netRight.setBorderWidth(1.2f);
        netRight.setPadding(8 * scale);
        netRight.setHorizontalAlignment(Element.ALIGN_RIGHT);
        netRight.setVerticalAlignment(Element.ALIGN_MIDDLE);

        Font netAmountFont = new Font(Font.HELVETICA, 15 * scale, Font.BOLD, primaryColor);
        Paragraph netAmtP = new Paragraph("₹ " + formatIndian(salary.getNetPaid()), netAmountFont);
        netAmtP.setAlignment(Element.ALIGN_RIGHT);
        netRight.addElement(netAmtP);
        netTable.addCell(netRight);

        doc.add(netTable);

        // --- Year-to-Date (YTD) Financial Summary ---
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

        Paragraph ytdTitle = new Paragraph("Year-to-Date (YTD) Financial Summary (" + targetYear + ")", hFont);
        ytdTitle.setSpacingBefore(6 * scale);
        ytdTitle.setSpacingAfter(4 * scale);
        doc.add(ytdTitle);

        PdfPTable ytdTable = new PdfPTable(new float[]{2f, 1.5f, 1.5f, 1.5f});
        ytdTable.setWidthPercentage(100);
        ytdTable.setHeaderRows(1);

        String[] ytdHeads = {"Period", "YTD Gross Earnings", "YTD Deductions", "YTD Net Paid"};
        for (String h : ytdHeads) {
            ytdTable.addCell(DocumentThemeHelper.createHeaderCell(h, themeEnum, primaryColor, thFont, Element.ALIGN_CENTER, scale));
        }

        ytdTable.addCell(DocumentThemeHelper.createDataCell(new Phrase("Jan - " + salary.getMonthYear() + " (" + ytdCount + " mos)", normal), themeEnum, false, Element.ALIGN_CENTER, scale));
        ytdTable.addCell(DocumentThemeHelper.createDataCell(new Phrase("₹ " + formatIndian(ytdGross), normal), themeEnum, false, Element.ALIGN_RIGHT, scale));
        ytdTable.addCell(DocumentThemeHelper.createDataCell(new Phrase("₹ " + formatIndian(ytdDeductions), normal), themeEnum, false, Element.ALIGN_RIGHT, scale));
        ytdTable.addCell(DocumentThemeHelper.createDataCell(new Phrase("₹ " + formatIndian(ytdNet), bold), themeEnum, false, Element.ALIGN_RIGHT, scale));

        doc.add(ytdTable);

        // --- Footer Notice & Signatures ---
        Paragraph noteP = new Paragraph("This is a computer-generated payslip from RupeeCRM and requires no physical signature.", small);
        noteP.setAlignment(Element.ALIGN_CENTER);
        noteP.setSpacingBefore(10 * scale);
        doc.add(noteP);

        PdfPTable sig = new PdfPTable(new float[]{1f, 1f});
        sig.setWidthPercentage(100);
        sig.setSpacingBefore(16 * scale);

        PdfPCell leftSig = new PdfPCell();
        leftSig.setBorder(Rectangle.NO_BORDER);
        leftSig.addElement(new Paragraph("Employee Signature\n\n\n_______________________", normal));
        sig.addCell(leftSig);

        PdfPCell rightSig = new PdfPCell();
        rightSig.setBorder(Rectangle.NO_BORDER);
        rightSig.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph rSigP = new Paragraph("For " + firmName + "\n\n\n_______________________\nAuthorised Signatory", normal);
        rSigP.setAlignment(Element.ALIGN_RIGHT);
        rightSig.addElement(rSigP);
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

    private void addInfoCell(PdfPTable table, String label, Font labelFont, String value, Font valueFont, float scale) {
        PdfPCell lCell = new PdfPCell(new Phrase(label, labelFont));
        lCell.setPadding(4f * scale);
        lCell.setBorderColor(DocumentThemeHelper.BORDER_LIGHT);
        lCell.setBackgroundColor(new Color(248, 250, 252));
        table.addCell(lCell);

        PdfPCell vCell = new PdfPCell(new Phrase(value, valueFont));
        vCell.setPadding(4f * scale);
        vCell.setBorderColor(DocumentThemeHelper.BORDER_LIGHT);
        table.addCell(vCell);
    }

    private static String maskBankAccount(String acc) {
        if (acc == null || acc.isBlank()) return "—";
        String trimmed = acc.trim();
        if (trimmed.length() <= 4) return trimmed;
        String last4 = trimmed.substring(trimmed.length() - 4);
        return "•••• •••• " + last4;
    }

    private static String formatMonthYear(String my) {
        if (my == null || !my.contains("-")) return my != null ? my : "—";
        try {
            String[] parts = my.split("-");
            if (parts.length == 2) {
                int mo = Integer.parseInt(parts[0].trim());
                int yr = Integer.parseInt(parts[1].trim());
                java.time.Month m = java.time.Month.of(mo);
                String name = m.name().substring(0, 1) + m.name().substring(1).toLowerCase();
                return name + " " + yr;
            }
        } catch (Exception ignored) {}
        return my;
    }

    public static String formatIndian(Double amount) {
        if (amount == null) return "0.00";
        long whole = (long) Math.floor(Math.abs(amount));
        long paise = Math.round((Math.abs(amount) - whole) * 100);
        String s = Long.toString(whole);
        StringBuilder sb = new StringBuilder();
        int len = s.length();
        if (len <= 3) {
            sb.append(s);
        } else {
            sb.append(s.substring(len - 3));
            int i = len - 3;
            while (i > 0) {
                int start = Math.max(0, i - 2);
                sb.insert(0, s.substring(start, i) + ",");
                i -= 2;
            }
        }
        return (amount < 0 ? "-" : "") + sb.toString() + "." + String.format("%02d", paise);
    }

    public static String formatAmount(Double d) {
        return formatIndian(d);
    }

    /**
     * Simple number to words converter (Indian numbering).
     */
    private static String numberToWords(Double amount) {
        if (amount == null) return "Zero Rupees Only";
        long wholePart = (long) Math.floor(Math.abs(amount));
        int decimalPart = (int) Math.round((Math.abs(amount) - wholePart) * 100);
        String words = wholePart == 0 ? "Zero" : convertToIndianWords(wholePart);
        String result = words + " Rupees";
        if (decimalPart > 0) {
            result += " and " + convertToIndianWords(decimalPart) + " Paise";
        }
        result += " Only";
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