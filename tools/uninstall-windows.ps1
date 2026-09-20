# ==============================================================================
# RupeeCRM Windows Uninstaller
# ==============================================================================
# Usage (PowerShell):
#   .\tools\uninstall-windows.ps1
#
# This script:
# 1. Stops running RupeeCRM processes gracefully.
# 2. Removes application binaries from %LOCALAPPDATA%\Programs\RupeeCRM.
# 3. Removes Desktop and Start Menu shortcuts.
# 4. Cleans up Windows auto-start registry entries and Startup folder scripts.
# 5. PRESERVES customer database and business data in %APPDATA%\SimpleBilling.
# ==============================================================================

[CmdletBinding()]
param(
    [switch]$PurgeAllCustomerData = $false,
    [switch]$Quiet = $false
)

$ErrorActionPreference = "Continue"

function Write-Step {
    param([string]$Message)
    Write-Host "[+] $Message" -ForegroundColor Cyan
}

function Write-Success {
    param([string]$Message)
    Write-Host "[✓] $Message" -ForegroundColor Green
}

function Write-WarnMsg {
    param([string]$Message)
    Write-Host "[!] $Message" -ForegroundColor Yellow
}

Write-Host "======================================================" -ForegroundColor Blue
Write-Host "           RupeeCRM Windows Uninstaller               " -ForegroundColor White
Write-Host "======================================================" -ForegroundColor Blue
Write-Host ""

# ------------------------------------------------------------------------------
# 1. Stop Running Processes
# ------------------------------------------------------------------------------
Write-Step "Checking for running RupeeCRM processes..."
$processes = Get-Process -Name "RupeeCRM", "Billsoft" -ErrorAction SilentlyContinue

if ($processes) {
    Write-Step "Stopping running RupeeCRM processes..."
    foreach ($p in $processes) {
        try {
            $p.CloseMainWindow() | Out-Null
            Start-Sleep -Milliseconds 500
            if (-not $p.HasExited) {
                $p.Kill()
                $p.WaitForExit(3000)
            }
        } catch {
            Write-WarnMsg "Process termination note: $($_.Exception.Message)"
        }
    }
    Start-Sleep -Seconds 1
    Write-Success "RupeeCRM processes stopped."
} else {
    Write-Success "No active RupeeCRM processes found."
}

# ------------------------------------------------------------------------------
# 2. Remove Auto-Start Entries
# ------------------------------------------------------------------------------
Write-Step "Removing Windows auto-start registrations..."

# 2a. HKCU Registry
$runKey = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run"
foreach ($val in @("RupeeCRMService", "BillsoftService")) {
    if (Test-Path $runKey) {
        if (Get-ItemProperty -Path $runKey -Name $val -ErrorAction SilentlyContinue) {
            Remove-ItemProperty -Path $runKey -Name $val -Force -ErrorAction SilentlyContinue
        }
    }
    reg.exe delete "HKCU\Software\Microsoft\Windows\CurrentVersion\Run" /v $val /f 2>$null | Out-Null
    Write-Success "Removed registry auto-start value: $val"
}

# 2b. Startup Folder Scripts
$startupFolder = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\Startup"
foreach ($vbs in @("RupeeCRM.vbs", "Billsoft.vbs")) {
    $vbsPath = Join-Path $startupFolder $vbs
    if (Test-Path $vbsPath) {
        Remove-Item -Path $vbsPath -Force -ErrorAction SilentlyContinue
        Write-Success "Removed startup folder script: $vbs"
    }
}

# ------------------------------------------------------------------------------
# 3. Remove Desktop & Start Menu Shortcuts
# ------------------------------------------------------------------------------
Write-Step "Removing shortcuts..."

# 3a. Desktop shortcuts
$desktopPath = [Environment]::GetFolderPath("Desktop")
if ($desktopPath) {
    foreach ($lnk in @("RupeeCRM.lnk", "Billsoft.lnk")) {
        $lnkPath = Join-Path $desktopPath $lnk
        if (Test-Path $lnkPath) {
            Remove-Item -Path $lnkPath -Force -ErrorAction SilentlyContinue
            Write-Success "Removed Desktop shortcut: $lnk"
        }
    }
}

# 3b. Start Menu folders
$startMenuPrograms = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs"
foreach ($dir in @("RupeeCRM", "Billsoft")) {
    $menuDir = Join-Path $startMenuPrograms $dir
    if (Test-Path $menuDir) {
        Remove-Item -Path $menuDir -Recurse -Force -ErrorAction SilentlyContinue
        Write-Success "Removed Start Menu directory: $dir"
    }
}

# ------------------------------------------------------------------------------
# 4. Remove Installed Binaries
# ------------------------------------------------------------------------------
Write-Step "Removing installed application binaries..."

$installDirs = @(
    (Join-Path $env:LOCALAPPDATA "Programs\RupeeCRM"),
    (Join-Path $env:LOCALAPPDATA "Programs\RupeeCRM.old"),
    (Join-Path $env:LOCALAPPDATA "Programs\Billsoft")
)

foreach ($dir in $installDirs) {
    if (Test-Path $dir) {
        try {
            Remove-Item -Path $dir -Recurse -Force -ErrorAction Stop
            Write-Success "Removed application files from $dir"
        } catch {
            Write-WarnMsg "Could not remove some files in $dir (may be in use or locked): $($_.Exception.Message)"
        }
    }
}

# ------------------------------------------------------------------------------
# 5. Customer Data Safety
# ------------------------------------------------------------------------------
$dataDir = Join-Path $env:APPDATA "SimpleBilling"

if ($PurgeAllCustomerData) {
    if (-not $Quiet) {
        Write-Host ""
        Write-Host "======================================================" -ForegroundColor Red
        Write-Host "        CAUTION: PERMANENT DATA PURGE REQUESTED        " -ForegroundColor White
        Write-Host "======================================================" -ForegroundColor Red
        Write-Host "This will PERMANENTLY delete your customer database, invoices, backups, and reports in:" -ForegroundColor Yellow
        Write-Host "  $dataDir" -ForegroundColor White
        Write-Host ""
        $confirm = Read-Host "Are you sure you want to permanently delete all customer data? (Type YES to confirm)"
        if ($confirm -ne "YES") {
            Write-Host "Data purge cancelled. Customer database preserved." -ForegroundColor Green
            $PurgeAllCustomerData = $false
        }
    }
}

if ($PurgeAllCustomerData) {
    if (Test-Path $dataDir) {
        Remove-Item -Path $dataDir -Recurse -Force -ErrorAction SilentlyContinue
        Write-Success "Purged customer data directory: $dataDir"
    }
} else {
    if (Test-Path $dataDir) {
        Write-Host ""
        Write-Host "======================================================" -ForegroundColor Green
        Write-Host "              CUSTOMER DATA PRESERVED                 " -ForegroundColor White
        Write-Host "======================================================" -ForegroundColor Green
        Write-Host "Your invoices, customer records, and database remain safely stored in:" -ForegroundColor Gray
        Write-Host "  $dataDir" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "If you reinstall RupeeCRM in the future, all your data will be restored automatically." -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "======================================================" -ForegroundColor Green
Write-Host "          RupeeCRM Uninstallation Complete            " -ForegroundColor White
Write-Host "======================================================" -ForegroundColor Green
Write-Host ""
