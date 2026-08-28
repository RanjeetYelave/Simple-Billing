package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.Employee;
import com.billing.simple.billsoft.entities.EmployeeAdvance;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.SalaryRecord;
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
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 48, 48);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
        Font hFont = new Font(Font.HELVETICA, 11, Font.BOLD);
        Font normal = new Font(Font.HELVETICA, 10, Font.NORMAL);
        Font bold = new Font(Font.HELVETICA, 10, Font.BOLD);
        Font small = new Font(Font.HELVETICA, 9, Font.NORMAL);

        // --- Header: Company Info ---
        PdfPTable header = new PdfPTable(new float[]{2f, 1.6f});
        header.setWidthPercentage(100);
        header.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);

        // Logo
        if (firm != null && firm.getLogoBase64() != null && !firm.getLogoBase64().isBlank()) {
            try {
                String b64 = firm.getLogoBase64().trim();
                if (b64.startsWith("data:")) {
                    int idx = b64.indexOf("base64,");
                    if (idx >= 0) b64 = b64.substring(idx + 7);
                }
                byte[] bytes = Base64.getDecoder().decode(b64);
                Image logo = Image.getInstance(bytes);
                logo.scaleToFit(110f, 60f);
                logo.setAlignment(Image.LEFT);
                left.addElement(logo);
            } catch (Exception ignored) {}
        }

        String firmName = (firm != null && firm.getFirmName() != null && !firm.getFirmName().isBlank())
                ? firm.getFirmName() : "Firm Name";
        left.addElement(new Paragraph(firmName, titleFont));
        if (firm != null) {
            if (firm.getAddressLine1() != null && !firm.getAddressLine1().isBlank())
                left.addElement(new Paragraph(firm.getAddressLine1(), normal));
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
            if (cityLine.length() > 0) left.addElement(new Paragraph(cityLine.toString(), normal));
        }
        header.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph payslipTitle = new Paragraph("PAYSLIP", titleFont);
        payslipTitle.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(payslipTitle);
        right.addElement(new Paragraph("Month: " + salary.getMonthYear(), bold));
        right.addElement(new Paragraph("Date: " + (salary.getPaymentDate() != null ? salary.getPaymentDate().format(DATE_FMT) : "-"), normal));
        header.addCell(right);

        doc.add(header);
        doc.add(new Paragraph("\n"));

        // --- Employee Details ---
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
        empRight.addElement(new Paragraph("Employee ID: " + emp.getId(), normal));
        if (emp.getDateOfJoining() != null)
            empRight.addElement(new Paragraph("DOJ: " + emp.getDateOfJoining().format(DATE_FMT), normal));
        empSection.addCell(empRight);

        doc.add(empSection);
        doc.add(new Paragraph("\n"));

        // --- Earnings & Deductions Table ---
        PdfPTable details = new PdfPTable(new float[]{3f, 1f, 1f});
        details.setWidthPercentage(100);
        details.setHeaderRows(1);

        // Header row
        String[] detailHeads = {"Description", "Days/Units", "Amount (₹)"};
        for (String h : detailHeads) {
            PdfPCell cell = new PdfPCell(new Phrase(h, hFont));
            cell.setBackgroundColor(Color.LIGHT_GRAY);
            cell.setPadding(6);
            details.addCell(cell);
        }

        // Calculate actual days in month from monthYear
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
        
        // Earnings
        details.addCell(new PdfPCell(new Phrase("Base Salary", normal)));
        details.addCell(new PdfPCell(new Phrase(String.valueOf(workingDays) + "/" + totalDays, normal)));
        details.addCell(new PdfPCell(new Phrase(formatAmount(salary.getBaseSalaryAtTime()), normal)));

        if (salary.getBonusAmount() != null && salary.getBonusAmount() > 0) {
            details.addCell(new PdfPCell(new Phrase("Bonus / Overtime", normal)));
            details.addCell(new PdfPCell(new Phrase("-", normal)));
            details.addCell(new PdfPCell(new Phrase(formatAmount(salary.getBonusAmount()), normal)));
        }

        // Deductions
        if (salary.getLeaveDeductionAmount() != null && salary.getLeaveDeductionAmount() > 0) {
            details.addCell(new PdfPCell(new Phrase("Leave Deduction (" + (salary.getUnpaidLeaves() != null ? salary.getUnpaidLeaves() : 0) + " days)", normal)));
            details.addCell(new PdfPCell(new Phrase(String.valueOf(salary.getUnpaidLeaves()), normal)));
            details.addCell(new PdfPCell(new Phrase("- " + formatAmount(salary.getLeaveDeductionAmount()), normal)));
        }

        if (salary.getAdvanceDeducted() != null && salary.getAdvanceDeducted() > 0) {
            details.addCell(new PdfPCell(new Phrase("Advance Deduction", normal)));
            details.addCell(new PdfPCell(new Phrase("-", normal)));
            details.addCell(new PdfPCell(new Phrase("- " + formatAmount(salary.getAdvanceDeducted()), normal)));
        }

        // Net Paid
        PdfPCell netLabel = new PdfPCell(new Phrase("NET PAID", bold));
        netLabel.setPadding(8);
        netLabel.setBackgroundColor(new Color(230, 240, 255));
        details.addCell(netLabel);
        PdfPCell netDays = new PdfPCell(new Phrase("", bold));
        netDays.setBackgroundColor(new Color(230, 240, 255));
        details.addCell(netDays);
        PdfPCell netAmt = new PdfPCell(new Phrase(formatAmount(salary.getNetPaid()), bold));
        netAmt.setPadding(8);
        netAmt.setBackgroundColor(new Color(230, 240, 255));
        netAmt.setHorizontalAlignment(Element.ALIGN_RIGHT);
        details.addCell(netAmt);

        doc.add(details);
        doc.add(new Paragraph("\n"));

        // --- In Words ---
        doc.add(new Paragraph("Amount in words: " + numberToWords(salary.getNetPaid()), normal));
        doc.add(new Paragraph("\n"));

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
            PdfPCell c = new PdfPCell(new Phrase(h, hFont));
            c.setBackgroundColor(new Color(240, 243, 246));
            c.setPadding(5);
            ytdTable.addCell(c);
        }

        ytdTable.addCell(new PdfPCell(new Phrase("Jan - " + salary.getMonthYear() + " (" + ytdCount + " months)", normal)));
        ytdTable.addCell(new PdfPCell(new Phrase("₹" + formatAmount(ytdGross), normal)));
        ytdTable.addCell(new PdfPCell(new Phrase("₹" + formatAmount(ytdDeductions), normal)));
        ytdTable.addCell(new PdfPCell(new Phrase("₹" + formatAmount(ytdNet), bold)));

        doc.add(ytdTable);
        doc.add(new Paragraph("\n"));

        // Footer signature
        PdfPTable sig = new PdfPTable(new float[]{1f, 1f});
        sig.setWidthPercentage(100);
        PdfPCell leftSig = new PdfPCell(new Phrase("\n\n\nFor " + firmName, normal));
        leftSig.setBorder(Rectangle.NO_BORDER);
        sig.addCell(leftSig);
        PdfPCell rightSig = new PdfPCell(new Phrase("\n\n\nAuthorised Signatory", normal));
        rightSig.setBorder(Rectangle.NO_BORDER);
        rightSig.setHorizontalAlignment(Element.ALIGN_RIGHT);
        sig.addCell(rightSig);
        doc.add(sig);

        doc.close();
        return baos.toByteArray();
    }

    /**
     * Generate an Employee Statement PDF showing advances, salaries, and YTD summary.
     */
    public byte[] generateEmployeeStatement(Employee emp, List<SalaryRecord> salaries,
                                             List<EmployeeAdvance> advances,
                                             LocalDate from, LocalDate to,
                                             FirmDetails firm) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 48, 48);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
        Font hFont = new Font(Font.HELVETICA, 11, Font.BOLD);
        Font normal = new Font(Font.HELVETICA, 10, Font.NORMAL);
        Font bold = new Font(Font.HELVETICA, 10, Font.BOLD);
        Font small = new Font(Font.HELVETICA, 9, Font.NORMAL);

        // Title
        String firmName = (firm != null && firm.getFirmName() != null && !firm.getFirmName().isBlank())
                ? firm.getFirmName() : "Firm Name";
        Paragraph title = new Paragraph(firmName + " - Employee Statement", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        doc.add(title);
        doc.add(new Paragraph("Employee: " + emp.getName() + " | Role: " + (emp.getRole() != null ? emp.getRole() : "-"), normal));
        if (from != null && to != null) {
            doc.add(new Paragraph("Period: " + from.format(DATE_FMT) + " to " + to.format(DATE_FMT), normal));
        }
        doc.add(new Paragraph("\n"));

        // YTD Summary
        doc.add(new Paragraph("YTD (Year-to-Date) Summary", hFont));
        int currentYear = LocalDate.now().getYear();
        double ytdBase = 0;
        double ytdBonus = 0;
        double ytdDeductions = 0;
        double ytdNet = 0;
        int salaryCount = 0;
        for (SalaryRecord s : salaries) {
            String monthYear = s.getMonthYear();
            if (monthYear != null && monthYear.contains("-")) {
                try {
                    String[] parts = monthYear.split("-");
                    if (parts.length != 2) continue;
                    int yearPart = Integer.parseInt(parts[1].trim());
                    if (yearPart == currentYear) {
                        ytdBase += (s.getBaseSalaryAtTime() != null ? s.getBaseSalaryAtTime() : 0);
                        ytdBonus += (s.getBonusAmount() != null ? s.getBonusAmount() : 0);
                        ytdDeductions += (s.getLeaveDeductionAmount() != null ? s.getLeaveDeductionAmount() : 0)
                                + (s.getAdvanceDeducted() != null ? s.getAdvanceDeducted() : 0);
                        ytdNet += (s.getNetPaid() != null ? s.getNetPaid() : 0);
                        salaryCount++;
                    }
                } catch (NumberFormatException ignored) {
                    // skip malformed monthYear
                }
            }
        }
        double ytdGross = ytdBase + ytdBonus;

        PdfPTable ytdTable = new PdfPTable(new float[]{2f, 1f});
        ytdTable.setWidthPercentage(60);
        addYtdRow(ytdTable, "Gross Earnings", formatAmount(ytdBase + ytdBonus), normal);
        addYtdRow(ytdTable, "Total Bonus", formatAmount(ytdBonus), normal);
        addYtdRow(ytdTable, "Total Deductions", formatAmount(ytdDeductions), normal);
        addYtdRow(ytdTable, "Net Paid (YTD)", formatAmount(ytdNet), bold);
        doc.add(ytdTable);
        doc.add(new Paragraph("\n"));

        // Salary History
        doc.add(new Paragraph("Salary Payment History", hFont));
        PdfPTable salTable = new PdfPTable(new float[]{1.5f, 1f, 1f, 1f, 1f});
        salTable.setWidthPercentage(100);
        salTable.setHeaderRows(1);
        String[] salHeads = {"Month", "Base", "Bonus", "Deductions", "Net Paid"};
        for (String h : salHeads) {
            PdfPCell cell = new PdfPCell(new Phrase(h, hFont));
            cell.setBackgroundColor(Color.LIGHT_GRAY);
            cell.setPadding(6);
            salTable.addCell(cell);
        }
        if (salaries.isEmpty()) {
            PdfPCell cell = new PdfPCell(new Phrase("No salary records found", normal));
            cell.setColspan(5);
            cell.setPadding(10);
            salTable.addCell(cell);
        } else {
            for (SalaryRecord s : salaries) {
                salTable.addCell(new PdfPCell(new Phrase(s.getMonthYear() != null ? s.getMonthYear() : "-", normal)));
                salTable.addCell(new PdfPCell(new Phrase(formatAmount(s.getBaseSalaryAtTime()), normal)));
                salTable.addCell(new PdfPCell(new Phrase(formatAmount(s.getBonusAmount()), normal)));
                double ded = (s.getLeaveDeductionAmount() != null ? s.getLeaveDeductionAmount() : 0) +
                        (s.getAdvanceDeducted() != null ? s.getAdvanceDeducted() : 0);
                salTable.addCell(new PdfPCell(new Phrase(formatAmount(ded), normal)));
                salTable.addCell(new PdfPCell(new Phrase(formatAmount(s.getNetPaid()), normal)));
            }
        }
        doc.add(salTable);
        doc.add(new Paragraph("\n"));

        // Advance Ledger
        doc.add(new Paragraph("Advance / Loan Ledger", hFont));
        PdfPTable advTable = new PdfPTable(new float[]{1.5f, 2f, 1f, 1f});
        advTable.setWidthPercentage(100);
        advTable.setHeaderRows(1);
        String[] advHeads = {"Date", "Description", "Amount", "Type"};
        for (String h : advHeads) {
            PdfPCell cell = new PdfPCell(new Phrase(h, hFont));
            cell.setBackgroundColor(Color.LIGHT_GRAY);
            cell.setPadding(6);
            advTable.addCell(cell);
        }
        if (advances.isEmpty()) {
            PdfPCell cell = new PdfPCell(new Phrase("No advance records", normal));
            cell.setColspan(4);
            cell.setPadding(10);
            advTable.addCell(cell);
        } else {
            for (EmployeeAdvance a : advances) {
                advTable.addCell(new PdfPCell(new Phrase(a.getDate() != null ? a.getDate().format(DATE_FMT) : "-", normal)));
                advTable.addCell(new PdfPCell(new Phrase(a.getDescription() != null ? a.getDescription() : "-", normal)));
                advTable.addCell(new PdfPCell(new Phrase(formatAmount(a.getAmount()), normal)));
                advTable.addCell(new PdfPCell(new Phrase(a.getAmount() > 0 ? "Advance Given" : "Deduction", normal)));
            }
        }
        doc.add(advTable);

        // Current balance
        doc.add(new Paragraph("\n"));
        doc.add(new Paragraph("Current Advance Balance: " + formatAmount(emp.getCurrentAdvanceBalance()), bold));

        doc.close();
        return baos.toByteArray();
    }

    // ---- Helpers ----

    private void addYtdRow(PdfPTable table, String label, String value, Font font) {
        PdfPCell l = new PdfPCell(new Phrase(label, font));
        l.setBorder(Rectangle.NO_BORDER);
        l.setPadding(4);
        table.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(value, font));
        v.setBorder(Rectangle.NO_BORDER);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setPadding(4);
        table.addCell(v);
    }

    private static String formatAmount(Double d) {
        if (d == null) return "0.00";
        return String.format("%.2f", d);
    }

    private static String getYearFromMonth(String monthYear) {
        if (monthYear == null || !monthYear.contains("-")) return "current";
        String[] parts = monthYear.split("-");
        // FIX B5: monthYear format is "MM-YYYY" (e.g., "05-2026"), parts[1] is already 4-digit year
        return parts.length >= 2 ? parts[1] : "current";
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