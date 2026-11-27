package com.billing.simple.billsoft.telegram.service;

import com.billing.simple.billsoft.util.IndianFinancialYearUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public final class TelegramDateUtils {

    private static final DateTimeFormatter HUMAN_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private TelegramDateUtils() {}

    public static class DateRange {
        private final LocalDate from;
        private final LocalDate to;
        private final String label;

        public DateRange(LocalDate from, LocalDate to, String label) {
            this.from = from;
            this.to = to;
            this.label = label;
        }

        public LocalDate getFrom() { return from; }
        public LocalDate getTo() { return to; }
        public String getLabel() { return label; }

        public String toColonString() {
            return from.toString() + ":" + to.toString();
        }

        public String formatHuman() {
            return from.format(HUMAN_FORMATTER) + " to " + to.format(HUMAN_FORMATTER);
        }
    }

    public static String formatHuman(LocalDate date) {
        if (date == null) return "—";
        return date.format(HUMAN_FORMATTER);
    }

    public static DateRange resolveQuickFilter(String filterKey) {
        LocalDate today = LocalDate.now();
        if (filterKey == null) {
            return new DateRange(today.withDayOfMonth(1), today, "This Month");
        }
        switch (filterKey.toUpperCase()) {
            case "THIS_MONTH":
                return new DateRange(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()), "This Month");
            case "LAST_MONTH": {
                LocalDate prev = today.minusMonths(1);
                return new DateRange(prev.withDayOfMonth(1), prev.withDayOfMonth(prev.lengthOfMonth()), "Last Month");
            }
            case "THIS_QUARTER": {
                int q = (today.getMonthValue() - 1) / 3;
                LocalDate qStart = LocalDate.of(today.getYear(), q * 3 + 1, 1);
                LocalDate qEnd = qStart.plusMonths(3).minusDays(1);
                return new DateRange(qStart, qEnd, "This Quarter");
            }
            case "LAST_QUARTER": {
                int q = (today.getMonthValue() - 1) / 3;
                LocalDate qStart = LocalDate.of(today.getYear(), q * 3 + 1, 1).minusMonths(3);
                LocalDate qEnd = qStart.plusMonths(3).minusDays(1);
                return new DateRange(qStart, qEnd, "Last Quarter");
            }
            case "THIS_YEAR":
                return new DateRange(LocalDate.of(today.getYear(), 1, 1), LocalDate.of(today.getYear(), 12, 31), "This Year");
            case "LAST_YEAR":
                return new DateRange(LocalDate.of(today.getYear() - 1, 1, 1), LocalDate.of(today.getYear() - 1, 12, 31), "Last Year");
            case "THIS_FY":
                return new DateRange(IndianFinancialYearUtils.getFinancialYearStart(today),
                        IndianFinancialYearUtils.getFinancialYearEnd(today),
                        "This Financial Year (" + IndianFinancialYearUtils.getFinancialYear(today) + ")");
            case "LAST_FY": {
                LocalDate prevYear = today.minusYears(1);
                return new DateRange(IndianFinancialYearUtils.getFinancialYearStart(prevYear),
                        IndianFinancialYearUtils.getFinancialYearEnd(prevYear),
                        "Last Financial Year (" + IndianFinancialYearUtils.getFinancialYear(prevYear) + ")");
            }
            default:
                return new DateRange(today.withDayOfMonth(1), today, "This Month");
        }
    }

    public static LocalDate parseFlexibleDate(String text) {
        if (text == null) return null;
        String clean = text.trim().toLowerCase();
        if ("today".equals(clean)) return LocalDate.now();
        if ("yesterday".equals(clean)) return LocalDate.now().minusDays(1);
        if ("tomorrow".equals(clean)) return LocalDate.now().plusDays(1);

        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,                     // 2026-11-27
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),            // 27-11-2026
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),            // 27/11/2026
                DateTimeFormatter.ofPattern("dd.MM.yyyy"),            // 27.11.2026
                DateTimeFormatter.ofPattern("d-M-yyyy"),              // 1-4-2026
                DateTimeFormatter.ofPattern("d/M/yyyy")               // 1/4/2026
        );

        for (DateTimeFormatter dtf : formatters) {
            try {
                return LocalDate.parse(text.trim(), dtf);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }
}
