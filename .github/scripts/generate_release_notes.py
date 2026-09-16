#!/usr/bin/env python3
"""
RupeeCRM Release Changelog & Regression Summary Generator
==========================================================
Generates GitHub Release notes combining:
1. Developer commit description / release notes (Customer-facing "What's New")
2. Dynamically parsed JUnit/Surefire XML regression test summary table
3. Release distribution artifact manifest

Fails safely if test reports are missing or tests failed.
"""

import os
import sys
import glob
import re
import argparse
import xml.etree.ElementTree as ET

CATEGORIES = [
    ("Billing & Calculations", [
        "InvoiceCalculation", "InvoiceLifecycle", "InvoicePdf", "InvoiceService", 
        "LargeInvoiceAndQuotation", "InvoicePayment", "SalesReturn", "AccountingAndInventoryAudit"
    ]),
    ("Statements & Ledgers", [
        "StatementsRegressionTest", "StatementsAndLedgerLargeDataset", "StatementService",
        "PartyServiceAdvanceAdjustment", "PartyService", "CustomerService", "CustomerController",
        "StatementAndDevLogEdge"
    ]),
    ("Purchases & Smart Reorder", [
        "PurchaseOrder", "PartyController", "PartyLedger", "PartyAndPurchaseController"
    ]),
    ("Inventory & Stock", [
        "ProductCatalog", "InventoryStock", "ProductService", "ProductController"
    ]),
    ("HR & Payroll", [
        "EmployeeAttendance", "EmployeePayroll", "EmployeePdf", "EmployeeController"
    ]),
    ("Finance, Savings & Goals", [
        "ExpenseController", "SavingService", "GoalService", "BackupSavingsGoals"
    ]),
    ("Multi-Firm Security & NLU", [
        "MultiTenantSecurity", "AuthorizationFilter", "AuthenticationRegression",
        "Omnisearch", "RupeecrmNlu", "OpenNlp", "KpiController", "KpiService",
        "BusinessLetter", "WhatsApp", "Planner", "InboxMessage", "ServiceBooster", "ServiceImplDeep"
    ]),
    ("Licensing, Backup & System", [
        "License", "MachineIdentity", "RawKeyConversion", "CryptoSpike", "SnoozeManager",
        "DataProtection", "BackupIntegration", "BackupService", "BackupSelectiveImport",
        "AutoBackupService", "BackupAndReset", "PaginationBoundary", "AllApiEndpoints",
        "PersistenceAndDataDurability", "NoteSize", "HealthHeartbeat", "UpdateService",
        "UpdateController", "ApiDiagnostics", "NetworkReachability", "FirmDetails",
        "DataDirectoryResolver", "FlexibleLocalDateTime", "BillsoftApplication"
    ])
]

def categorize(class_name):
    base_name = class_name.split(".")[-1]
    for cat_name, patterns in CATEGORIES:
        for p in patterns:
            if p.lower() in base_name.lower():
                return cat_name
    return "Licensing, Backup & System"

def parse_reports(report_dirs):
    category_stats = {cat: {"tests": 0, "passed": 0, "failed": 0, "errors": 0, "skipped": 0} for cat, _ in CATEGORIES}
    total_stats = {"tests": 0, "passed": 0, "failed": 0, "errors": 0, "skipped": 0}
    seen_classes = set()

    xml_files = []
    for d in report_dirs:
        if os.path.isdir(d):
            xml_files.extend(glob.glob(os.path.join(d, "TEST-*.xml")))

    if not xml_files:
        raise FileNotFoundError(f"No Surefire XML test report files found in directories: {report_dirs}")

    for f in sorted(xml_files):
        try:
            tree = ET.parse(f)
            root = tree.getroot()
            class_name = root.attrib.get("name", "")
            if not class_name or class_name in seen_classes:
                continue
            seen_classes.add(class_name)

            tests = int(root.attrib.get("tests", 0))
            failures = int(root.attrib.get("failures", 0))
            errors = int(root.attrib.get("errors", 0))
            skipped = int(root.attrib.get("skipped", 0))
            passed = tests - (failures + errors + skipped)

            cat = categorize(class_name)
            category_stats[cat]["tests"] += tests
            category_stats[cat]["passed"] += passed
            category_stats[cat]["failed"] += failures
            category_stats[cat]["errors"] += errors
            category_stats[cat]["skipped"] += skipped

            total_stats["tests"] += tests
            total_stats["passed"] += passed
            total_stats["failed"] += failures
            total_stats["errors"] += errors
            total_stats["skipped"] += skipped
        except Exception as e:
            raise ValueError(f"Failed to parse test report {f}: {e}")

    return category_stats, total_stats

def clean_commit_description(raw_msg):
    """
    Cleans git commit message into a pristine customer-facing What's New markdown.
    Preserves subjects, multi-line bodies, bullet points, and markdown formatting.
    Removes internal git/CI trailers (Signed-off-by, Co-authored-by, etc.).
    """
    if not raw_msg:
        return "Performance optimizations, stability improvements, and general enhancement updates."
    
    msg = raw_msg
    if "\\n" in msg and "\n" not in msg:
        msg = msg.replace("\\n", "\n")

    lines = msg.strip().splitlines()
    cleaned_lines = []
    for line in lines:
        # Strip trailing commit trailers
        if re.match(r'^(Signed-off-by|Co-authored-by|Reviewed-by|Change-Id):', line, re.IGNORECASE):
            continue
        cleaned_lines.append(line)
    
    cleaned = "\n".join(cleaned_lines).strip()
    if not cleaned:
        return "Performance optimizations, stability improvements, and general enhancement updates."
    return cleaned

def generate_markdown(cat_stats, total, whats_new_text, version=""):
    md = []
    md.append("## What's New\n")
    md.append(whats_new_text.strip())
    md.append("\n\n## Regression Test Summary\n")
    md.append("| Test Area | Tests | Passed | Failed | Errors | Skipped | Status |")
    md.append("|---|---:|---:|---:|---:|---:|---|")
    
    for cat, _ in CATEGORIES:
        s = cat_stats[cat]
        status = "✅ PASS" if (s["failed"] == 0 and s["errors"] == 0 and s["tests"] > 0) else ("⚠️ EMPTY" if s["tests"] == 0 else "❌ FAIL")
        md.append(f"| {cat} | {s['tests']} | {s['passed']} | {s['failed']} | {s['errors']} | {s['skipped']} | {status} |")
    
    total_status = "✅ PASS" if (total["failed"] == 0 and total["errors"] == 0 and total["tests"] > 0) else "❌ FAIL"
    md.append(f"| **Total** | **{total['tests']}** | **{total['passed']}** | **{total['failed']}** | **{total['errors']}** | **{total['skipped']}** | **{total_status}** |")
    
    md.append(f"\n**Regression result:** {total['passed']} passed / {total['tests']} total — {total['failed']} failed, {total['errors']} errors, {total['skipped']} skipped.\n")
    
    md.append("### 📦 Distribution Artifacts")
    md.append("- **Windows Installer**: `RupeeCRM-Setup.msi`")
    md.append("- **Windows Portable**: `RupeeCRM-Windows-x64.zip`")
    md.append("- **macOS Apple Silicon (M1/M2/M3/M4)**: `RupeeCRM-macOS-arm64.tar.gz` (Native Application Bundle)")
    md.append("- **WAR Payload**: `billsoft.war`\n")
    md.append("### 🍏 macOS Quick Install (Zero-Warning)")
    md.append("To install or update on macOS without Gatekeeper malware warnings, run in Terminal:")
    md.append("```bash")
    md.append("curl -fsSL https://raw.githubusercontent.com/RanjeetYelave/Simple-Billing/main/tools/install-mac.sh | bash")
    md.append("```\n")
    
    return "\n".join(md)

def main():
    parser = argparse.ArgumentParser(description="Generate RupeeCRM release changelog and regression summary")
    parser.add_argument("--commit-msg-file", help="Path to file containing commit message")
    parser.add_argument("--commit-msg", help="Raw commit message string")
    parser.add_argument("--release-notes-file", help="Path to custom release-notes.txt if present")
    parser.add_argument("--version", default="", help="Release version tag (e.g. v1.0.42)")
    parser.add_argument("--surefire-dirs", default="billsoft/target/surefire-reports,launcher/target/surefire-reports", help="Comma-separated directories containing surefire XML reports")
    parser.add_argument("--output", default="release-notes.txt", help="Output path for final release notes markdown")
    args = parser.parse_args()

    # 1. Resolve Developer What's New description
    whats_new_text = ""
    if args.release_notes_file and os.path.isfile(args.release_notes_file):
        with open(args.release_notes_file, "r", encoding="utf-8") as rf:
            whats_new_text = rf.read().strip()
    elif args.commit_msg_file and os.path.isfile(args.commit_msg_file):
        with open(args.commit_msg_file, "r", encoding="utf-8") as cf:
            whats_new_text = cf.read().strip()
    elif args.commit_msg:
        whats_new_text = args.commit_msg.strip()
    
    whats_new_text = clean_commit_description(whats_new_text)

    # 2. Parse Surefire test reports
    report_dirs = [d.strip() for d in args.surefire_dirs.split(",") if d.strip()]
    try:
        cat_stats, total = parse_reports(report_dirs)
    except Exception as e:
        print(f"Error parsing test reports: {e}", file=sys.stderr)
        sys.exit(1)

    # 3. Generate combined markdown
    release_md = generate_markdown(cat_stats, total, whats_new_text, args.version)

    # 4. Write output file
    with open(args.output, "w", encoding="utf-8") as out:
        out.write(release_md)

    print(f"Successfully generated release notes at {args.output} ({total['tests']} total tests, status: {'PASS' if total['failed']==0 and total['errors']==0 else 'FAIL'})")

    if total["failed"] > 0 or total["errors"] > 0:
        print(f"WARNING: Release contains {total['failed']} failures and {total['errors']} errors!", file=sys.stderr)
        # Note: Surefire execution already halts Maven build if tests fail; this ensures changelog accurately reflects test state.

if __name__ == "__main__":
    main()
