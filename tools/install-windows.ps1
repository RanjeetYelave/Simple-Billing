<#
.SYNOPSIS
    One-Command Windows Automated Installer & Clean Upgrader for RupeeCRM.
.DESCRIPTION
    Stops any previous running RupeeCRM instances, removes old application binaries,
    installs the latest RupeeCRM MSI package, verifies application health, and launches
    the application in the default web browser. Customer data is safely preserved.
.EXAMPLE
    # Direct execution in PowerShell:
    irm https://raw.githubusercontent.com/RanjeetYelave/Simple-Billing/main/tools/install-windows.ps1 | iex

    # From Windows Command Prompt (CMD) or Run dialog:
    powershell -ExecutionPolicy Bypass -Command "irm https://raw.githubusercontent.com/RanjeetYelave/Simple-Billing/main/tools/install-windows.ps1 | iex"
#>

[CmdletBinding()]
param(
    [string]$MsiPath = "",
    [switch]$ForceCleanData
)

$ErrorActionPreference = "Stop"

# Use TLS 1.2 for modern GitHub downloads
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "         RupeeCRM Unified Windows One-Command Installer    " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
Write-Host ""

# ------------------------------------------------------------------------------
# 1. Stop Existing RupeeCRM Processes
# ------------------------------------------------------------------------------
Write-Host "==> [1/5] Stopping previous RupeeCRM instances..." -ForegroundColor Cyan

Get-Process -ErrorAction SilentlyContinue | Where-Object {
    try {
        ($_.ProcessName -like "*RupeeCRM*") -or ($_.Path -and $_.Path -like "*RupeeCRM*")
    } catch { $false }
} | ForEach-Object {
    Write-Host "    Terminating active process: $($_.ProcessName) (PID: $($_.Id))" -ForegroundColor Yellow
    Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
}

# Also gracefully terminate any orphaned backend java/javaw processes
Get-CimInstance Win32_Process -Filter "Name = 'java.exe' or Name = 'javaw.exe'" -ErrorAction SilentlyContinue | Where-Object {
    $_.CommandLine -and ($_.CommandLine -like "*rupeecrm*" -or $_.CommandLine -like "*launcher.jar*" -or $_.CommandLine -like "*RUPEECRM_DATA_DIR*")
} | ForEach-Object {
    Write-Host "    Terminating backend Java process (PID: $($_.ProcessId))" -ForegroundColor Yellow
    Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
}

Start-Sleep -Seconds 1

# ------------------------------------------------------------------------------
# 2. Clean Up Old Application Binaries
# ------------------------------------------------------------------------------
Write-Host "==> [2/5] Cleaning up old application binaries..." -ForegroundColor Cyan

$binaryDir = "$env:LOCALAPPDATA\Programs\RupeeCRM"
if (Test-Path $binaryDir) {
    Write-Host "    Removing previous installation at $binaryDir..." -ForegroundColor DarkGray
    try {
        Remove-Item -Path $binaryDir -Recurse -Force -ErrorAction SilentlyContinue
    } catch {}
}

# If user explicitly requested a complete data wipe
if ($ForceCleanData) {
    $dataDir = "$env:LOCALAPPDATA\RupeeCRM"
    if (Test-Path $dataDir) {
        Write-Host "    [Warning] Purging customer data directory as requested: $dataDir" -ForegroundColor Red
        Remove-Item -Path $dataDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

# ------------------------------------------------------------------------------
# 3. Locate or Download MSI Package
# ------------------------------------------------------------------------------
Write-Host "==> [3/5] Preparing RupeeCRM installer..." -ForegroundColor Cyan

$tempMsi = "$env:TEMP\RupeeCRMSetup.msi"

if ($MsiPath -and (Test-Path $MsiPath)) {
    $installerMsi = (Resolve-Path $MsiPath).Path
    Write-Host "    Using local MSI installer: $installerMsi" -ForegroundColor Green
} elseif (Test-Path "RupeeCRMSetup.msi") {
    $installerMsi = (Resolve-Path "RupeeCRMSetup.msi").Path
    Write-Host "    Using MSI installer from current folder: $installerMsi" -ForegroundColor Green
} else {
    $downloadUrl = "https://github.com/RanjeetYelave/Simple-Billing/releases/latest/download/RupeeCRMSetup.msi"
    Write-Host "    Downloading latest release from GitHub..." -ForegroundColor Yellow
    Write-Host "    URL: $downloadUrl" -ForegroundColor DarkGray
    
    try {
        $wc = New-Object System.Net.WebClient
        $wc.DownloadFile($downloadUrl, $tempMsi)
        $installerMsi = $tempMsi
        Write-Host "    Downloaded: $((Get-Item $installerMsi).Length) bytes" -ForegroundColor Green
    } catch {
        throw "Failed to download RupeeCRM installer from GitHub. Check internet connection: $($_.Exception.Message)"
    }
}

# ------------------------------------------------------------------------------
# 4. Execute Silent MSI Installation
# ------------------------------------------------------------------------------
Write-Host "==> [4/5] Installing RupeeCRM..." -ForegroundColor Cyan

$installLog = "$env:TEMP\rupeecrm_install.log"
if (Test-Path $installLog) { Remove-Item $installLog -Force -ErrorAction SilentlyContinue }

$msiArgs = "/i `"$installerMsi`" /qn /L*v `"$installLog`""
$process = Start-Process -FilePath "msiexec.exe" -ArgumentList $msiArgs -Wait -PassThru -NoNewWindow

if ($process.ExitCode -ne 0 -and $process.ExitCode -ne 3010) {
    Write-Host "MSI Installation failed with exit code: $($process.ExitCode)" -ForegroundColor Red
    if (Test-Path $installLog) {
        Write-Host "--- Last 50 lines of installer log ---" -ForegroundColor DarkRed
        Get-Content $installLog -Tail 50 | ForEach-Object { Write-Host $_ }
    }
    throw "MSI Installation failed with exit code $($process.ExitCode)"
}

Write-Host "    ✓ Installation completed successfully (ExitCode: $($process.ExitCode))" -ForegroundColor Green

# ------------------------------------------------------------------------------
# 5. Verify & Launch Application
# ------------------------------------------------------------------------------
Write-Host "==> [5/5] Verifying installation and starting RupeeCRM..." -ForegroundColor Cyan

$installedExe = "$env:LOCALAPPDATA\Programs\RupeeCRM\RupeeCRM.exe"
if (-not (Test-Path $installedExe)) {
    # Check fallback location
    $fallbackExe = "$env:LOCALAPPDATA\RupeeCRM\RupeeCRM.exe"
    if (Test-Path $fallbackExe) {
        $installedExe = $fallbackExe
    } else {
        throw "Installation verification failed: RupeeCRM.exe not found in $installedExe"
    }
}

Write-Host "    Starting RupeeCRM ($installedExe)..." -ForegroundColor Green
Start-Process -FilePath $installedExe -ArgumentList "--background"

# Wait for backend health
$healthUrl = "http://127.0.0.1:28080/api/health"
$appUrl    = "http://localhost:28080/"

Write-Host "    Waiting for backend service to initialize..." -ForegroundColor DarkGray
$isHealthy = $false
$sw = [System.Diagnostics.Stopwatch]::StartNew()

while ($sw.Elapsed.TotalSeconds -lt 40) {
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

if ($isHealthy) {
    Write-Host "    ✓ RupeeCRM backend is healthy on port 28080 ($($sw.Elapsed.TotalSeconds.ToString('F1'))s)" -ForegroundColor Green
} else {
    Write-Host "    [Note] Backend starting in background. Opening UI..." -ForegroundColor Yellow
}

# Open web interface in default browser
try {
    Start-Process $appUrl
} catch {
    Write-Host "Open your browser and navigate to: $appUrl" -ForegroundColor White
}

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "  ✓ RupeeCRM Installation & Setup Complete!               " -ForegroundColor Green
Write-Host "  URL: $appUrl                                            " -ForegroundColor White
Write-Host "==========================================================" -ForegroundColor Green
Write-Host ""
