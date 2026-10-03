# RupeeCRM (Simple-Billing)

[![Build and Test](https://github.com/RanjeetYelave/Simple-Billing/actions/workflows/regression-tests.yml/badge.svg)](https://github.com/RanjeetYelave/Simple-Billing/actions/workflows/regression-tests.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.3-green.svg)](https://spring.io/projects/spring-boot)
[![Playwright](https://img.shields.io/badge/Playwright-97%20E2E%20Tests-purple.svg)](https://playwright.dev/)

**RupeeCRM** is a modern, high-performance, **100% offline-first desktop billing, CRM, and business management platform** tailored for small-to-medium enterprises, retail stores, traders, and service businesses.

Built on an embedded Spring Boot engine with a native supervisor, desktop system tray control center, local H2 persistence, and an intelligent natural language search system, RupeeCRM delivers cloud-grade ERP capabilities directly to the local desktop without cloud dependency, subscription locks, or internet requirements.

---

## Key Highlights

- **100% Offline-First Architecture**: Zero external network dependency for billing, invoicing, customer management, inventory, and reporting. All data resides securely on the local device.
- **Single Unified MSI Installer (`RupeeCRMSetup.msi`)**: Fast, 1-click Windows installer with zero configuration. No PowerShell, manual Java installation, or ZIP extraction required.
- **Natural Language Omnibar (NLP Engine)**: Powered by Apache OpenNLP and a custom arithmetic AST parser. Supports complex calculations, unit conversions, multi-lingual vernacular queries (English, Hindi, Marathi, Devanagari numerals), Indian numbering systems (Lakhs/Crores), and instant entity lookup.
- **Zero-Loss Data Isolation**: Clean, strict separation between replaceable application binaries (`%LOCALAPPDATA%\Programs\RupeeCRM\`) and persistent customer business data (`%LOCALAPPDATA%\RupeeCRM\data\`), guaranteeing seamless, risk-free updates and automated backups.
- **Native Tray Control Center & Diagnostics**: One-click dashboard access, background service restart, automated health diagnostics, and safe backup/restore directly from the system tray.
- **Complete Business Suite**: Invoicing, Customer 360, Inventory, Quotations, Challans, Planner Kanban, HR/Payroll, Financial BI, and Multi-Firm isolation in a single unified interface.

---

## Core Feature Modules

### 1. Invoicing & Paperwork Hub
- **GST & Non-GST Invoicing**: Rapid billing interface with automatic tax calculations (Forward & Reverse GST, CGST/SGST/IGST), custom discounts, and payment terms.
- **Quotations & Estimates**: Create commercial quotations with 1-click conversion into finalized invoices.
- **Delivery Challans & Proforma Invoices**: Complete paperwork lifecycle from proforma creation to dispatch challans and final settlement.
- **Flexible Settlement**: Supports Split Payments, Cash, UPI, Credit Cards, Bank Transfers, and Partial Balances with instant receipt printing and PDF generation.

### 2. Customer 360 & CRM
- **Customer Directory**: Centralized management of client contact records, tax IDs, and billing preferences.
- **Customer 360 Ledger**: Comprehensive timeline view of all invoices, settlements, balance dues, and payment histories per client.
- **Accounts Receivable Aging**: Automated aging bucket calculations (0–30, 31–60, 61–90, 90+ days) to track overdue payments and streamline collections.

### 3. Inventory & Product Catalog
- **Stock Tracking & Adjustments**: Real-time multi-category product catalog with low-stock alerts, SKU/barcode lookup, and bulk price updates.
- **Trade & Vernacular Units**: Built-in awareness of standard and Indian trade units (Pieces, Kilograms, Quintals, Litres, Meters, Dozens, Boxes, Gross).

### 4. Intelligent Omnibar & NLP Calculation Engine
- **Deterministic Math AST**: Immediate evaluation of mathematical formulas, chained expressions, and tax percentages.
- **Vernacular & Multilingual Support**: Resolves Devanagari numerals (०–९), colloquial quantifiers (*ek lakh*, *dedh so*, *panch hazar*), and bilingual business queries.
- **Dimensional Unit & Currency Conversion**: Instant cross-unit conversions (Weight, Volume, Length, Area, Speed, Storage, Temperature) and global currency reference benchmarks.
- **Smart Business Actions**: Natural language quick actions (e.g., `"Expense Fuel 500 UPI"`, `"Remind payment due tomorrow"`).

### 5. Financial BI, Accounting & Analytics
- **Business Intelligence (BI)**: Top/Bottom N customer rankings, best-selling products, and gross margin analytics.
- **Period Trend Comparisons**: Month-over-Month (MoM) and Day-over-Day (DoD) growth evaluations.
- **Expense Tracking**: Categorized operating expenses with payment method reconciliation.

### 6. HR, Staff & Productivity Planner
- **Staff Directory & Attendance**: Employee profile management with daily attendance logging and automated payroll calculation.
- **Planner Kanban Board**: Visual task tracking board with drag-and-drop workflow stages and sticky planner notes.

### 7. Multi-Firm & Enterprise Security
- **Multi-Tenant / Multi-Firm Isolation**: Seamlessly manage multiple business entities within a single installation with strict data boundary isolation.
- **Data Protection & Encrypted Tokens**: Cryptographically secured session tokens via Windows DPAPI (`CryptProtectData`), audit logging, and automated local database snapshot backups.

---

## Architecture Overview

```
+-------------------------------------------------------------+
|                      User Experience                        |
|  Responsive Web UI / Chromium Shell / Desktop Notifications |
+-------------------------------------------------------------+
                               |
                               v (HTTP / REST API - 127.0.0.1:28080)
+-------------------------------------------------------------+
|                 RupeeCRM Supervisor Launcher                |
|  - System Tray Control Center (Windows / macOS)             |
|  - Process Lifecycle, Health Monitoring & Self-Healing      |
|  - Atomic Update & Rollback Engine                          |
+-------------------------------------------------------------+
                               |
                               v (Embedded JVM / WAR)
+-------------------------------------------------------------+
|               Spring Boot Core Backend Engine               |
|  - Spring Web MVC Controllers & REST APIs                   |
|  - OpenNLP Intent Classifier & AST Calculation Engine       |
|  - Business Services (Invoices, Customers, Stock, HR, BI)   |
|  - Data Protection & DPAPI Credential Store                 |
+-------------------------------------------------------------+
                               |
                               v (JPA / Hibernate / JDBC)
+-------------------------------------------------------------+
|                    Embedded Local Storage                   |
|  - H2 Database (%LOCALAPPDATA%\RupeeCRM\data\database)      |
|  - Automated Snapshots (%LOCALAPPDATA%\RupeeCRM\backups\)   |
|  - Complete Zero-Cloud Data Privacy                         |
+-------------------------------------------------------------+
```

---

## Installation Guide

### Windows Installation

1. Download **`RupeeCRMSetup.msi`** from the latest [GitHub Release](https://github.com/RanjeetYelave/Simple-Billing/releases).
2. Double-click the installer and follow the wizard.
3. RupeeCRM will start automatically in your Windows System Tray and open your billing dashboard in your default browser.

#### Windows System Details:
- **OS Support**: Windows 10 or Windows 11 (64-bit x64 / AMD64). Windows on ARM64 runs via built-in x64 emulation.
- **Zero External Dependencies**: Bundles an embedded Eclipse Temurin JRE 21 runtime. **No pre-installed Java is required.**
- **Non-Admin Installation**: Installs to `%LOCALAPPDATA%\Programs\RupeeCRM` with user-level registry auto-start (`HKCU Run`).

### macOS (Apple Silicon ARM64)

Open Terminal and run:

```bash
curl -fsSL https://raw.githubusercontent.com/RanjeetYelave/Simple-Billing/overhaul/tools/install-mac.sh | bash
```

---

## Data Safety & Directory Separation

RupeeCRM strictly segregates application executables from customer business data:

| Component | Windows Location | Purpose / Policy |
| :--- | :--- | :--- |
| **Application Binaries** | `%LOCALAPPDATA%\Programs\RupeeCRM\` | Contains `RupeeCRM.exe`, embedded JRE runtime, and billing WAR engine. Replaced cleanly during updates and repairs. |
| **Customer Data** | `%LOCALAPPDATA%\RupeeCRM\data\` | Contains your live H2 database (`database.mv.db`), machine identity (`mid.dat`), and license (`license.lic`). **Never deleted during updates, repairs, or standard uninstalls.** |
| **Automated Backups** | `%LOCALAPPDATA%\RupeeCRM\backups\` | Contains daily and pre-update snapshots. Preserved across all lifecycle operations. |
| **Logs** | `%LOCALAPPDATA%\RupeeCRM\logs\` | Contains supervisor and backend diagnostic logs. |
| **Shortcuts** | Desktop & Start Menu | `RupeeCRM.lnk` for 1-click launch. |
| **Auto-Start** | `HKCU\...\Run\RupeeCRM` | Starts RupeeCRM quietly in the background on Windows login (user-toggleable in Settings). |

---

## Maintenance, Updates & Uninstallation

### Updating
Updates are checked automatically via the System Tray or when running `RupeeCRMSetup.msi`. Before applying an update, RupeeCRM gracefully stops the backend, verifies the release signature (Ed25519) and SHA-256 hash, creates a pre-update safety backup, and replaces the binaries. If the updated backend fails to start, the supervisor automatically rolls back to the previous version.

### Repairing
If application binaries are damaged, select **Repair RupeeCRM** in the System Tray or run `RupeeCRMSetup.msi` → **Repair**. All application binaries are restored while customer invoices, databases, and licenses are strictly preserved.

### Uninstalling
To remove RupeeCRM while keeping your customer data:
1. Open Windows **Settings** → **Installed Apps**.
2. Select **RupeeCRM** → **Uninstall**.
3. Application binaries, shortcuts, and autostart entries are removed, while your customer data in `%LOCALAPPDATA%\RupeeCRM\data` remains safe for future reinstall.

---

## Development & Testing

### Prerequisites
- **JDK 21** (Temurin / OpenJDK)
- **Maven 3.9+** (or included `./mvnw` wrapper)
- **Node.js 18+** & **npm** (for Playwright UI regression suite)

### 1. Build the Full Project
```bash
./mvnw clean package -DskipTests
```

### 2. Run Spring Boot Backend Directly
```bash
./mvnw spring-boot:run -pl billsoft
```
Access the application at `http://127.0.0.1:28080/`.

### 3. Run Backend Test Suite (612+ Tests)
```bash
./mvnw clean test
```

### 4. Run Playwright UI Regression Suite (97 Tests)
```bash
cd ui-regression
npm test
```

---

## Security & Privacy Principles

1. **Local Privacy**: Your financial records, customer contacts, and transaction ledger never leave your machine.
2. **Ed25519 & SHA-256 Release Signatures**: Every release manifest is cryptographically signed and verified before deployment.
3. **Genuine Windows DPAPI**: Critical tokens and vault credentials are encrypted using OS-level `CryptProtectData`.
4. **H2 Production Hardening**: Production Web Console disabled; server binds exclusively to `127.0.0.1`.
5. **Safe Process Management**: Tracks backend by PID; never executes blanket `taskkill /IM java.exe`.
6. **Clean Footprint**: Does not modify global environment variables (`PATH`, `JAVA_HOME`) or pollute system-level directories.

---

## License

This project is licensed under the [MIT License](LICENSE).
