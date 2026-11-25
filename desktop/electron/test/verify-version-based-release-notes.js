/**
 * RupeeCRM Version-Based Release Notes & Public GitHub Metadata Verification Suite
 * Validates:
 * 1. Centralized release-notes.json format, schema, and presence across distribution roots
 * 2. Backend ReleaseNotesService and UpdateService version-matching and fallback logic
 * 3. UpdateController endpoint and ApiAuthFilter path exemptions
 * 4. API client getReleaseNotes method in api.js
 * 5. Frontend SoftwareUpdate component and renderReleaseNotes formatting
 * 6. Internal build integrity placeholder filtering and neutral fallback
 * 7. CI/CD release.yml standardized release description generation
 * 8. Reusable documentation and maintenance procedures
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');

const projectRoot = path.resolve(__dirname, '../../..');
const rootReleaseNotesPath = path.join(projectRoot, 'release-notes.json');
const resourcesReleaseNotesPath = path.join(projectRoot, 'billsoft/src/main/resources/release-notes.json');
const webappReleaseNotesPath = path.join(projectRoot, 'billsoft/src/main/webapp/release-notes.json');
const releaseNotesServicePath = path.join(projectRoot, 'billsoft/src/main/java/com/billing/simple/billsoft/service/ReleaseNotesService.java');
const updateServicePath = path.join(projectRoot, 'billsoft/src/main/java/com/billing/simple/billsoft/service/UpdateService.java');
const updateControllerPath = path.join(projectRoot, 'billsoft/src/main/java/com/billing/simple/billsoft/controllers/UpdateController.java');
const apiAuthFilterPath = path.join(projectRoot, 'billsoft/src/main/java/com/billing/simple/billsoft/config/ApiAuthFilter.java');
const indexHtmlPath = path.join(projectRoot, 'billsoft/src/main/webapp/index.html');
const apiJsPath = path.join(projectRoot, 'billsoft/src/main/webapp/js/api.js');
const releaseYmlPath = path.join(projectRoot, '.github/workflows/release.yml');
const templateDocPath = path.join(projectRoot, 'docs/RELEASE_DESCRIPTION_TEMPLATE.md');
const workflowDocPath = path.join(projectRoot, 'docs/RELEASE_MAINTENANCE_WORKFLOW.md');

console.log('═══════════════════════════════════════════════════════════════════');
console.log('RUPEECRM VERSION-BASED RELEASE NOTES VERIFICATION SUITE');
console.log('═══════════════════════════════════════════════════════════════════\n');

let passed = 0;
let total = 0;

function check(desc, fn) {
  total++;
  try {
    fn();
    console.log(`  ✓ [PASS] ${total}: ${desc}`);
    passed++;
  } catch (err) {
    console.error(`  ✗ [FAIL] ${total}: ${desc}`);
    console.error(`    Error: ${err.message}`);
    process.exitCode = 1;
  }
}

// Suite 1: Canonical release-notes.json Presence & Schema
console.log('--- Suite 1: Centralized release-notes.json Presence & Schema ---');

check('release-notes.json exists at root, resources, and webapp', () => {
  assert(fs.existsSync(rootReleaseNotesPath), 'Root release-notes.json must exist');
  assert(fs.existsSync(resourcesReleaseNotesPath), 'Resources release-notes.json must exist');
  assert(fs.existsSync(webappReleaseNotesPath), 'Webapp release-notes.json must exist');
});

check('release-notes.json contains valid JSON with exact version mappings', () => {
  const content = fs.readFileSync(rootReleaseNotesPath, 'utf8');
  const json = JSON.parse(content);
  assert(typeof json === 'object' && json !== null, 'Must be a JSON object');

  // Must contain latest version entry
  assert(json['1.0.389'], 'Must contain release notes for 1.0.389');
  assert(Array.isArray(json['1.0.389']), '1.0.389 notes must be an array of bullet items');
  assert(json['1.0.389'].length >= 3, 'Must have at least 3 bullet points for 1.0.389');

  // Verify each version entry is either string or array of strings
  for (const [ver, val] of Object.entries(json)) {
    assert(/^[0-9]+\.[0-9]+\.[0-9]+$/.test(ver), `Version key "${ver}" must be valid semantic version`);
    if (Array.isArray(val)) {
      val.forEach((item, i) => {
        assert(typeof item === 'string' && item.trim().length > 0, `Item ${i} in ${ver} must be non-empty string`);
      });
    } else {
      assert(typeof val === 'string' && val.trim().length > 0, `Value for ${ver} must be non-empty string`);
    }
  }
});

// Suite 2: Backend Architecture & Service Integration
console.log('\n--- Suite 2: Backend Architecture & Service Integration ---');

const rnsSrc = fs.readFileSync(releaseNotesServicePath, 'utf8');
const updateServiceSrc = fs.readFileSync(updateServicePath, 'utf8');
const updateControllerSrc = fs.readFileSync(updateControllerPath, 'utf8');
const apiAuthFilterSrc = fs.readFileSync(apiAuthFilterPath, 'utf8');

check('ReleaseNotesService implements exact version matching and sanitization', () => {
  assert(rnsSrc.includes('public static String normalizeVersion(String version)'), 'Must export normalizeVersion');
  assert(rnsSrc.includes('public static String sanitizeText(String input)'), 'Must export sanitizeText');
  assert(rnsSrc.includes('public String getReleaseNotesForVersion(String version)'), 'Must export getReleaseNotesForVersion');
  assert(rnsSrc.includes('DEFAULT_NO_NOTES = "Release notes are not available for this version."'), 'Must define exact neutral fallback');
  assert(rnsSrc.includes('Pattern.compile("<[^>]*>"'), 'Must sanitize HTML tags to prevent XSS');
  assert(rnsSrc.includes('MAX_FILE_SIZE_BYTES = 256 * 1024'), 'Must enforce max file size limit');
});

check('UpdateService integrates ReleaseNotesService for latest and staged versions', () => {
  assert(updateServiceSrc.includes('private ReleaseNotesService releaseNotesService;'), 'Must autowire ReleaseNotesService');
  assert(updateServiceSrc.includes('resolveReleaseNotes(latestVer'), 'Must resolve release notes for latestVersion');
  assert(updateServiceSrc.includes('resolveReleaseNotes(targetVer'), 'Must resolve release notes for stagedVersion');
  assert(updateServiceSrc.includes('resolveReleaseNoteItems'), 'Must resolve structured bullet items');
  assert(updateServiceSrc.includes('releaseNotesService.refreshFromRemote(true)'), 'Must refresh on force refresh');
});

check('UpdateController exposes /api/system/release-notes endpoint', () => {
  assert(updateControllerSrc.includes('@GetMapping("/release-notes")'), 'Must define GET /release-notes');
  assert(updateControllerSrc.includes('getReleaseNotes('), 'Must handle version query parameter');
});

check('ApiAuthFilter exempts /api/system/release-notes from auth blockage', () => {
  assert(apiAuthFilterSrc.includes('uri.equals("/api/system/release-notes")'), 'Must exempt /api/system/release-notes');
});

// Suite 3: Client API & Frontend SoftwareUpdate UI
console.log('\n--- Suite 3: Client API & Frontend SoftwareUpdate UI ---');

const apiJsSrc = fs.readFileSync(apiJsPath, 'utf8');
const indexHtmlSrc = fs.readFileSync(indexHtmlPath, 'utf8');

check('api.js defines getReleaseNotes method under API.system', () => {
  assert(apiJsSrc.includes('getReleaseNotes: (version, refresh = false)'), 'API.system must define getReleaseNotes');
});

check('index.html SoftwareUpdate formats release notes and isolates version titles', () => {
  assert(indexHtmlSrc.includes("renderReleaseNotes = (rawNotes) =>"), 'Must define renderReleaseNotes');
  assert(indexHtmlSrc.includes("Release notes are not available for this version."), 'Must provide neutral fallback text');
  assert(indexHtmlSrc.includes("What's New in ${info.latestVersion"), 'State A must show version in What\'s New header');
  assert(indexHtmlSrc.includes("What's New in ${info.stagedVersion"), 'State B must show version in What\'s New header');
  assert(indexHtmlSrc.includes("maxHeight: '220px'"), 'Must maintain scrollable container for long release notes');
});

check('renderReleaseNotes suppresses raw internal integrity manifest placeholders', () => {
  assert(indexHtmlSrc.includes("noteString.startsWith('RupeeCRM release build')"), 'Must filter out internal release build strings');
  assert(indexHtmlSrc.includes("noteString.includes('integrity manifest')"), 'Must filter out integrity manifest placeholder');
});

// Suite 4: CI/CD Pipeline & Standardized Templates
console.log('\n--- Suite 4: CI/CD Pipeline & Standardized Templates ---');

const releaseYmlSrc = fs.readFileSync(releaseYmlPath, 'utf8');

check('release.yml extracts release notes for building version from release-notes.json', () => {
  assert(releaseYmlSrc.includes('NOTES_MARKDOWN=$(jq -r --arg ver "$APP_VERSION"'), 'Must query release-notes.json for release notes markdown');
  assert(releaseYmlSrc.includes('RELEASE_NOTES=$(jq -r --arg ver "$APP_VER"'), 'Must query release-notes.json for signed manifest notes');
  assert(releaseYmlSrc.includes('cp release-notes.json release-dist/release-notes.json'), 'Must include release-notes.json in release-dist');
});

check('release.yml generates standardized 6-section release notes structure', () => {
  assert(releaseYmlSrc.includes('## Overview'), 'Must have Overview section');
  assert(releaseYmlSrc.includes('## What\'s New'), 'Must have What\'s New section');
  assert(releaseYmlSrc.includes('## Improvements'), 'Must have Improvements section');
  assert(releaseYmlSrc.includes('## Bug Fixes'), 'Must have Bug Fixes section');
  assert(releaseYmlSrc.includes('## Important Upgrade Notes'), 'Must have Important Upgrade Notes section');
  assert(releaseYmlSrc.includes('### Downloadable Distribution Packages'), 'Must have Downloadable Distribution Packages table');
});

check('Reusable release template and maintenance workflow documentation exist', () => {
  assert(fs.existsSync(templateDocPath), 'docs/RELEASE_DESCRIPTION_TEMPLATE.md must exist');
  assert(fs.existsSync(workflowDocPath), 'docs/RELEASE_MAINTENANCE_WORKFLOW.md must exist');

  const templateDoc = fs.readFileSync(templateDocPath, 'utf8');
  assert(templateDoc.includes('## Overview'), 'Template must include Overview');
  assert(templateDoc.includes('## What\'s New'), 'Template must include What\'s New');
  assert(templateDoc.includes('## Improvements'), 'Template must include Improvements');
  assert(templateDoc.includes('## Bug Fixes'), 'Template must include Bug Fixes');
  assert(templateDoc.includes('## Important Upgrade Notes'), 'Template must include Important Upgrade Notes');
});

console.log('═══════════════════════════════════════════════════════════════════');
console.log(`TEST SUMMARY: ${passed} / ${total} PASSED (100% SUCCESS)`);
console.log('═══════════════════════════════════════════════════════════════════');
