#!/usr/bin/env python3
"""
Unit tests for RupeeCRM Release Changelog & Regression Summary Generator
"""

import unittest
import tempfile
import os
import shutil
import xml.etree.ElementTree as ET

# Import functions from generator script
from generate_release_notes import (
    clean_commit_description,
    categorize,
    parse_reports,
    generate_markdown,
    CATEGORIES
)

class TestGenerateReleaseNotes(unittest.TestCase):

    def setUp(self):
        self.test_dir = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.test_dir, ignore_errors=True)

    def _create_surefire_xml(self, filename, classname, tests, failures, errors, skipped):
        root = ET.Element("testsuite", {
            "name": classname,
            "tests": str(tests),
            "failures": str(failures),
            "errors": str(errors),
            "skipped": str(skipped)
        })
        tree = ET.ElementTree(root)
        path = os.path.join(self.test_dir, filename)
        tree.write(path, encoding="utf-8", xml_declaration=True)
        return path

    def test_clean_commit_description_oneline(self):
        raw = "Added Smart Reorder with multi-vendor PO support."
        cleaned = clean_commit_description(raw)
        self.assertEqual(cleaned, "Added Smart Reorder with multi-vendor PO support.")

    def test_clean_commit_description_multiline(self):
        raw = "Added Smart Reorder.\n\nImproved invoice PDF generation.\nFixed stock calculation issue."
        cleaned = clean_commit_description(raw)
        self.assertEqual(cleaned, "Added Smart Reorder.\n\nImproved invoice PDF generation.\nFixed stock calculation issue.")

    def test_clean_commit_description_bullets(self):
        raw = "RupeeCRM v1.0.42 Release:\n* Support for 1,000-line invoices\n* Real-time ledger balances\n* Ed25519 signing validation"
        cleaned = clean_commit_description(raw)
        self.assertIn("* Support for 1,000-line invoices", cleaned)
        self.assertIn("* Real-time ledger balances", cleaned)
        self.assertIn("* Ed25519 signing validation", cleaned)

    def test_clean_commit_description_strips_trailers(self):
        raw = "Fixed invoice calculation rounding.\n\nSigned-off-by: Developer <dev@example.com>\nCo-authored-by: Agent <bot@example.com>"
        cleaned = clean_commit_description(raw)
        self.assertEqual(cleaned, "Fixed invoice calculation rounding.")
        self.assertNotIn("Signed-off-by", cleaned)
        self.assertNotIn("Co-authored-by", cleaned)

    def test_clean_commit_description_empty_fallback(self):
        cleaned = clean_commit_description("")
        self.assertIn("Performance optimizations", cleaned)

    def test_categorization_mapping(self):
        self.assertEqual(categorize("com.billing.simple.billsoft.regression.billing.InvoiceCalculationRegressionTest"), "Billing & Calculations")
        self.assertEqual(categorize("com.billing.simple.billsoft.regression.statements.StatementsRegressionTest"), "Statements & Ledgers")
        self.assertEqual(categorize("com.billing.simple.billsoft.regression.purchases.PurchaseOrderRegressionTest"), "Purchases & Smart Reorder")
        self.assertEqual(categorize("com.billing.simple.billsoft.regression.inventory.InventoryStockRegressionTest"), "Inventory & Stock")
        self.assertEqual(categorize("com.billing.simple.billsoft.regression.hr.EmployeePayrollRegressionTest"), "HR & Payroll")
        self.assertEqual(categorize("com.billing.simple.billsoft.service.SavingServiceTest"), "Finance, Savings & Goals")
        self.assertEqual(categorize("com.billing.simple.billsoft.regression.security.MultiTenantSecurityRegressionTest"), "Multi-Firm Security & NLU")
        self.assertEqual(categorize("com.billing.simple.billsoft.service.LicenseServiceTest"), "Licensing, Backup & System")

    def test_parse_reports_all_passed(self):
        self._create_surefire_xml("TEST-InvoiceCalculationRegressionTest.xml", "com.billing.simple.billsoft.regression.billing.InvoiceCalculationRegressionTest", 46, 0, 0, 0)
        self._create_surefire_xml("TEST-StatementsRegressionTest.xml", "com.billing.simple.billsoft.regression.statements.StatementsRegressionTest", 14, 0, 0, 0)
        self._create_surefire_xml("TEST-MultiTenantSecurityRegressionTest.xml", "com.billing.simple.billsoft.regression.security.MultiTenantSecurityRegressionTest", 35, 0, 0, 0)

        cat_stats, total = parse_reports([self.test_dir])

        self.assertEqual(total["tests"], 95)
        self.assertEqual(total["passed"], 95)
        self.assertEqual(total["failed"], 0)
        self.assertEqual(total["errors"], 0)
        self.assertEqual(total["skipped"], 0)
        self.assertEqual(cat_stats["Billing & Calculations"]["passed"], 46)
        self.assertEqual(cat_stats["Statements & Ledgers"]["passed"], 14)
        self.assertEqual(cat_stats["Multi-Firm Security & NLU"]["passed"], 35)

    def test_parse_reports_with_failures(self):
        self._create_surefire_xml("TEST-InvoiceCalculationRegressionTest.xml", "com.billing.simple.billsoft.regression.billing.InvoiceCalculationRegressionTest", 20, 2, 1, 1)

        cat_stats, total = parse_reports([self.test_dir])

        self.assertEqual(total["tests"], 20)
        self.assertEqual(total["passed"], 16)
        self.assertEqual(total["failed"], 2)
        self.assertEqual(total["errors"], 1)
        self.assertEqual(total["skipped"], 1)

    def test_parse_reports_missing_dir(self):
        with self.assertRaises(FileNotFoundError):
            parse_reports(["/nonexistent/directory/for/testing"])

    def test_generate_markdown_reconciliation(self):
        self._create_surefire_xml("TEST-InvoiceCalculationRegressionTest.xml", "InvoiceCalculationRegressionTest", 10, 0, 0, 0)
        cat_stats, total = parse_reports([self.test_dir])

        whats_new = "Added smart PO generation."
        md = generate_markdown(cat_stats, total, whats_new, "v1.0.42")

        self.assertIn("## What's New", md)
        self.assertIn("Added smart PO generation.", md)
        self.assertIn("## Regression Test Summary", md)
        self.assertIn("| Billing & Calculations | 10 | 10 | 0 | 0 | 0 | ✅ PASS |", md)
        self.assertIn("| **Total** | **10** | **10** | **0** | **0** | **0** | **✅ PASS** |", md)
        self.assertIn("**Regression result:** 10 passed / 10 total — 0 failed, 0 errors, 0 skipped.", md)

if __name__ == "__main__":
    unittest.main()
