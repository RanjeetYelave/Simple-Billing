# RupeeCRM Production Release Description Template

Use this standardized structure for all RupeeCRM GitHub releases and release announcements.

---

```markdown
# RupeeCRM v{VERSION}

**Release Date:** {MONTH} {DAY}, {YEAR}  
**Application Version:** `v{VERSION}` (Build {BUILD_NUMBER})  
**Branch:** `PROD`  

---

## Overview

{1-2 paragraphs concisely explaining the business value, major features, or core focus of this release for prospective users and existing customers.}

---

## What's New

- **Feature 1:** {Description of major new user-facing capability.}
- **Feature 2:** {Description of major new user-facing capability.}
- **Feature 3:** {Description of major new user-facing capability.}

---

## Improvements

- **Improvement 1:** {Enhancement to existing functionality, UI layout, speed, or usability.}
- **Improvement 2:** {System optimization, isolated transaction handling, or workflow simplification.}

---

## Bug Fixes

- **Fix 1:** {Remedy for specific defect or unexpected behavior.}
- **Fix 2:** {Resolution of edge case or regression.}

---

## Important Upgrade Notes

{Include only when applicable: e.g. schema migration notes, backup recommendations, or configuration reminders. If none, state: "Automatic database schema updates are applied seamlessly upon launch; existing data and licenses are 100% preserved."}

---

### Downloadable Distribution Packages

| Package Type | File | Size | SHA256 Checksum |
|---|---|---|---|
| **Windows x64 Installer (Versioned)** | [`RupeeCRM-Setup-{VERSION}-x64.exe`](https://github.com/RanjeetYelave/Simple-Billing/releases/download/v{VERSION}/RupeeCRM-Setup-{VERSION}-x64.exe) | `{SIZE}` | `{SHA256}` |
| **Windows x64 Installer (Latest)** | [`RupeeCRM-Setup.exe`](https://github.com/RanjeetYelave/Simple-Billing/releases/download/v{VERSION}/RupeeCRM-Setup.exe) | `{SIZE}` | `{SHA256}` |
| **macOS ARM64 DMG (Versioned)** | [`RupeeCRM-{VERSION}-arm64.dmg`](https://github.com/RanjeetYelave/Simple-Billing/releases/download/v{VERSION}/RupeeCRM-{VERSION}-arm64.dmg) | `{SIZE}` | `{SHA256}` |
| **macOS ARM64 DMG (Latest)** | [`RupeeCRM-arm64.dmg`](https://github.com/RanjeetYelave/Simple-Billing/releases/download/v{VERSION}/RupeeCRM-arm64.dmg) | `{SIZE}` | `{SHA256}` |
| **In-App OTA Application Package** | [`rupeecrm.war`](https://github.com/RanjeetYelave/Simple-Billing/releases/download/v{VERSION}/rupeecrm.war) | `~84 MB` | See `SHA256SUMS.txt` |
| **Legacy OTA Package** | [`billsoft.war`](https://github.com/RanjeetYelave/Simple-Billing/releases/download/v{VERSION}/billsoft.war) | `~84 MB` | See `SHA256SUMS.txt` |
| **Dedicated Manual OTA Update Package** | [`RupeeCRM-Update-{VERSION}.zip`](https://github.com/RanjeetYelave/Simple-Billing/releases/download/v{VERSION}/RupeeCRM-Update-{VERSION}.zip) | `~81 MB` | See `.sha256` |
| **Integrity Checksums** | [`SHA256SUMS.txt`](https://github.com/RanjeetYelave/Simple-Billing/releases/download/v{VERSION}/SHA256SUMS.txt) | `<1 KB` | Checksum Manifest |

### Verification (PowerShell)
```powershell
(Get-FileHash -Algorithm SHA256 .\RupeeCRM-Setup-{VERSION}-x64.exe).Hash -eq "{SHA256}"
```

### Verification (macOS Terminal)
```bash
shasum -a 256 RupeeCRM-{VERSION}-arm64.dmg
```

> [!NOTE]
> Official stable production release v{VERSION}. All distributable packages are published and downloadable.
```
