# RupeeCRM (Simple-Billing)

RupeeCRM is a fast, offline-first desktop billing and business management platform. It combines an embedded Spring Boot backend engine with a native supervisor launcher, desktop tray integration, local H2 persistence, and automated rollbacks.

---

## Quick Installation

### Windows (1-Line PowerShell Quick Install)
Open PowerShell (no administrator privileges required) and run:

```powershell
irm https://raw.githubusercontent.com/RanjeetYelave/Simple-Billing/overhaul/tools/install-windows.ps1 | iex
```

#### Alternative Method (Download & Inspect First):
If you prefer to review the script prior to execution:
```powershell
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/RanjeetYelave/Simple-Billing/overhaul/tools/install-windows.ps1" -OutFile install-windows.ps1
.\install-windows.ps1
```

### macOS (Apple Silicon ARM64)
Open Terminal and run:

```bash
curl -fsSL https://raw.githubusercontent.com/RanjeetYelave/Simple-Billing/overhaul/tools/install-mac.sh | bash
```

---

## Windows System Requirements & Details

* **Operating System**: Windows 10 or Windows 11 (64-bit x64 / AMD64). Windows on ARM64 runs via built-in x64 compatibility.
* **Java**: **No Java installation required**. The release package bundles a standalone, lightweight JRE.
* **Privileges**: **No Administrator privileges required**. Installs cleanly in the current user's profile.
* **Internet**: Required only during the initial package download from GitHub Releases. Once installed, RupeeCRM is 100% offline-first.

---

## Application Layout & Data Safety

RupeeCRM strictly segregates application executables from customer business data:

| Component | Windows Location | Description |
| :--- | :--- | :--- |
| **Application Binaries** | `%LOCALAPPDATA%\Programs\RupeeCRM\` | Contains `RupeeCRM.exe`, embedded JRE runtime, and billing WAR engine. Replaced cleanly during updates. |
| **Customer Data** | `%APPDATA%\SimpleBilling\` | Contains your H2 database (`database.mv.db`), historical snapshots (`backup/`), and logs. **Never deleted during updates or standard uninstalls.** |
| **Shortcuts** | Desktop & Start Menu | `RupeeCRM.lnk` for 1-click launch. |
| **Auto-Start** | `HKCU\...\Run` & Startup VBS | Starts RupeeCRM quietly in background on Windows login. |

---

## Updates & Reinstalls

To update RupeeCRM to the latest version, simply run the installation command again:
```powershell
irm https://raw.githubusercontent.com/RanjeetYelave/Simple-Billing/overhaul/tools/install-windows.ps1 | iex
```
* The installer safely stops any active background supervisor.
* Downloads the latest package and verifies the SHA-256 checksum.
* Atomically swaps the application binaries in `%LOCALAPPDATA%\Programs\RupeeCRM\`.
* Your customer database (`%APPDATA%\SimpleBilling\`) remains completely untouched.
* The supervisor restarts automatically.

---

## Uninstallation

To uninstall RupeeCRM while preserving your customer data:

```powershell
.\tools\uninstall-windows.ps1
```
* Stops all active RupeeCRM background processes.
* Removes application binaries from `%LOCALAPPDATA%\Programs\RupeeCRM`.
* Removes Desktop and Start Menu shortcuts.
* Cleans up auto-start registry entries and Startup folder scripts.
* **Customer Data Guarantee**: All invoices, customers, and database files in `%APPDATA%\SimpleBilling\` are preserved.

---

## Security & Integrity Guarantees

1. **SHA-256 Verification**: Every downloaded release archive is cryptographically verified against the authoritative release checksum prior to extraction.
2. **Encrypted Transport**: All communication with GitHub Releases uses HTTPS / TLS 1.2+.
3. **No Embedded Credentials**: Scripts contain zero private tokens, API keys, or embedded secrets.
4. **No Security Bypasses**: The installer does not disable Windows Defender, SmartScreen, or modify firewall/antivirus policies.
5. **No System Pollution**: Does not modify global `PATH`, `JAVA_HOME`, or system directories.

---

## Development Workflow

### 1. Build Reactor with Maven
```bash
./mvnw clean package -DskipTests
```

### 2. Run Locally
```bash
java -jar launcher/target/launcher-0.0.1-SNAPSHOT.jar
```
Or run the Spring Boot web engine directly:
```bash
./mvnw spring-boot:run -pl billsoft
```

### 3. Run Backend Regression Tests
```bash
./mvnw test -pl billsoft
```

### 4. Run UI Regression Suite (Playwright)
```bash
cd ui-regression
npm test
```
