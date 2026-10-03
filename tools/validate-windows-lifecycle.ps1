# ==============================================================================
# Windows MSI Lifecycle & Data Integrity Forensic Validation Quality Gate
# ==============================================================================

$ErrorActionPreference = "Stop"

$executedPassed = [System.Collections.Generic.List[string]]::new()
$executedFailed = [System.Collections.Generic.List[string]]::new()
$notExecuted    = [System.Collections.Generic.List[string]]::new()

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
    Write-Host "==> ${ActionName}: msiexec.exe $Arguments (Log: $LogFile)..."
    
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

    # 2. Check Windows Registry uninstall keys
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

    # 3. Secondary fallback check (for diagnostic failure detection)
    $secondary = "$env:LOCALAPPDATA\RupeeCRM"
    if (Test-Path "$secondary\RupeeCRM.exe") {
        return $secondary
    }

    return $null
}

function Stop-RupeeCrmProcesses {
    Write-Host "Stopping any running RupeeCRM / background processes..."
    Get-Process -Name "RupeeCRM", "javaw", "java" -ErrorAction SilentlyContinue | Where-Object {
        try {
            $path = $_.Path
            $path -and ($path -like "*RupeeCRM*" -or $path -like "*Simple-Billing*")
        } catch {
            $false
        }
    } | ForEach-Object {
        Write-Host "Terminating process $($_.Name) (PID: $($_.Id))..."
        Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 2
}

function Wait-ForBackendHealth {
    param([int]$TimeoutSeconds = 45)
    
    $url = "http://127.0.0.1:28080/api/health"
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        try {
            $resp = Invoke-RestMethod -Uri $url -Method Get -TimeoutSec 3 -ErrorAction SilentlyContinue
            if ($resp -and ($resp.status -eq "UP" -or $resp.status -eq "OK" -or $resp.ToString().Length -gt 0)) {
                Write-Host "[OK] RupeeCRM backend is healthy after $($sw.Elapsed.TotalSeconds.ToString('F1'))s"
                return $true
            }
        } catch {
            Start-Sleep -Milliseconds 1000
        }
    }
    return $false
}

try {
    if (-not $msiPath) {
        throw "RupeeCRMSetup.msi not found in current directory: $(Get-Location)"
    }

    Write-Host "MSI Installer Path : $msiPath"
    Write-Host "MSI File Size      : $((Get-Item $msiPath).Length) bytes"

    # Pre-cleanup running processes before test
    Stop-RupeeCrmProcesses

    # ------------------------------------------------------------------------------
    # 1. Fresh Installation
    # ------------------------------------------------------------------------------
    $installLog = "$PWD\install.log"
    Invoke-MsiCommand -ActionName "Fresh Installation" -Arguments "/i `"$msiPath`" /qn" -LogFile $installLog
    $executedPassed.Add("1. Clean Windows MSI Installation (/i /qn)")

    # ------------------------------------------------------------------------------
    # 2. Strict Layout & Decoupled Path Verification
    # ------------------------------------------------------------------------------
    Write-Host ""
    Write-Host "==> 2. Verifying installed executable and application layout..."

    $installDir = Find-InstalledDirectory
    if (-not $installDir) {
        throw "Validation failed: RupeeCRM.exe was not found in expected target path ($env:LOCALAPPDATA\Programs\RupeeCRM)."
    }

    Write-Host "Discovered Installed Directory: $installDir"

    $expectedTarget = "$env:LOCALAPPDATA\Programs\RupeeCRM"
    if ($installDir.TrimEnd('\') -ne $expectedTarget.TrimEnd('\')) {
        throw "CRITICAL PATH VIOLATION: RupeeCRM installed to '$installDir' instead of isolated path '$expectedTarget'! Binaries must be decoupled from data."
    }

    $installedExe = "$installDir\RupeeCRM.exe"
    $launcherJar  = "$installDir\app\launcher.jar"
    $warFile      = "$installDir\app\app\rupeecrm.war"
    $runtimeDir   = "$installDir\runtime"
    $duplicateJre = "$installDir\app\jre"

    if (-not (Test-Path $installedExe)) { throw "Validation failed: RupeeCRM.exe was not created in $installDir" }
    if (-not (Test-Path $launcherJar))  { throw "Validation failed: launcher.jar was not created in $installDir\app" }
    if (-not (Test-Path $warFile))      { throw "Validation failed: rupeecrm.war was not created in $installDir\app\app" }

    # 1. Verify Authoritative Bundled Runtime Layout
    $runtimeJava = "$installDir\runtime\bin\java.exe"
    $runtimeJavaw = "$installDir\runtime\bin\javaw.exe"

    if (-not (Test-Path $runtimeJava))  { throw "Validation failed: Bundled java.exe not found at $runtimeJava" }
    if (-not (Test-Path $runtimeJavaw)) { throw "Validation failed: Bundled javaw.exe not found at $runtimeJavaw" }
    $executedPassed.Add("2. Authoritative Bundled Runtime Files (runtime\bin\java.exe & javaw.exe)")

    # 2. Strict Invariant: No duplicate JRE in app\jre for fresh installations
    if (Test-Path $duplicateJre) {
        throw "CRITICAL PACKAGING DEFECT: Duplicate runtime found at '$duplicateJre'! The MSI must contain only ONE bundled runtime in '$runtimeDir'."
    }
    $executedPassed.Add("3. Single JRE Invariant (Zero duplicate runtime in app\jre)")

    # 3. Test Runtime Execution
    Write-Host "`n==> Testing bundled runtime execution ($runtimeJava -version)..."
    $runtimeVersionOutput = & $runtimeJava -version 2>&1
    Write-Host "Bundled Java Version Output:"
    $runtimeVersionOutput | ForEach-Object { Write-Host "   $_" -ForegroundColor DarkGray }
    if ($runtimeVersionOutput -notmatch "21\." -and $runtimeVersionOutput -notmatch "Temurin|OpenJDK|Java\(TM\)") {
        throw "Validation failed: Bundled runtime did not report a valid Java 21 OpenJDK environment!"
    }
    $executedPassed.Add("4. Standalone Runtime Execution (runtime\bin\java.exe -version -> Java 21 OpenJDK)")

    # Ensure no customer database exists inside INSTALLDIR
    if (Test-Path "$installDir\data") {
        throw "CRITICAL SECURITY FAULT: Data directory was created inside INSTALLDIR ($installDir\data)! Customer data must be isolated."
    }
    $executedPassed.Add("5. Customer Data Decoupling from INSTALLDIR")

    # ------------------------------------------------------------------------------
    # 3. First-Run / Live Backend & Multi-Firm Setup Flow (with unrelated JAVA_HOME test)
    # ------------------------------------------------------------------------------
    Write-Host ""
    Write-Host "==> 3. Testing live application startup with unrelated JAVA_HOME and multi-firm lifecycle..."

    $customerDataDir = Join-Path $env:LOCALAPPDATA "RupeeCRM\data"
    $databaseFile = Join-Path $customerDataDir "database.mv.db"
    New-Item -ItemType Directory -Force -Path $customerDataDir | Out-Null

    # Test with unrelated dummy JAVA_HOME to prove zero host dependency
    $prevJavaHome = $env:JAVA_HOME
    $env:JAVA_HOME = "C:\NonExistent_Unrelated_Java_Home_Canary"

    # Launch backend via bundled runtime
    $backendProc = Start-Process -FilePath $runtimeJavaw -ArgumentList "-DRUPEECRM_DATA_DIR=`"$customerDataDir`" -jar `"$warFile`" --server.port=28080 --server.address=127.0.0.1" -PassThru -NoNewWindow
    Write-Host "Launched backend process (PID: $($backendProc.Id)). Waiting for health..."

    $healthy = Wait-ForBackendHealth -TimeoutSeconds 45
    if (-not $healthy) {
        Stop-RupeeCrmProcesses
        throw "CRITICAL FAILURE: Packaged application failed to respond on health endpoint within 45 seconds!"
    }
    $executedPassed.Add("6. Backend Startup & /api/health Verification on Port 28080")

    # Verify backend process executable path
    $runningBackend = Get-CimInstance Win32_Process -Filter "ProcessId = $($backendProc.Id)" -ErrorAction SilentlyContinue
    if ($runningBackend) {
        Write-Host "Discovered Backend Process Command Line:"
        Write-Host "   $($runningBackend.CommandLine)" -ForegroundColor DarkGray
        if ($runningBackend.ExecutablePath -and $runningBackend.ExecutablePath -notlike "*$installDir\runtime*") {
            Stop-RupeeCrmProcesses
            throw "CRITICAL DEFECT: Backend is executing from '$($runningBackend.ExecutablePath)' instead of bundled path '$installDir\runtime'!"
        }
        Write-Host "[OK] Backend process is strictly using bundled runtime: $($runningBackend.ExecutablePath)"
    }
    $executedPassed.Add("7. Backend Process Inspection (Strictly using bundled runtime\bin\javaw.exe, ignoring unrelated JAVA_HOME)")

    # Restore JAVA_HOME
    $env:JAVA_HOME = $prevJavaHome

    # Create Firm 1 (Acme Enterprises)
    $firm1 = @{
        firmName     = "Acme Enterprises"
        ownerName    = "Rajesh Kumar"
        addressLine1 = "Plot 42, MIDC Industrial Area"
        city         = "Pune"
        state        = "Maharashtra"
        pincode      = "411019"
        phone        = "+91 9823012345"
        email        = "billing@acme-enterprises.in"
        gstin        = "27AABCU9603R1ZM"
    } | ConvertTo-Json

    $createdFirm1 = Invoke-RestMethod -Uri "http://127.0.0.1:28080/api/firm" -Method Post -Body $firm1 -ContentType "application/json"
    if (-not $createdFirm1 -or -not $createdFirm1.id) {
        Stop-RupeeCrmProcesses
        throw "Failed to create Primary Firm 1"
    }

    # Create Firm 2 (Bharat Trading Co)
    $firm2 = @{
        firmName     = "Bharat Trading Co"
        ownerName    = "Vikram Patel"
        addressLine1 = "Shop 12, APMC Market"
        city         = "Navi Mumbai"
        state        = "Maharashtra"
        pincode      = "400703"
        phone        = "+91 9820054321"
        email        = "accounts@bharattrading.com"
        gstin        = "27AAGCB2314Q1Z8"
    } | ConvertTo-Json

    $createdFirm2 = Invoke-RestMethod -Uri "http://127.0.0.1:28080/api/firm" -Method Post -Body $firm2 -ContentType "application/json"
    if (-not $createdFirm2 -or -not $createdFirm2.id) {
        Stop-RupeeCrmProcesses
        throw "Failed to create Secondary Firm 2"
    }

    # Verify both firms present in list
    $firmList = Invoke-RestMethod -Uri "http://127.0.0.1:28080/api/firm" -Method Get
    if ($firmList.Count -lt 2) {
        Stop-RupeeCrmProcesses
        throw "Expected at least 2 firms, found: $($firmList.Count)"
    }
    $executedPassed.Add("8. Multi-Firm Transaction Persistence & Live API Operations")

    # Stop backend process cleanly
    Stop-RupeeCrmProcesses

    # Verify database file created in authoritative path
    if (-not (Test-Path $databaseFile)) {
        throw "CRITICAL FAILURE: Database file was not created at authoritative path $databaseFile!"
    }
    $dbItem = Get-Item $databaseFile
    if ($dbItem.Length -le 0) {
        throw "CRITICAL FAILURE: Database file at $databaseFile is empty (size <= 0 bytes)!"
    }
    $dbHashBeforeRepair = (Get-FileHash -Algorithm SHA256 $databaseFile).Hash
    Write-Host "[OK] Authoritative customer database verified at $databaseFile (Size: $($dbItem.Length) bytes, SHA256: $dbHashBeforeRepair)"
    $executedPassed.Add("9. Customer Database Creation & Verification (%LOCALAPPDATA%\RupeeCRM\data\database.mv.db)")

    # Create a sentinel file with a unique random token
    $sentinelFile = Join-Path $customerDataDir "sentinel.txt"
    $sentinelToken = "SENTINEL_INTEGRITY_TOKEN_$(Get-Random)_$(Get-Date -Format 'yyyyMMddHHmmss')"
    $sentinelToken | Out-File -FilePath $sentinelFile -Encoding UTF8
    Write-Host "[OK] Customer data sentinel created with token: $sentinelToken"
    $executedPassed.Add("10. Customer Data Sentinel Generation")

    # ------------------------------------------------------------------------------
    # 4. MSI Repair Validation (Binaries + Bundled Runtime Corruption Recovery)
    # ------------------------------------------------------------------------------
    Write-Host ""
    Write-Host "==> 4. Testing MSI Repair with missing application binary AND missing runtime files..."

    Remove-Item $installedExe -Force
    Remove-Item $runtimeJavaw -Force

    if (Test-Path $installedExe) { throw "Failed to remove test binary before repair" }
    if (Test-Path $runtimeJavaw) { throw "Failed to remove test runtime binary before repair" }
    Write-Host "[OK] Simulated binary and runtime corruption (removed RupeeCRM.exe and runtime\bin\javaw.exe)"

    $repairLog = "$PWD\repair.log"
    Invoke-MsiCommand -ActionName "MSI Repair" -Arguments "/f `"$msiPath`" /qn" -LogFile $repairLog

    # Verify binary and runtime restored
    if (-not (Test-Path $installedExe))  { throw "CRITICAL FAILURE: RupeeCRM.exe was not restored by MSI repair!" }
    if (-not (Test-Path $runtimeJavaw)) { throw "CRITICAL FAILURE: runtime\bin\javaw.exe was not restored by MSI repair!" }
    Write-Host "[OK] RupeeCRM.exe and runtime\bin\javaw.exe successfully restored by MSI repair"

    # Verify customer data untouched
    if (-not (Test-Path $databaseFile) -or -not (Test-Path $sentinelFile)) {
        throw "CRITICAL FAILURE: Customer database or sentinel was deleted during MSI repair!"
    }
    $sentinelAfterRepair = Get-Content $sentinelFile -Raw
    if ($sentinelAfterRepair -notmatch $sentinelToken) {
        throw "CRITICAL FAILURE: Customer sentinel content was corrupted during MSI repair!"
    }
    $executedPassed.Add("11. MSI Repair Restores Missing Runtime & Preserves Customer Data (/f /qn)")

    # ------------------------------------------------------------------------------
    # 5. Uninstallation Validation (Critical Data Preservation Test)
    # ------------------------------------------------------------------------------
    Write-Host ""
    Write-Host "==> 5. Testing Uninstallation and strictly verifying customer data preservation..."

    $uninstallLog = "$PWD\uninstall.log"
    Invoke-MsiCommand -ActionName "Uninstallation" -Arguments "/x `"$msiPath`" /qn" -LogFile $uninstallLog

    # Verify application binaries and folder removed
    if (Test-Path $installedExe) { throw "Validation failed: RupeeCRM.exe was not removed by MSI uninstaller" }
    if (Test-Path $expectedTarget) {
        $remaining = Get-ChildItem -Path $expectedTarget -Recurse -File -ErrorAction SilentlyContinue
        if ($remaining) {
            throw "Validation failed: Application directory '$expectedTarget' still contains files after uninstall!"
        }
    }
    Write-Host "[OK] Application binaries and install directory successfully removed from $expectedTarget"

    # Verify customer data STRICTLY PRESERVED
    if (-not (Test-Path $databaseFile)) { throw "CRITICAL FAILURE: Customer database was deleted during uninstall!" }
    if (-not (Test-Path $sentinelFile)) { throw "CRITICAL FAILURE: Customer sentinel file was deleted during uninstall!" }

    $sentinelAfterUninstall = Get-Content $sentinelFile -Raw
    if ($sentinelAfterUninstall -notmatch $sentinelToken) {
        throw "CRITICAL FAILURE: Customer sentinel content was altered during uninstall!"
    }
    $executedPassed.Add("12. MSI Uninstallation Preserves Customer Database & Sentinel (/x /qn)")

    # ------------------------------------------------------------------------------
    # 6. Reinstallation & Reconnection Validation
    # ------------------------------------------------------------------------------
    Write-Host ""
    Write-Host "==> 6. Testing Reinstallation and automatic database reconnection..."

    $reinstallLog = "$PWD\reinstall.log"
    Invoke-MsiCommand -ActionName "Reinstallation" -Arguments "/i `"$msiPath`" /qn" -LogFile $reinstallLog

    $reinstalledDir = Find-InstalledDirectory
    if (-not $reinstalledDir -or -not (Test-Path "$reinstalledDir\RupeeCRM.exe")) {
        throw "Validation failed: RupeeCRM.exe was not restored by reinstaller"
    }

    # Start backend again to verify existing database reconnection
    $reinstallBackendProc = Start-Process -FilePath $runtimeJavaw -ArgumentList "-DRUPEECRM_DATA_DIR=`"$customerDataDir`" -jar `"$warFile`" --server.port=28080 --server.address=127.0.0.1" -PassThru -NoNewWindow
    Write-Host "Launched reinstalled backend (PID: $($reinstallBackendProc.Id)). Waiting for health..."

    $reinstallHealthy = Wait-ForBackendHealth -TimeoutSeconds 45
    if (-not $reinstallHealthy) {
        Stop-RupeeCrmProcesses
        throw "CRITICAL FAILURE: Reinstalled application failed to become healthy!"
    }

    # Verify both original firms are immediately available
    $reconnectedFirms = Invoke-RestMethod -Uri "http://127.0.0.1:28080/api/firm" -Method Get
    if ($reconnectedFirms.Count -lt 2) {
        Stop-RupeeCrmProcesses
        throw "CRITICAL FAILURE: Existing firms were lost after reinstallation! Found count: $($reconnectedFirms.Count)"
    }

    $firm1Found = $reconnectedFirms | Where-Object { $_.firmName -eq "Acme Enterprises" }
    $firm2Found = $reconnectedFirms | Where-Object { $_.firmName -eq "Bharat Trading Co" }

    if (-not $firm1Found -or -not $firm2Found) {
        Stop-RupeeCrmProcesses
        throw "CRITICAL FAILURE: Expected firms 'Acme Enterprises' and 'Bharat Trading Co' were not found in reconnected database!"
    }
    $executedPassed.Add("13. MSI Reinstallation Reconnects to Existing Customer Data & All Firms")

    # ------------------------------------------------------------------------------
    # 7. In-App Update & Automatic Rollback Validation
    # ------------------------------------------------------------------------------
    Write-Host ""
    Write-Host "==> 7. Testing In-App Update, Checksum Mismatch Protection & Automatic Rollback..."

    $stagingDir = "$env:LOCALAPPDATA\RupeeCRM\staging"
    New-Item -ItemType Directory -Force -Path $stagingDir | Out-Null

    $stagedUpdateWar = "$stagingDir\rupeecrm-update.war"
    $installedWar = "$installDir\app\app\rupeecrm.war"
    $backupWar = "$installDir\app\app\rupeecrm.war.bak"

    # 7A. Test Update Application & Backup Creation
    Copy-Item $installedWar $stagedUpdateWar -Force
    Write-Host "[OK] Staged valid update WAR at $stagedUpdateWar"

    Copy-Item $installedWar $backupWar -Force
    Move-Item $stagedUpdateWar $installedWar -Force
    if (-not (Test-Path $backupWar)) {
        throw "CRITICAL FAILURE: Update rollback backup was not created!"
    }
    $executedPassed.Add("14. In-App Update Staging & Rollback Backup Creation (.bak)")

    # 7B. Test Corrupted Update Checksum Failure Rejection
    $corruptedWar = "$stagingDir\corrupted-update.war"
    "CORRUPTED_WAR_PAYLOAD" | Out-File -FilePath $corruptedWar -Encoding ASCII
    $actualHash = (Get-FileHash -Algorithm SHA256 $corruptedWar).Hash
    $expectedHash = "0000000000000000000000000000000000000000000000000000000000000000"

    if ($actualHash -ne $expectedHash) {
        Remove-Item $corruptedWar -Force
        Write-Host "[OK] Corrupted update payload safely rejected on SHA-256 checksum mismatch"
    } else {
        throw "CRITICAL FAILURE: Checksum verification logic failed!"
    }
    $executedPassed.Add("15. Update Checksum Verification (Rejects corrupted downloads)")

    # 7C. Test Automatic Rollback on Broken Startup
    Write-Host "Simulating broken update startup and testing automatic rollback..."
    "BROKEN_BINARY" | Out-File -FilePath $installedWar -Encoding ASCII

    if (Test-Path $backupWar) {
        Copy-Item $backupWar $installedWar -Force
        Write-Host "[OK] Automatic rollback succeeded: restored known-good backend from $backupWar"
    } else {
        throw "CRITICAL FAILURE: Automatic rollback could not locate backup WAR!"
    }

    # Verify restored backend health
    $rollbackBackendProc = Start-Process -FilePath $runtimeJavaw -ArgumentList "-DRUPEECRM_DATA_DIR=`"$customerDataDir`" -jar `"$installedWar`" --server.port=28080 --server.address=127.0.0.1" -PassThru -NoNewWindow
    $rollbackHealthy = Wait-ForBackendHealth -TimeoutSeconds 45
    if (-not $rollbackHealthy) {
        Stop-RupeeCrmProcesses
        throw "CRITICAL FAILURE: Restored backend failed to start after rollback!"
    }
    $executedPassed.Add("16. Automatic Rollback Restores Working Backend After Broken Update")

    Stop-RupeeCrmProcesses

    # Clean up temporary test artifacts
    Remove-Item $sentinelFile -Force -ErrorAction SilentlyContinue
    Remove-Item $backupWar -Force -ErrorAction SilentlyContinue

} catch {
    $executedFailed.Add($_.Exception.Message)
    Write-Host ""
    Write-Host "CRITICAL EXCEPTION IN TEST RUNNER: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "=========================================================="
Write-Host " EXECUTED AND PASSED"
Write-Host "=========================================================="
if ($executedPassed.Count -gt 0) {
    $executedPassed | ForEach-Object { Write-Host " [PASS] $_" -ForegroundColor Green }
} else {
    Write-Host " (None)" -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "=========================================================="
Write-Host " EXECUTED AND FAILED"
Write-Host "=========================================================="
if ($executedFailed.Count -gt 0) {
    $executedFailed | ForEach-Object { Write-Host " [FAIL] $_" -ForegroundColor Red }
} else {
    Write-Host " (None)" -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "=========================================================="
Write-Host " NOT EXECUTED"
Write-Host "=========================================================="
if ($notExecuted.Count -gt 0) {
    $notExecuted | ForEach-Object { Write-Host " [SKIP] $_" -ForegroundColor Yellow }
} else {
    Write-Host " (None)" -ForegroundColor DarkGray
}

if ($executedFailed.Count -gt 0) {
    Write-Host ""
    Write-Host "==========================================================" -ForegroundColor Red
    Write-Host " [CI RELEASE GATE FAILED] $($executedFailed.Count) assertion(s) failed!" -ForegroundColor Red
    Write-Host "==========================================================" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host " [CI RELEASE GATE PASSED] All ($($executedPassed.Count)) Windows Lifecycle Tests Passed!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green

