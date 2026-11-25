# RupeeCRM Release Notes Maintenance Workflow

This document outlines the standard procedure for authoring, verifying, and publishing version-based release notes for RupeeCRM.

---

## 1. Authoritative Release Notes File

The single authoritative file is:
`release-notes.json` (at the root of the repository, mirrored in `billsoft/src/main/resources/release-notes.json` and `billsoft/src/main/webapp/release-notes.json`).

### Schema
```json
{
  "1.0.390": [
    "First bullet point describing feature or improvement.",
    "Second bullet point describing fix or performance optimization."
  ],
  "1.0.389": [
    "..."
  ]
}
```
*Note: Both string arrays (for multiple bullet points) and single strings are supported.*

---

## 2. Step-by-Step Procedure for Every New Release

### Step 1: Add the Exact Version Entry to `release-notes.json`
- Open `release-notes.json` in the repository root.
- Add the new version key matching the upcoming release (e.g. `"1.0.390"`).
- Write concise, professional, user-facing bullet points explaining what changed.
- Copy/sync the file to:
  - `billsoft/src/main/resources/release-notes.json`
  - `billsoft/src/main/webapp/release-notes.json`

### Step 2: Review for Security & Privacy
Before committing, ensure release notes:
- Contain **no internal development jargon, test logs, or error stack traces**.
- Contain **no customer data, credentials, encryption keys, or internal IP/paths**.
- Contain **no unreviewed commit hashes or automated git commit messages**.
- Prevent XSS by using plain text (safe markdown like `**bold**` or `` `code` `` is supported).

### Step 3: Commit and Push to `PROD`
Commit the updated `release-notes.json` alongside your implementation changes:
```bash
git add release-notes.json billsoft/src/main/resources/release-notes.json billsoft/src/main/webapp/release-notes.json
git commit -m "docs(release): add release notes for v1.0.390"
git push origin PROD
```

### Step 4: Automated CI/CD Execution
The GitHub Actions workflow automatically:
1. Reads `release-notes.json` for the building version (`$APP_VERSION`).
2. Embeds the user-facing release notes into `manifest.json` and cryptographically signs it with Ed25519.
3. Formats `release-notes.md` using the standardized template and attaches it to the GitHub Release.
4. Bundles `release-notes.json` as a release distribution asset.

### Step 5: Verification in Application
1. Launch RupeeCRM and navigate to **Settings → Software Update**.
2. Verify that **What's New in {VERSION}:** displays the exact bullet points from `release-notes.json`.
3. If the version is not in `release-notes.json`, verify that the neutral fallback is displayed:
   `"Release notes are not available for this version."`
4. Confirm that download, staging, and installation functionality remains unaffected.
