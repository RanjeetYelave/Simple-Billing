package com.billing.simple.billsoft.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FuzzySearchMatcherTest {

    static class TestProduct {
        final String name;
        final String category;
        final String hsnCode;

        TestProduct(String name, String category, String hsnCode) {
            this.name = name;
            this.category = category;
            this.hsnCode = hsnCode;
        }
    }

    static class TestCustomer {
        final String name;
        final String phone;
        final String gstin;

        TestCustomer(String name, String phone, String gstin) {
            this.name = name;
            this.phone = phone;
            this.gstin = gstin;
        }
    }

    private final List<TestProduct> products = Arrays.asList(
            new TestProduct("CPVC Ball Valve - 15 mm", "CPVC Fitting", "84818090"),
            new TestProduct("CPVC Ball Valve - 20 mm", "CPVC Fitting", "84818090"),
            new TestProduct("CPVC Ball Valve - 25 mm", "CPVC Fitting", "84818090"),
            new TestProduct("Brass Bib Cock 15mm", "Sanitary", "84818020")
    );

    private final List<TestCustomer> customers = Arrays.asList(
            new TestCustomer("Samrat Machinery Pvt Ltd", "+91 98765 43210", "27AABCS1234F1Z5"),
            new TestCustomer("Maharaja Tools & Hardware", "9822011223", "27XYZ1234")
    );

    @Test
    void testMissFewWordsProductSearch() {
        // User typed "cpvc 15", missing "Ball Valve -"
        List<TestProduct> matches = FuzzySearchMatcher.filterAndSort(
                products, "cpvc 15", p -> Arrays.asList(p.name, p.category, p.hsnCode), 10);
        assertFalse(matches.isEmpty(), "Should find matches for 'cpvc 15'");
        assertEquals("CPVC Ball Valve - 15 mm", matches.get(0).name);
    }

    @Test
    void testPartialWordsProductSearch() {
        // User typed "bal val 15"
        List<TestProduct> matches = FuzzySearchMatcher.filterAndSort(
                products, "bal val 15", p -> Arrays.asList(p.name, p.category, p.hsnCode), 10);
        assertFalse(matches.isEmpty(), "Should find matches for 'bal val 15'");
        assertEquals("CPVC Ball Valve - 15 mm", matches.get(0).name);
    }

    @Test
    void testSpecialCharsAndSpaceToleranceProductSearch() {
        // "cpvc-ball", "cpvc  25" (multiple spaces), "15mm" (no space)
        List<TestProduct> dashMatch = FuzzySearchMatcher.filterAndSort(
                products, "cpvc-ball", p -> Arrays.asList(p.name, p.category, p.hsnCode), 10);
        assertEquals(3, dashMatch.size());

        List<TestProduct> spaceMatch = FuzzySearchMatcher.filterAndSort(
                products, "cpvc   25", p -> Arrays.asList(p.name, p.category, p.hsnCode), 10);
        assertEquals(1, spaceMatch.size());
        assertEquals("CPVC Ball Valve - 25 mm", spaceMatch.get(0).name);

        List<TestProduct> compactMatch = FuzzySearchMatcher.filterAndSort(
                products, "15mm", p -> Arrays.asList(p.name, p.category, p.hsnCode), 10);
        assertTrue(compactMatch.stream().anyMatch(p -> p.name.equals("CPVC Ball Valve - 15 mm")));
        assertTrue(compactMatch.stream().anyMatch(p -> p.name.equals("Brass Bib Cock 15mm")));
    }

    @Test
    void testNumericExactnessForDimensions() {
        // "valve 20" must NOT match "15 mm" or "25 mm"
        List<TestProduct> matches = FuzzySearchMatcher.filterAndSort(
                products, "valve 20", p -> Arrays.asList(p.name, p.category, p.hsnCode), 10);
        assertEquals(1, matches.size());
        assertEquals("CPVC Ball Valve - 20 mm", matches.get(0).name);
    }

    @Test
    void testMissFewWordsCustomerSearch() {
        // User typed "samrat ltd", missing "Machinery Pvt"
        List<TestCustomer> matches = FuzzySearchMatcher.filterAndSort(
                customers, "samrat ltd", c -> Arrays.asList(c.name, c.phone, c.gstin), 10);
        assertEquals(1, matches.size());
        assertEquals("Samrat Machinery Pvt Ltd", matches.get(0).name);
    }

    @Test
    void testPartialWordsAndSpecialCharsCustomerSearch() {
        // "sam mac" (partial words), "samrat-machinery" (hyphen)
        List<TestCustomer> partialMatch = FuzzySearchMatcher.filterAndSort(
                customers, "sam mac", c -> Arrays.asList(c.name, c.phone, c.gstin), 10);
        assertEquals(1, partialMatch.size());
        assertEquals("Samrat Machinery Pvt Ltd", partialMatch.get(0).name);

        List<TestCustomer> hyphenMatch = FuzzySearchMatcher.filterAndSort(
                customers, "samrat-machinery", c -> Arrays.asList(c.name, c.phone, c.gstin), 10);
        assertEquals(1, hyphenMatch.size());
        assertEquals("Samrat Machinery Pvt Ltd", hyphenMatch.get(0).name);
    }

    @Test
    void testPhoneFormattingToleranceCustomerSearch() {
        // User types plain digits "9876543210" against "+91 98765 43210"
        List<TestCustomer> phoneMatch = FuzzySearchMatcher.filterAndSort(
                customers, "9876543210", c -> Arrays.asList(c.name, c.phone, c.gstin), 10);
        assertEquals(1, phoneMatch.size());
        assertEquals("Samrat Machinery Pvt Ltd", phoneMatch.get(0).name);
    }
}

