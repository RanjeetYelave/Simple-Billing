# ==============================================================================
# Windows MSI Lifecycle Validation Script for CI & Local Release Testing
# ==============================================================================

$ErrorActionPreference = "Stop"

Write-Host "=========================================================="
Write-Host " [CI VALIDATION] Testing Windows Unified MSI Lifecycle"
Write-Host "=========================================================="

$msiPath = if (Test-Path "RupeeCRMSetup.msi") { (Resolve-Path "RupeeCRMSetup.msi").Path } else { $null }

function Invoke-MsiCommand {
    param(
        [Parameter(Mandatory=$true)][string]$ActionName,
        [Parameter(Mandatory=$true)][string]$Arguments,
        [Parameter(Mandatory=$true)][string]$LogFile
    )

    Write-Host ""
    Write-Host "==> $ActionName: msiexec.exe $Arguments (Log: $LogFile)..."
    
    if (Test-Path $LogFile) { Remove-Item $LogFile -Force }

    $fullArgs = "$Arguments /L*v `"$LogFile`""
    $process = Start-Process -FilePath "msiexec.exe" -ArgumentList $fullArgs -Wait -PassThru -NoNewWindow
    
    Write-Host "MSI process exit code: $($process.ExitCode)"

    # Windows Installer success codes: 0 = Success, 3010 = Success (Reboot required)
    if ($process.ExitCode -ne 0 -and $process.ExitCode -ne 3010) {
        Write-Host "----------------- MSI LOG FAILURE DIAGNOSTICS (Last 100 lines) -----------------"
        if (Test-Path $LogFile) {
            Get-Content $LogFile -Tail 100 | ForEach-Object { Write-Host $_ }
        } else {
            Write-Host "Log file '$LogFile' was not created."
        }
        Write-Host "--------------------------------------------------------------------------------"
        throw "MSI execution failed during '$ActionName' with exit code: $($process.ExitCode)"
    }
    Write-Host "✓ $ActionName executed successfully with exit code $($process.ExitCode)"
}

function Find-InstalledDirectory {
    # 1. Primary intended target: %LOCALAPPDATA%\Programs\RupeeCRM
    $primary = "$env:LOCALAPPDATA\Programs\RupeeCRM"
    if (Test-Path "$primary\RupeeCRM.exe") {
        return $primary
    }

    # 2. Secondary fallback: %LOCALAPPDATA%\RupeeCRM
    $secondary = "$env:LOCALAPPDATA\RupeeCRM"
    if (Test-Path "$secondary\RupeeCRM.exe") {
        return $secondary
    }

    # 3. Check Windows Registry uninstall keys
    $regPaths = @(
        "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall",
        "HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall"
    )
    foreach ($rp in $regPaths) {
        if (Test-Path $rp) {
            $keys = Get-ChildItem -Path $rp -ErrorAction SilentlyContinue
            foreach ($k in $keys) {
                $disp = $k.GetValue("DisplayName")
                if ($disp -and $disp -like "*RupeeCRM*") {
                    $loc = $k.GetValue("InstallLocation")
                    if ($loc -and (Test-Path "$loc\RupeeCRM.exe")) {
                        return $loc.TrimEnd('\')
                    }
                }
            }
        }
    }

    # 4. Search candidate roots for RupeeCRM.exe
    $searchRoots = @("$env:LOCALAPPDATA", "$env:ProgramFiles", "${env:ProgramFiles(x86)}")
    foreach ($sr in $searchRoots) {
        if ($sr -and (Test-Path $sr)) {
            $found = Get-ChildItem -Path $sr -Filter "RupeeCRM.exe" -Recurse -File -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($found) {
                return $found.DirectoryName
            }
        }
    }

    return $null
}

if (-not $msiPath) {
    throw "RupeeCRMSetup.msi not found in current directory: $(Get-Location)"
}

Write-Host "MSI Installer Path : $msiPath"
Write-Host "MSI File Size      : $((Get-Item $msiPath).Length) bytes"

# ------------------------------------------------------------------------------
# 1. Fresh Installation
# ------------------------------------------------------------------------------
$installLog = "$PWD\install.log"
Invoke-MsiCommand -ActionName "Fresh Installation" -Arguments "/i `"$msiPath`" /qn" -LogFile $installLog

# ------------------------------------------------------------------------------
# 2. Verify Installed Directory & Binaries
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "==> 2. Verifying installed executable and application layout..."

$installDir = Find-InstalledDirectory
if (-not $installDir) {
    Write-Host "----------------- MSI LOG (Full Target Analysis) -----------------"
    if (Test-Path $installLog) {
        Select-String -Path $installLog -Pattern "INSTALLDIR|TARGETDIR|APPLICATIONFOLDER|ProductCode|UpgradeCode|Exit code" | ForEach-Object { Write-Host $_.Line }
    }
    Write-Host "------------------------------------------------------------------"
    throw "Validation failed: RupeeCRM.exe was not found in any expected target path ($env:LOCALAPPDATA\Programs\RupeeCRM, $env:LOCALAPPDATA\RupeeCRM, or Registry)."
}

Write-Host "Discovered Installed Directory: $installDir"

$installedExe = "$installDir\RupeeCRM.exe"
$launcherJar  = "$installDir\app\launcher.jar"
$warFile      = "$installDir\app\app\rupeecrm.war"
$runtimeDir   = "$installDir\runtime"
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

# ------------------------------------------------------------------------------
# 3. Create Test Customer Database in Authoritative Path
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "==> 3. Creating test customer database in authoritative path..."
$dataDir = "$env:LOCALAPPDATA\RupeeCRM\data"
New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
$dummyDb = "$dataDir\billsoft_database.mv.db"
$dbCheckToken = "CUSTOMER_DATABASE_DATA_INTEGRITY_CHECK_TOKEN_$(Get-Random)"
$dbCheckToken | Out-File -FilePath $dummyDb -Encoding UTF8
Write-Host "[OK] Test customer database created at $dummyDb (Token: $dbCheckToken)"

# ------------------------------------------------------------------------------
# 4. Test MSI Repair
# ------------------------------------------------------------------------------
$repairLog = "$PWD\repair.log"
Invoke-MsiCommand -ActionName "MSI Repair" -Arguments "/f `"$msiPath`" /qn" -LogFile $repairLog

if (-not (Test-Path $installedExe)) {
    throw "Validation failed: RupeeCRM.exe missing after repair!"
}
if (-not (Test-Path $dummyDb)) {
    throw "CRITICAL FAILURE: Customer database was deleted during repair!"
}
$repairDbContent = Get-Content $dummyDb -Raw
if ($repairDbContent -notmatch $dbCheckToken) {
    throw "CRITICAL FAILURE: Customer database content was corrupted during repair!"
}
Write-Host "[OK] MSI repair successful and customer data safely preserved"

# ------------------------------------------------------------------------------
# 5. Test Uninstallation
# ------------------------------------------------------------------------------
$uninstallLog = "$PWD\uninstall.log"
Invoke-MsiCommand -ActionName "Uninstallation" -Arguments "/x `"$msiPath`" /qn" -LogFile $uninstallLog

# Verify binaries removed
if (Test-Path $installedExe) {
    throw "Validation failed: RupeeCRM.exe was not removed by MSI uninstaller"
}
Write-Host "[OK] RupeeCRM executable successfully removed from $installDir"

# Verify customer database strictly preserved
if (-not (Test-Path $dummyDb)) {
    throw "CRITICAL FAILURE: Customer database was deleted during uninstall!"
}
$uninstallDbContent = Get-Content $dummyDb -Raw
if ($uninstallDbContent -notmatch $dbCheckToken) {
    throw "CRITICAL FAILURE: Customer database content was corrupted during uninstall!"
}
Write-Host "[OK] Customer database strictly preserved at $dummyDb after uninstall"

# ------------------------------------------------------------------------------
# 6. Test Reinstallation after Uninstall
# ------------------------------------------------------------------------------
$reinstallLog = "$PWD\reinstall.log"
Invoke-MsiCommand -ActionName "Reinstallation" -Arguments "/i `"$msiPath`" /qn" -LogFile $reinstallLog

$reinstalledDir = Find-InstalledDirectory
if (-not $reinstalledDir -or -not (Test-Path "$reinstalledDir\RupeeCRM.exe")) {
    throw "Validation failed: RupeeCRM.exe was not restored by reinstaller"
}
if (-not (Test-Path $dummyDb)) {
    throw "CRITICAL FAILURE: Existing customer database was wiped on reinstall!"
}
$reinstallDbContent = Get-Content $dummyDb -Raw
if ($reinstallDbContent -notmatch $dbCheckToken) {
    throw "CRITICAL FAILURE: Existing customer database content was corrupted on reinstall!"
}
Write-Host "[OK] Reinstallation completed and successfully reconnected to existing database"

Write-Host ""
Write-Host "=========================================================="
Write-Host " [CI SUCCESS] All Windows MSI Lifecycle Checks Passed!"
Write-Host "=========================================================="
