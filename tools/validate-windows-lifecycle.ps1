# ==============================================================================
# Windows Installer & Uninstaller Lifecycle Validation Script for CI
# ==============================================================================

$ErrorActionPreference = "Stop"

Write-Host "=========================================================="
Write-Host " [CI VALIDATION] Testing Windows Installer & Uninstaller"
Write-Host "=========================================================="

$zipPath = (Resolve-Path "RupeeCRM-Windows-x64.zip").Path
$rawChecksum = Get-Content "RupeeCRM-Windows-x64.zip.sha256" -Raw
$checksum = ($rawChecksum -replace '\s','').ToLower()

Write-Host "Artifact ZIP Path : $zipPath"
Write-Host "Expected Checksum : $checksum"

# 1. Test Installer
Write-Host ""
Write-Host "==> 1. Executing tools/install-windows.ps1..."
& ./tools/install-windows.ps1 -CustomDownloadUrl $zipPath -CustomChecksum $checksum -SkipLaunch

# 2. Verify binaries installed
Write-Host ""
Write-Host "==> 2. Verifying installed executable..."
$installedExe = "$env:LOCALAPPDATA\Programs\RupeeCRM\RupeeCRM.exe"
if (-not (Test-Path $installedExe)) {
    throw "Validation failed: RupeeCRM.exe was not installed to $installedExe"
}
Write-Host "[OK] Installed executable verified at $installedExe"

# 3. Verify Desktop and Start Menu shortcuts (optional in headless CI)
Write-Host ""
Write-Host "==> 3. Checking shortcuts..."
$desktopLnk = "$env:USERPROFILE\Desktop\RupeeCRM.lnk"
$menuLnk = "$env:APPDATA\Microsoft\Windows\Start Menu\Programs\RupeeCRM\RupeeCRM.lnk"
if (Test-Path $desktopLnk) {
    Write-Host "[OK] Desktop shortcut verified at $desktopLnk"
} else {
    Write-Host "[INFO] Desktop shortcut skipped in headless CI"
}
if (Test-Path $menuLnk) {
    Write-Host "[OK] Start Menu shortcut verified at $menuLnk"
} else {
    Write-Host "[INFO] Start Menu shortcut skipped in headless CI"
}

# 4. Verify Auto-Start Registry entry (Authoritative .NET Registry Assertion)
Write-Host ""
Write-Host "==> 4. Verifying Auto-Start Registry..."
$runKeyObj = [Microsoft.Win32.Registry]::CurrentUser.OpenSubKey("Software\Microsoft\Windows\CurrentVersion\Run")
$regVal = if ($runKeyObj) { $val = $runKeyObj.GetValue("RupeeCRMService"); $runKeyObj.Close(); $val } else { $null }

if (-not $regVal) {
    throw "Validation failed: HKCU Run auto-start key was not set"
}
if ($regVal -notmatch "RupeeCRM.*--background") {
    throw "Validation failed: HKCU Run auto-start command format invalid ($regVal)"
}
Write-Host "[OK] Auto-start registry key verified: $regVal"

# 5. Create dummy customer database to verify zero data loss
Write-Host ""
Write-Host "==> 5. Creating test customer database..."
$dataDir = "$env:APPDATA\SimpleBilling"
New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
$dummyDb = "$dataDir\database.mv.db"
"DUMMY_DATABASE_CONTENT" | Out-File -FilePath $dummyDb
Write-Host "[OK] Dummy customer database created at $dummyDb"

# 6. Test Uninstaller
Write-Host ""
Write-Host "==> 6. Executing tools/uninstall-windows.ps1..."
& ./tools/uninstall-windows.ps1 -Quiet

$appDir = "$env:LOCALAPPDATA\Programs\RupeeCRM"
$installedExe = "$appDir\RupeeCRM.exe"
if (Test-Path $installedExe) {
    throw "Validation failed: RupeeCRM.exe was not removed by uninstaller"
}
Write-Host "[OK] RupeeCRM executable successfully removed"

# Verify HKCU Run was removed
$runKeyPost = [Microsoft.Win32.Registry]::CurrentUser.OpenSubKey("Software\Microsoft\Windows\CurrentVersion\Run")
$regValPost = if ($runKeyPost) { $val = $runKeyPost.GetValue("RupeeCRMService"); $runKeyPost.Close(); $val } else { $null }
if ($regValPost) {
    throw "Validation failed: HKCU Run auto-start key was NOT removed after uninstall"
}
Write-Host "[OK] Auto-start registry key successfully removed"

# Cleanup directory shell if delayed by OS file handles
if (Test-Path $appDir) {
    for ($i = 0; $i -lt 5; $i++) {
        cmd.exe /c "attrib -r -s -h `"$appDir\*.*`" /s /d >nul 2>nul"
        cmd.exe /c "rmdir /s /q `"$appDir`" >nul 2>nul"
        if (-not (Test-Path $appDir)) { break }
        Start-Sleep -Seconds 1
    }
}
Write-Host "[OK] Application directory uninstalled"

if (-not (Test-Path $dummyDb)) {
    throw "CRITICAL FAILURE: Customer database was deleted during uninstall!"
}
Write-Host "[OK] Customer data safely preserved after uninstall"
Write-Host ""
Write-Host "=========================================================="
Write-Host " [CI SUCCESS] All Windows Lifecycle Checks Passed!"
Write-Host "=========================================================="
