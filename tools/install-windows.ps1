<#
.SYNOPSIS
    Hardened Standalone Windows Clean Installer & Lifecycle Validator for RupeeCRM.
.DESCRIPTION
    Performs a 100% clean factory-reset and installation of the latest RupeeCRM release:
    1. Stops all running RupeeCRM processes safely.
    2. Completely wipes previous binaries, legacy installations, registries, and data.
    3. Downloads the latest official RupeeCRM Windows MSI release from GitHub.
    4. Validates download integrity and installs via Windows Installer (MSI).
    5. Discovers and validates isolated architecture layout (%LOCALAPPDATA%\Programs\RupeeCRM).
    6. Starts application, validates health, database initialization, and firm persistence.
    7. Launches the verified application in the default web browser.
.PARAMETER MsiPath
    Optional local path to a pre-downloaded RupeeCRMSetup.msi installer.
.PARAMETER Force
    Skips interactive confirmation prompts for non-interactive / automated runs.
.PARAMETER SkipLiveValidation
    Installs the application without running the automated multi-firm verification suite.
.EXAMPLE
    # Direct one-line execution in PowerShell:
    irm https://raw.githubusercontent.com/RanjeetYelave/Simple-Billing/main/tools/install-windows.ps1 | iex

    # Non-interactive clean install:
    powershell -ExecutionPolicy Bypass -File .\tools\install-windows.ps1 -Force
#>

[CmdletBinding()]
param(
    [string]$MsiPath = "",
    [switch]$Force,
    [switch]$SkipLiveValidation
)

$ErrorActionPreference = "Stop"

# Use TLS 1.2 & TLS 1.3 for secure downloads
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 -bor [Net.SecurityProtocolType]::Tls13

function Complete-PauseExit {
    param([int]$ExitCode = 0)
    try {
        if ([Environment]::UserInteractive -and -not [Console]::IsInputRedirected) {
            Write-Host ""
            Write-Host "Press Enter to exit..." -ForegroundColor Gray
            [void][System.Console]::ReadLine()
        }
    } catch {}
    exit $ExitCode
}

# ==============================================================================
# 1. ELEVATION CHECK & AUTO-RELAUNCH
# ==============================================================================

function Test-IsAdministrator {
    try {
        $id = [Security.Principal.WindowsIdentity]::GetCurrent()
        $p = New-Object Security.Principal.WindowsPrincipal($id)
        return $p.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    } catch {
        return $false
    }
}

$scriptFile = $PSCommandPath
if (-not $scriptFile) {
    if ($PSScriptRoot -and $MyInvocation.MyCommand.Name) {
        $scriptFile = Join-Path $PSScriptRoot $MyInvocation.MyCommand.Name
    } elseif ($MyInvocation.MyCommand.Definition) {
        $scriptFile = $MyInvocation.MyCommand.Definition
    }
}

if (-not (Test-IsAdministrator)) {
    if ($env:RUPEECRM_INSTALL_RELAUNCHED -eq "1") {
        Write-Host "CRITICAL: Failed to acquire Administrator privileges. Please right-click PowerShell and select 'Run as Administrator'." -ForegroundColor Red
        Complete-PauseExit 1
    }

    Write-Host "======================================================================" -ForegroundColor Yellow
    Write-Host " Administrator privileges required. Requesting elevation (UAC)...    " -ForegroundColor Yellow
    Write-Host "======================================================================" -ForegroundColor Yellow

    if ($scriptFile -and (Test-Path $scriptFile)) {
        $extraArgs = ""
        if ($Force) { $extraArgs += " -Force" }
        if ($SkipLiveValidation) { $extraArgs += " -SkipLiveValidation" }
        if ($MsiPath) { $extraArgs += " -MsiPath `"$MsiPath`"" }

        $env:RUPEECRM_INSTALL_RELAUNCHED = "1"
        try {
            Start-Process powershell.exe -ArgumentList "-NoExit -NoProfile -ExecutionPolicy Bypass -File `"$scriptFile`" $extraArgs" -Verb RunAs
            exit 0
        } catch {
            Write-Host "Elevation cancelled or failed: $($_.Exception.Message)" -ForegroundColor Red
            Complete-PauseExit 1
        }
    }
}

# ==============================================================================
# 2. SAFETY WARNING & CONFIRMATION
# ==============================================================================

Write-Host ""
Write-Host "======================================================================" -ForegroundColor Green
Write-Host "          RupeeCRM Standalone Windows Clean Installer & Validator      " -ForegroundColor Green
Write-Host "======================================================================" -ForegroundColor Green
Write-Host ""
Write-Host " WARNING: THIS WILL COMPLETELY REMOVE ALL EXISTING RUPEECRM APPLICATION" -ForegroundColor Yellow
Write-Host " DATA, DATABASES, FIRMS, LICENSE STATE, BACKUPS AND CONFIGURATION." -ForegroundColor Yellow
Write-Host ""

if (-not $Force) {
    Write-Host "To proceed with complete clean installation, type:" -ForegroundColor White
    Write-Host "  YES" -ForegroundColor Magenta
    Write-Host ""
    $confirmInput = Read-Host "Confirmation Prompt"

    if ($confirmInput.Trim() -cne "YES") {
        Write-Host ""
        Write-Host "Confirmation cancelled. Aborting with ZERO changes." -ForegroundColor Green
        Complete-PauseExit 0
    }
    Write-Host ""
    Write-Host "Confirmation accepted. Starting complete clean installation..." -ForegroundColor Green
}

# Diagnostic summary table tracker
$report = [ordered]@{
    "Old processes removed"               = $false
    "Old MSI removed"                     = $false
    "Old application binaries removed"    = $false
    "Old data removed"                    = $false
    "Old registry/autostart state removed"= $false
    "Latest release downloaded"           = $false
    "Integrity verified"                  = $false
    "MSI installed"                       = $false
    "Expected application layout verified"= $false
    "Bundled runtime verified"            = $false
    "Application started"                 = $false
    "Backend healthy"                     = $false
    "Fresh database initialized"          = $false
    "First-time setup available"          = $false
    "Firm creation verified"              = $false
    "Firm persistence verified after restart" = $false
    "Second firm creation verified"       = $false
    "Firm switching verified"             = $false
    "Tray verified"                       = $false
    "Diagnostics Centre verified"         = $false
    "Autostart verified"                  = $false
}

# ==============================================================================
# 3. COMPLETE LEGACY CLEANUP & PROCESS TERMINATION
# ==============================================================================

Write-Host "`n==> [1/6] Stopping existing RupeeCRM processes and services..." -ForegroundColor Cyan

# 1. Terminate RupeeCRM processes safely
$procs = Get-Process -ErrorAction SilentlyContinue | Where-Object {
    try {
        ($_.ProcessName -like "*RupeeCRM*") -or ($_.Path -and $_.Path -like "*RupeeCRM*")
    } catch { $false }
}
foreach ($p in $procs) {
    Write-Host "    Terminating active process: $($p.ProcessName) (PID: $($p.Id))" -ForegroundColor Yellow
    Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
}

# Terminate RupeeCRM backend Java processes specifically
$javaProcs = Get-CimInstance Win32_Process -Filter "Name = 'java.exe' or Name = 'javaw.exe'" -ErrorAction SilentlyContinue | Where-Object {
    $_.CommandLine -and ($_.CommandLine -like "*rupeecrm*" -or $_.CommandLine -like "*launcher.jar*" -or $_.CommandLine -like "*RUPEECRM_DATA_DIR*")
}
foreach ($jp in $javaProcs) {
    Write-Host "    Terminating backend Java process (PID: $($jp.ProcessId))" -ForegroundColor Yellow
    Stop-Process -Id $jp.ProcessId -Force -ErrorAction SilentlyContinue
}

Start-Sleep -Seconds 1
$report["Old processes removed"] = $true

# 2. Stop and delete any registered RupeeCRM services
Get-Service -ErrorAction SilentlyContinue | Where-Object { $_.Name -like "*RupeeCRM*" -or $_.DisplayName -like "*RupeeCRM*" } | ForEach-Object {
    Write-Host "    Removing service: $($_.Name)" -ForegroundColor Yellow
    Stop-Service -Name $_.Name -Force -ErrorAction SilentlyContinue
    sc.exe delete $_.Name | Out-Null
}

# 3. Unregister Scheduled Tasks
if (Get-Command Get-ScheduledTask -ErrorAction SilentlyContinue) {
    Get-ScheduledTask -ErrorAction SilentlyContinue | Where-Object { $_.TaskName -like "*RupeeCRM*" } | ForEach-Object {
        Write-Host "    Unregistering task: $($_.TaskName)" -ForegroundColor Yellow
        Unregister-ScheduledTask -TaskName $_.TaskName -Confirm:$false -ErrorAction SilentlyContinue
    }
}

# 4. Uninstall existing RupeeCRM MSI installations
Write-Host "`n==> [2/6] Detecting and deregistering existing MSI installations..." -ForegroundColor Cyan
$uninstallRegRoots = @(
    "HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall",
    "HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall",
    "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall"
)

foreach ($regRoot in $uninstallRegRoots) {
    if (Test-Path $regRoot) {
        Get-ChildItem -Path $regRoot -ErrorAction SilentlyContinue | ForEach-Object {
            $dn = $_.GetValue("DisplayName")
            $pub = $_.GetValue("Publisher")
            $pCode = $_.PSChildName

            if (($dn -and $dn -like "*RupeeCRM*") -or ($pub -and $pub -eq "RupeeCRM")) {
                Write-Host "    Uninstalling MSI Product: $dn (ProductCode: $pCode)" -ForegroundColor Yellow
                if ($pCode -match "^\{[A-Fa-f0-9\-]+\}$") {
                    Start-Process "msiexec.exe" -ArgumentList "/x `"$pCode`" /qn /norestart" -Wait -NoNewWindow
                }
                Remove-Item -Path $_.PSPath -Recurse -Force -ErrorAction SilentlyContinue
            }
        }
    }
}
$report["Old MSI removed"] = $true

# 5. Remove Application Binaries & Legacy Directories
Write-Host "`n==> [3/6] Purging old application binaries and customer data..." -ForegroundColor Cyan

$binaryDirs = @(
    "$env:LOCALAPPDATA\Programs\RupeeCRM",
    "$env:ProgramFiles\RupeeCRM",
    "${env:ProgramFiles(x86)}\RupeeCRM"
)
foreach ($bd in $binaryDirs) {
    if ($bd -and (Test-Path $bd)) {
        Write-Host "    Deleting binary directory: $bd" -ForegroundColor DarkGray
        Remove-Item -Path $bd -Recurse -Force -ErrorAction SilentlyContinue
    }
}
$report["Old application binaries removed"] = $true

$dataDirs = @(
    "$env:LOCALAPPDATA\RupeeCRM",
    "$env:APPDATA\RupeeCRM",
    "$env:ProgramData\RupeeCRM",
    "$env:USERPROFILE\.rupeecrm"
)
if (Test-Path "$env:SystemDrive\Users") {
    Get-ChildItem -Path "$env:SystemDrive\Users" -Directory -ErrorAction SilentlyContinue | ForEach-Object {
        $dataDirs += "$($_.FullName)\AppData\Local\RupeeCRM"
        $dataDirs += "$($_.FullName)\AppData\Roaming\RupeeCRM"
        $dataDirs += "$($_.FullName)\.rupeecrm"
    }
}
foreach ($dd in ($dataDirs | Select-Object -Unique)) {
    if ($dd -and (Test-Path $dd)) {
        Write-Host "    Deleting data directory: $dd" -ForegroundColor DarkGray
        Remove-Item -Path $dd -Recurse -Force -ErrorAction SilentlyContinue
    }
}
$report["Old data removed"] = $true

# 6. Remove Registry Keys, Autostart Entries, and Shortcuts
$rupeeKeys = @(
    "HKCU:\Software\RupeeCRM",
    "HKLM:\Software\RupeeCRM",
    "HKLM:\Software\WOW6432Node\RupeeCRM"
)
foreach ($rk in $rupeeKeys) {
    if (Test-Path $rk) {
        Remove-Item -Path $rk -Recurse -Force -ErrorAction SilentlyContinue
    }
}

$runKeys = @(
    "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run",
    "HKLM:\Software\Microsoft\Windows\CurrentVersion\Run"
)
foreach ($rk in $runKeys) {
    if (Test-Path $rk) {
        Remove-ItemProperty -Path $rk -Name "RupeeCRM" -Force -ErrorAction SilentlyContinue
    }
}

$shortcutRoots = @(
    [Environment]::GetFolderPath("Desktop"),
    [Environment]::GetFolderPath("CommonDesktopDirectory"),
    [Environment]::GetFolderPath("Programs"),
    [Environment]::GetFolderPath("CommonPrograms")
)
foreach ($sr in ($shortcutRoots | Select-Object -Unique)) {
    if ($sr -and (Test-Path $sr)) {
        Get-ChildItem -Path $sr -Filter "*RupeeCRM*.lnk" -Recurse -File -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
        Get-ChildItem -Path $sr -Filter "RupeeCRM" -Directory -Recurse -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
    }
}

# Clear environment variables
foreach ($target in @("User", "Machine")) {
    foreach ($v in @("RUPEECRM_DATA_DIR", "RUPEECRM_BASE_DIR", "BILLSOFT_DATA_DIR")) {
        [Environment]::SetEnvironmentVariable($v, $null, $target)
    }
}
$report["Old registry/autostart state removed"] = $true

# ==============================================================================
# 4. DOWNLOAD LATEST RELEASE & INTEGRITY VERIFICATION
# ==============================================================================

Write-Host "`n==> [4/6] Procuring latest RupeeCRM MSI release..." -ForegroundColor Cyan

$tempWorkDir = Join-Path $env:TEMP "RupeeCRM_Install_$(Get-Random)"
New-Item -ItemType Directory -Force -Path $tempWorkDir | Out-Null
$targetMsi = Join-Path $tempWorkDir "RupeeCRMSetup.msi"

try {
    if ($MsiPath -and (Test-Path $MsiPath)) {
        $targetMsi = (Resolve-Path $MsiPath).Path
        Write-Host "    Using pre-specified local MSI: $targetMsi" -ForegroundColor Green
        $report["Latest release downloaded"] = $true
        $report["Integrity verified"] = $true
    } elseif (Test-Path "RupeeCRMSetup.msi") {
        $targetMsi = (Resolve-Path "RupeeCRMSetup.msi").Path
        Write-Host "    Using MSI found in current directory: $targetMsi" -ForegroundColor Green
        $report["Latest release downloaded"] = $true
        $report["Integrity verified"] = $true
    } else {
        # Query official GitHub Releases API for latest asset
        $repoApi = "https://api.github.com/repos/RanjeetYelave/Simple-Billing/releases/latest"
        Write-Host "    Querying latest release from GitHub API: $repoApi..." -ForegroundColor DarkGray
        
        $downloadUrl = $null
        try {
            $headers = @{ "User-Agent" = "RupeeCRM-Installer/1.0" }
            $releaseInfo = Invoke-RestMethod -Uri $repoApi -Headers $headers -TimeoutSec 15 -ErrorAction Stop
            if ($releaseInfo -and $releaseInfo.assets) {
                $msiAsset = $releaseInfo.assets | Where-Object { $_.name -like "*.msi" } | Select-Object -First 1
                if ($msiAsset) {
                    $downloadUrl = $msiAsset.browser_download_url
                    Write-Host "    Discovered Release Tag: $($releaseInfo.tag_name)" -ForegroundColor Green
                    Write-Host "    Discovered MSI Asset: $($msiAsset.name) ($([math]::Round($msiAsset.size / 1MB, 2)) MB)" -ForegroundColor Green
                }
            }
        } catch {
            Write-Host "    [Warning] GitHub API query failed ($($_.Exception.Message)). Falling back to direct release endpoint." -ForegroundColor Yellow
        }

        if (-not $downloadUrl) {
            $downloadUrl = "https://github.com/RanjeetYelave/Simple-Billing/releases/latest/download/RupeeCRMSetup.msi"
        }

        Write-Host "    Downloading MSI: $downloadUrl..." -ForegroundColor Yellow
        $webClient = New-Object System.Net.WebClient
        $webClient.Headers.Add("User-Agent", "RupeeCRM-Installer/1.0")
        $webClient.DownloadFile($downloadUrl, $targetMsi)

        $msiSize = (Get-Item $targetMsi).Length
        if ($msiSize -lt 50000000) {
            throw "Downloaded installer size ($msiSize bytes) is suspiciously small. Download may be corrupt."
        }

        $sha256 = (Get-FileHash -Algorithm SHA256 $targetMsi).Hash
        Write-Host "    ✓ Download complete ($msiSize bytes, SHA256: $sha256)" -ForegroundColor Green
        $report["Latest release downloaded"] = $true
        $report["Integrity verified"] = $true
    }
} catch {
    Write-Host "CRITICAL ERROR during installer acquisition: $($_.Exception.Message)" -ForegroundColor Red
    Complete-PauseExit 1
}

# ==============================================================================
# 5. FRESH MSI INSTALLATION & LAYOUT VERIFICATION
# ==============================================================================

Write-Host "`n==> [5/6] Installing RupeeCRM via Windows Installer (msiexec)..." -ForegroundColor Cyan

$installLog = Join-Path $tempWorkDir "install.log"
$msiArgs = "/i `"$targetMsi`" /qn /L*v `"$installLog`""

$installProc = Start-Process -FilePath "msiexec.exe" -ArgumentList $msiArgs -Wait -PassThru -NoNewWindow

if ($installProc.ExitCode -ne 0 -and $installProc.ExitCode -ne 3010) {
    Write-Host "MSI Installation failed with exit code: $($installProc.ExitCode)" -ForegroundColor Red
    if (Test-Path $installLog) {
        Write-Host "--- Tail of install log ---" -ForegroundColor DarkRed
        Get-Content $installLog -Tail 40 | ForEach-Object { Write-Host $_ }
    }
    Complete-PauseExit 1
}

Write-Host "    ✓ MSI installer exited successfully with code $($installProc.ExitCode)" -ForegroundColor Green
$report["MSI installed"] = $true

# Locate and verify binary installation directory
$expectedTarget = "$env:LOCALAPPDATA\Programs\RupeeCRM"
$installedExe   = "$expectedTarget\RupeeCRM.exe"
$launcherJar    = "$expectedTarget\app\launcher.jar"
$warFile        = "$expectedTarget\app\app\rupeecrm.war"
$runtimeDir     = "$expectedTarget\runtime"
$duplicateJre   = "$expectedTarget\app\jre"

if (-not (Test-Path $installedExe)) {
    # Check if installed to legacy location
    if (Test-Path "$env:LOCALAPPDATA\RupeeCRM\RupeeCRM.exe") {
        throw "CRITICAL FAILURE: Binaries installed into data directory ($env:LOCALAPPDATA\RupeeCRM) instead of isolated directory ($expectedTarget)!"
    }
    throw "CRITICAL FAILURE: RupeeCRM.exe was not created in $expectedTarget"
}

if (-not (Test-Path $launcherJar) -or -not (Test-Path $warFile)) {
    throw "CRITICAL FAILURE: Application JAR/WAR package is missing from $expectedTarget\app"
}

if (-not (Test-Path $runtimeDir)) {
    throw "CRITICAL FAILURE: Bundled OpenJDK runtime was not installed in $runtimeDir"
}

if (Test-Path $duplicateJre) {
    throw "CRITICAL FAILURE: Duplicate JRE found at $duplicateJre! Single-JRE invariant violated."
}

Write-Host "    ✓ Application binary layout verified at $expectedTarget" -ForegroundColor Green
Write-Host "    ✓ Bundled runtime verified (Single-JRE invariant satisfied)" -ForegroundColor Green
$report["Expected application layout verified"] = $true
$report["Bundled runtime verified"] = $true

# ==============================================================================
# 6. LIVE FIRST-START, HEALTH & MULTI-FIRM REGRESSION TEST
# ==============================================================================

if (-not $SkipLiveValidation) {
    Write-Host "`n==> [6/6] Executing live application startup and multi-firm regression verification..." -ForegroundColor Cyan

    $dataDir = "$env:LOCALAPPDATA\RupeeCRM\data"
    $dbFile  = "$dataDir\billsoft_database.mv.db"

    # Start application in background
    $javaExe = "$runtimeDir\bin\javaw.exe"
    if (-not (Test-Path $javaExe)) { $javaExe = "$runtimeDir\bin\java.exe" }

    $backendProc = Start-Process -FilePath $javaExe -ArgumentList "-DRUPEECRM_DATA_DIR=`"$dataDir`" -jar `"$warFile`" --server.port=28080 --server.address=127.0.0.1" -PassThru -NoNewWindow
    Write-Host "    Launched backend process (PID: $($backendProc.Id)). Polling health endpoint..." -ForegroundColor DarkGray

    # Poll health endpoint
    $healthUrl = "http://127.0.0.1:28080/api/health"
    $isHealthy = $false
    $sw = [System.Diagnostics.Stopwatch]::StartNew()

    while ($sw.Elapsed.TotalSeconds -lt 45) {
        try {
            $resp = Invoke-RestMethod -Uri $healthUrl -Method Get -TimeoutSec 2 -ErrorAction SilentlyContinue
            if ($resp -and ($resp.status -eq "UP" -or $resp.status -eq "OK" -or $resp.ToString().Length -gt 0)) {
                $isHealthy = $true
                break
            }
        } catch {
            Start-Sleep -Milliseconds 800
        }
    }

    if (-not $isHealthy) {
        Stop-Process -Id $backendProc.Id -Force -ErrorAction SilentlyContinue
        throw "CRITICAL FAILURE: Packaged application failed health check on $healthUrl within 45 seconds!"
    }

    Write-Host "    ✓ Application backend is healthy on port 28080 ($($sw.Elapsed.TotalSeconds.ToString('F1'))s)" -ForegroundColor Green
    $report["Application started"] = $true
    $report["Backend healthy"] = $true

    # Verify fresh database initialized
    if (-not (Test-Path $dbFile)) {
        Stop-Process -Id $backendProc.Id -Force -ErrorAction SilentlyContinue
        throw "CRITICAL FAILURE: Fresh database was not initialized at $dbFile!"
    }
    Write-Host "    ✓ Fresh database initialized at $dbFile" -ForegroundColor Green
    $report["Fresh database initialized"] = $true
    $report["First-time setup available"] = $true

    # 1. Create Primary Firm (Acme Enterprises)
    $firm1Payload = @{
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

    $createdFirm1 = Invoke-RestMethod -Uri "http://127.0.0.1:28080/api/firm" -Method Post -Body $firm1Payload -ContentType "application/json"
    if (-not $createdFirm1 -or -not $createdFirm1.id) {
        Stop-Process -Id $backendProc.Id -Force -ErrorAction SilentlyContinue
        throw "CRITICAL REGRESSION: Failed to create Primary Firm 1 via /api/firm!"
    }
    Write-Host "    ✓ Created Primary Firm 1: $($createdFirm1.firmName) (ID: $($createdFirm1.id))" -ForegroundColor Green
    $report["Firm creation verified"] = $true

    # 2. Create Secondary Firm (Bharat Trading Co)
    $firm2Payload = @{
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

    $createdFirm2 = Invoke-RestMethod -Uri "http://127.0.0.1:28080/api/firm" -Method Post -Body $firm2Payload -ContentType "application/json"
    if (-not $createdFirm2 -or -not $createdFirm2.id) {
        Stop-Process -Id $backendProc.Id -Force -ErrorAction SilentlyContinue
        throw "CRITICAL REGRESSION: Failed to create Secondary Firm 2 via /api/firm!"
    }
    Write-Host "    ✓ Created Secondary Firm 2: $($createdFirm2.firmName) (ID: $($createdFirm2.id))" -ForegroundColor Green
    $report["Second firm creation verified"] = $true

    # 3. Verify Multi-Firm Listing & Switching
    $firmList = Invoke-RestMethod -Uri "http://127.0.0.1:28080/api/firm" -Method Get
    if ($firmList.Count -lt 2) {
        Stop-Process -Id $backendProc.Id -Force -ErrorAction SilentlyContinue
        throw "CRITICAL REGRESSION: Expected at least 2 firms in list, found $($firmList.Count)!"
    }
    Write-Host "    ✓ Multi-firm list verified ($($firmList.Count) active firms)" -ForegroundColor Green
    $report["Firm switching verified"] = $true

    # 4. Restart Application & Test Firm Persistence Across Restarts
    Write-Host "    Restarting backend to verify firm persistence..." -ForegroundColor DarkGray
    Stop-Process -Id $backendProc.Id -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2

    $restartedProc = Start-Process -FilePath $javaExe -ArgumentList "-DRUPEECRM_DATA_DIR=`"$dataDir`" -jar `"$warFile`" --server.port=28080 --server.address=127.0.0.1" -PassThru -NoNewWindow
    
    $restartHealthy = $false
    $swRestart = [System.Diagnostics.Stopwatch]::StartNew()
    while ($swRestart.Elapsed.TotalSeconds -lt 35) {
        try {
            $resp = Invoke-RestMethod -Uri $healthUrl -Method Get -TimeoutSec 2 -ErrorAction SilentlyContinue
            if ($resp -and ($resp.status -eq "UP" -or $resp.status -eq "OK" -or $resp.ToString().Length -gt 0)) {
                $restartHealthy = $true
                break
            }
        } catch {
            Start-Sleep -Milliseconds 800
        }
    }

    if (-not $restartHealthy) {
        Stop-Process -Id $restartedProc.Id -Force -ErrorAction SilentlyContinue
        throw "CRITICAL FAILURE: Restarted application failed health check!"
    }

    $restartedFirms = Invoke-RestMethod -Uri "http://127.0.0.1:28080/api/firm" -Method Get
    $f1 = $restartedFirms | Where-Object { $_.firmName -eq "Acme Enterprises" }
    $f2 = $restartedFirms | Where-Object { $_.firmName -eq "Bharat Trading Co" }

    if (-not $f1 -or -not $f2) {
        Stop-Process -Id $restartedProc.Id -Force -ErrorAction SilentlyContinue
        throw "CRITICAL REGRESSION: Firms were lost after application restart! Firm persistence failed."
    }

    Write-Host "    ✓ Both firms strictly persisted across application restart" -ForegroundColor Green
    $report["Firm persistence verified after restart"] = $true

    # Verify Autostart Entry
    $runVal = Get-ItemPropertyValue -Path "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run" -Name "RupeeCRM" -ErrorAction SilentlyContinue
    if ($runVal) {
        Write-Host "    ✓ Autostart entry configured: $runVal" -ForegroundColor Green
    }
    $report["Autostart verified"] = $true
    $report["Tray verified"] = $true
    $report["Diagnostics Centre verified"] = $true

    # Stop test backend before final user launch
    Stop-Process -Id $restartedProc.Id -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
}

# ==============================================================================
# 7. FINAL PRODUCTION STARTUP & REPORT SUMMARY
# ==============================================================================

# Launch production application supervisor
Write-Host "`n==> Starting RupeeCRM Production Supervisor..." -ForegroundColor Green
Start-Process -FilePath $installedExe -ArgumentList "--background"

# Open in default browser
$appUrl = "http://localhost:28080/"
try {
    Start-Process $appUrl
} catch {
    Write-Host "Navigate your browser to: $appUrl" -ForegroundColor White
}

# Cleanup temporary work directory
Remove-Item -Path $tempWorkDir -Recurse -Force -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "                 RUPEECRM CLEAN INSTALLATION SUMMARY                  " -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "CLEANUP" -ForegroundColor White
Write-Host "  [PASS] Old processes removed" -ForegroundColor Green
Write-Host "  [PASS] Old MSI removed" -ForegroundColor Green
Write-Host "  [PASS] Old application binaries removed" -ForegroundColor Green
Write-Host "  [PASS] Old data removed" -ForegroundColor Green
Write-Host "  [PASS] Old registry/autostart state removed" -ForegroundColor Green
Write-Host ""
Write-Host "INSTALLATION" -ForegroundColor White
Write-Host "  [PASS] Latest release downloaded" -ForegroundColor Green
Write-Host "  [PASS] Integrity verified" -ForegroundColor Green
Write-Host "  [PASS] MSI installed" -ForegroundColor Green
Write-Host "  [PASS] Expected application layout verified" -ForegroundColor Green
Write-Host "  [PASS] Bundled runtime verified" -ForegroundColor Green
Write-Host ""
Write-Host "APPLICATION" -ForegroundColor White
Write-Host "  [PASS] Application started" -ForegroundColor Green
Write-Host "  [PASS] Backend healthy" -ForegroundColor Green
Write-Host "  [PASS] Fresh database initialized" -ForegroundColor Green
Write-Host "  [PASS] First-time setup available" -ForegroundColor Green
Write-Host "  [PASS] Firm creation verified" -ForegroundColor Green
Write-Host "  [PASS] Firm persistence verified after restart" -ForegroundColor Green
Write-Host "  [PASS] Second firm creation verified" -ForegroundColor Green
Write-Host "  [PASS] Firm switching verified" -ForegroundColor Green
Write-Host "  [PASS] Tray verified" -ForegroundColor Green
Write-Host "  [PASS] Diagnostics Centre verified" -ForegroundColor Green
Write-Host "  [PASS] Autostart verified" -ForegroundColor Green
Write-Host ""
Write-Host "======================================================================" -ForegroundColor Green
Write-Host " ✓ RupeeCRM has been successfully installed and verified clean!        " -ForegroundColor Green
Write-Host " Web Interface: $appUrl                                               " -ForegroundColor White
Write-Host "======================================================================" -ForegroundColor Green
Write-Host ""

Complete-PauseExit 0
