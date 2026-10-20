package com.billing.simple.billsoft.util;

import java.time.LocalDate;

/**
 * Utility for Indian Financial Year calculations (1 April to 31 March).
 * Used for statutory numbering partitions and rollover handling.
 */
public final class IndianFinancialYearUtils {

    private IndianFinancialYearUtils() {
        // utility class
    }

    /**
     * Calculates the standard 2-digit Indian fiscal year string (e.g. "26-27" for 2026-04-01 to 2027-03-31).
     *
     * @param date the date to calculate for (defaults to today if null)
     * @return fiscal year string e.g. "26-27"
     */
    public static String getFinancialYear(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        int year = date.getYear();
        int month = date.getMonthValue();

        int startYear;
        int endYear;

        if (month >= 4) { // April to December
            startYear = year;
            endYear = year + 1;
        } else { // January to March
            startYear = year - 1;
            endYear = year;
        }

        return String.format("%02d-%02d", startYear % 100, endYear % 100);
    }

    /**
     * Calculates the standard 2-digit Indian fiscal year string for current system date.
     */
    public static String getFinancialYear() {
        return getFinancialYear(LocalDate.now());
    }

    /**
     * Calculates the 4-digit Indian fiscal year string (e.g. "2026-2027").
     */
    public static String getFullFinancialYear(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        int year = date.getYear();
        int month = date.getMonthValue();

        int startYear;
        int endYear;

        if (month >= 4) {
            startYear = year;
            endYear = year + 1;
        } else {
            startYear = year - 1;
            endYear = year;
        }

        return String.format("%04d-%04d", startYear, endYear);
    }

    /**
     * Returns the start date of the Indian financial year (April 1).
     */
    public static LocalDate getFinancialYearStart(LocalDate date) {
        if (date == null) date = LocalDate.now();
        int year = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return LocalDate.of(year, 4, 1);
    }

    /**
     * Returns the end date of the Indian financial year (March 31).
     */
    public static LocalDate getFinancialYearEnd(LocalDate date) {
        if (date == null) date = LocalDate.now();
        int year = date.getMonthValue() >= 4 ? date.getYear() + 1 : date.getYear();
        return LocalDate.of(year, 3, 31);
    }
}
