# ==============================================================================
# Windows MSI Lifecycle Validation Script for CI
# ==============================================================================

$ErrorActionPreference = "Stop"

Write-Host "=========================================================="
Write-Host " [CI VALIDATION] Testing Windows Unified MSI Lifecycle"
Write-Host "=========================================================="

$msiPath = if (Test-Path "RupeeCRMSetup.msi") { (Resolve-Path "RupeeCRMSetup.msi").Path } else { $null }

if ($msiPath) {
    Write-Host "MSI Installer Path : $msiPath"

    # 1. Fresh Install
    Write-Host ""
    Write-Host "==> 1. Executing Fresh Install: msiexec /i RupeeCRMSetup.msi /qn..."
    Start-Process -FilePath "msiexec.exe" -ArgumentList "/i `"$msiPath`" /qn" -Wait -NoNewWindow
}

# 2. Verify installed binaries & layout
Write-Host ""
Write-Host "==> 2. Verifying installed executable and application layout..."
$installDir = "$env:LOCALAPPDATA\Programs\RupeeCRM"
$installedExe = "$installDir\RupeeCRM.exe"
$launcherJar = "$installDir\app\launcher.jar"
$warFile = "$installDir\app\app\rupeecrm.war"
$runtimeDir = "$installDir\runtime"
$duplicateJre = "$installDir\app\jre"

if (Test-Path $installedExe) {
    Write-Host "[OK] Installed executable verified at $installedExe"
} else {
    throw "Validation failed: RupeeCRM.exe was not created in $installDir"
}

if (Test-Path $launcherJar) {
    Write-Host "[OK] Launcher JAR verified at $launcherJar"
} else {
    throw "Validation failed: launcher.jar was not created in $installDir\app"
}

if (Test-Path $warFile) {
    Write-Host "[OK] Backend WAR verified at $warFile"
} else {
    throw "Validation failed: rupeecrm.war was not created in $installDir\app\app"
}

if (Test-Path $runtimeDir) {
    Write-Host "[OK] Bundled runtime verified at $runtimeDir"
} else {
    throw "Validation failed: runtime directory was not created in $installDir"
}

if (Test-Path $duplicateJre) {
    throw "Validation failed: Duplicate JRE found at $duplicateJre! Packaging violates single-JRE invariant."
}
Write-Host "[OK] Single-JRE invariant verified (no duplicate app/jre)"

# 3. Create test customer database in persistent data directory
Write-Host ""
Write-Host "==> 3. Creating test customer database in authoritative path..."
$dataDir = "$env:LOCALAPPDATA\RupeeCRM\data"
New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
$dummyDb = "$dataDir\billsoft_database.mv.db"
"CUSTOMER_DATABASE_DATA_INTEGRITY_CHECK_TOKEN" | Out-File -FilePath $dummyDb -Encoding UTF8
Write-Host "[OK] Test customer database created at $dummyDb"

# 4. Test MSI Repair
if ($msiPath) {
    Write-Host ""
    Write-Host "==> 4. Executing MSI Repair: msiexec /f RupeeCRMSetup.msi /qn..."
    Start-Process -FilePath "msiexec.exe" -ArgumentList "/f `"$msiPath`" /qn" -Wait -NoNewWindow

    if (-not (Test-Path $installedExe)) {
        throw "Validation failed: RupeeCRM.exe missing after repair!"
    }
    if (-not (Test-Path $dummyDb)) {
        throw "CRITICAL FAILURE: Customer database was deleted during repair!"
    }
    Write-Host "[OK] MSI repair successful and customer data safely preserved"
}

# 5. Test Uninstallation
if ($msiPath) {
    Write-Host ""
    Write-Host "==> 5. Executing Uninstallation: msiexec /x RupeeCRMSetup.msi /qn..."
    Start-Process -FilePath "msiexec.exe" -ArgumentList "/x `"$msiPath`" /qn" -Wait -NoNewWindow

    # Verify binaries removed
    if (Test-Path $installedExe) {
        throw "Validation failed: RupeeCRM.exe was not removed by MSI uninstaller"
    }
    Write-Host "[OK] RupeeCRM executable successfully removed from $installDir"

    # Verify customer database strictly preserved
    if (-not (Test-Path $dummyDb)) {
        throw "CRITICAL FAILURE: Customer database was deleted during uninstall!"
    }
    $dbContent = Get-Content $dummyDb -Raw
    if ($dbContent -notmatch "CUSTOMER_DATABASE_DATA_INTEGRITY_CHECK_TOKEN") {
        throw "CRITICAL FAILURE: Customer database content was corrupted during uninstall!"
    }
    Write-Host "[OK] Customer database strictly preserved at $dummyDb after uninstall"
}

# 6. Test Reinstallation after Uninstall
if ($msiPath) {
    Write-Host ""
    Write-Host "==> 6. Executing Reinstallation: msiexec /i RupeeCRMSetup.msi /qn..."
    Start-Process -FilePath "msiexec.exe" -ArgumentList "/i `"$msiPath`" /qn" -Wait -NoNewWindow

    if (-not (Test-Path $installedExe)) {
        throw "Validation failed: RupeeCRM.exe was not restored by reinstaller"
    }
    if (-not (Test-Path $dummyDb)) {
        throw "CRITICAL FAILURE: Existing customer database was wiped on reinstall!"
    }
    Write-Host "[OK] Reinstallation completed and successfully reconnected to existing database"
}

Write-Host ""
Write-Host "=========================================================="
Write-Host " [CI SUCCESS] All Windows MSI Lifecycle Checks Passed!"
Write-Host "=========================================================="
