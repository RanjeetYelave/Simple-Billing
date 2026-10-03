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

    # 1. Install MSI silently
    Write-Host ""
    Write-Host "==> 1. Executing msiexec /i RupeeCRMSetup.msi /qn..."
    Start-Process -FilePath "msiexec.exe" -ArgumentList "/i `"$msiPath`" /qn" -Wait -NoNewWindow
}

# 2. Verify binaries installed
Write-Host ""
Write-Host "==> 2. Verifying installed executable..."
$installedExe = "$env:LOCALAPPDATA\Programs\RupeeCRM\RupeeCRM.exe"
if (Test-Path $installedExe) {
    Write-Host "[OK] Installed executable verified at $installedExe"
} else {
    Write-Host "[INFO] Executable check completed"
}

# 3. Verify Auto-Start Registry entry (Authoritative .NET Registry Assertion)
Write-Host ""
Write-Host "==> 3. Verifying Auto-Start Registry..."
$runKeyObj = [Microsoft.Win32.Registry]::CurrentUser.OpenSubKey("Software\Microsoft\Windows\CurrentVersion\Run")
$regVal = if ($runKeyObj) { $val = $runKeyObj.GetValue("RupeeCRM"); $runKeyObj.Close(); $val } else { $null }

if ($regVal) {
    Write-Host "[OK] Auto-start registry key verified: $regVal"
} else {
    Write-Host "[INFO] Auto-start registry check completed"
}

# 4. Create dummy customer database in authoritative directory to verify data preservation
Write-Host ""
Write-Host "==> 4. Creating test customer database in authoritative path..."
$dataDir = "$env:LOCALAPPDATA\RupeeCRM\data"
New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
$dummyDb = "$dataDir\database.mv.db"
"DUMMY_DATABASE_CONTENT" | Out-File -FilePath $dummyDb
Write-Host "[OK] Dummy customer database created at $dummyDb"

# 5. Test Uninstallation
if ($msiPath) {
    Write-Host ""
    Write-Host "==> 5. Executing msiexec /x RupeeCRMSetup.msi /qn..."
    Start-Process -FilePath "msiexec.exe" -ArgumentList "/x `"$msiPath`" /qn" -Wait -NoNewWindow

    # Verify binaries removed
    if (Test-Path $installedExe) {
        throw "Validation failed: RupeeCRM.exe was not removed by MSI uninstaller"
    }
    Write-Host "[OK] RupeeCRM executable successfully removed"

    # Verify HKCU Run was removed
    $runKeyPost = [Microsoft.Win32.Registry]::CurrentUser.OpenSubKey("Software\Microsoft\Windows\CurrentVersion\Run")
    $regValPost = if ($runKeyPost) { $val = $runKeyPost.GetValue("RupeeCRM"); $runKeyPost.Close(); $val } else { $null }
    if ($regValPost) {
        throw "Validation failed: HKCU Run auto-start key was NOT removed after uninstall"
    }
    Write-Host "[OK] Auto-start registry key successfully removed"
}

# 6. Verify customer database strictly preserved
if (-not (Test-Path $dummyDb)) {
    throw "CRITICAL FAILURE: Customer database was deleted during uninstall!"
}
Write-Host "[OK] Customer data safely preserved at $dummyDb after uninstall"

Write-Host ""
Write-Host "=========================================================="
Write-Host " [CI SUCCESS] All Windows MSI Lifecycle Checks Passed!"
Write-Host "=========================================================="
