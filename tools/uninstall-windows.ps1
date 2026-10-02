# ==============================================================================
# RupeeCRM Windows Uninstaller
# ==============================================================================
# Usage (PowerShell):
#   .\tools\uninstall-windows.ps1
#   .\tools\uninstall-windows.ps1 -PurgeData
#   .\tools\uninstall-windows.ps1 -PurgeAllCustomerData
#
# This script:
# 1. Stops running RupeeCRM and backend JVM processes gracefully.
# 2. Removes application binaries from %LOCALAPPDATA%\Programs\RupeeCRM.
# 3. Removes Desktop and Start Menu shortcuts.
# 4. Cleans up Windows auto-start registry entries and Startup folder scripts.
# 5. PRESERVES customer database by default unless -PurgeData is specified.
# ==============================================================================

[CmdletBinding()]
param(
    [Alias("PurgeData", "Purge", "PurgeAll", "Clean", "p")]
    [switch]$PurgeAllCustomerData = $false,

    [Alias("q")]
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
Write-Step "Checking for running RupeeCRM and backend processes..."
$installPrefix = Join-Path $env:LOCALAPPDATA "Programs\RupeeCRM"
$processes = Get-Process -ErrorAction SilentlyContinue | Where-Object {
    $_.Name -in @("RupeeCRM", "Billsoft", "java", "javaw", "msedgewebview2") -and (
        $_.Name -in @("RupeeCRM", "Billsoft") -or (
            try {
                $_.Path -and (
                    $_.Path.StartsWith($installPrefix, [System.StringComparison]::OrdinalIgnoreCase) -or
                    $_.Path.Contains("RupeeCRM") -or
                    $_.Path.Contains("Billsoft") -or
                    $_.Path.Contains("SimpleBilling")
                )
            } catch { $false }
        )
    )
}

if ($processes) {
    Write-Step "Stopping running RupeeCRM processes..."
    foreach ($p in $processes) {
        try {
            $p.CloseMainWindow() | Out-Null
            Start-Sleep -Milliseconds 200
            if (-not $p.HasExited) {
                $p.Kill()
                $p.WaitForExit(2000)
            }
        } catch {
            Write-WarnMsg "Process termination note: $($_.Exception.Message)"
        }
    }
    Start-Sleep -Milliseconds 500
    Write-Success "RupeeCRM processes stopped."
} else {
    Write-Success "No active RupeeCRM processes found."
}

# ------------------------------------------------------------------------------
# 2. Remove Auto-Start Entries & Registry Keys
# ------------------------------------------------------------------------------
Write-Step "Removing Windows auto-start registrations..."

# 2a. HKCU Registry (Authoritative .NET Registry API)
try {
    $runKey = [Microsoft.Win32.Registry]::CurrentUser.OpenSubKey("Software\Microsoft\Windows\CurrentVersion\Run", $true)
    if ($runKey) {
        foreach ($val in @("RupeeCRMService", "BillsoftService")) {
            $runKey.DeleteValue($val, $false)
        }
        $runKey.Close()
        Write-Success "Removed registry auto-start registrations"
    }
} catch {
    Write-WarnMsg "Registry removal note: $($_.Exception.Message)"
}

# 2b. Cleanup Software & Uninstall registry keys
try {
    foreach ($root in @("HKCU:\Software", "HKLM:\Software")) {
        foreach ($sub in @("RupeeCRM", "SimpleBilling", "Billsoft")) {
            $regPath = Join-Path $root $sub
            if (Test-Path $regPath) {
                Remove-Item -Path $regPath -Recurse -Force -ErrorAction SilentlyContinue
            }
        }
        $uninstallRoot = Join-Path $root "Microsoft\Windows\CurrentVersion\Uninstall"
        foreach ($app in @("RupeeCRM", "Billsoft")) {
            $uPath = Join-Path $uninstallRoot $app
            if (Test-Path $uPath) {
                Remove-Item -Path $uPath -Recurse -Force -ErrorAction SilentlyContinue
            }
        }
    }
} catch {}

# 2c. Startup Folder Scripts
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

# 3a. Desktop shortcuts (User and Public)
$desktopPaths = @(
    [Environment]::GetFolderPath("Desktop"),
    (Join-Path $env:USERPROFILE "Desktop"),
    [Environment]::GetFolderPath("CommonDesktopDirectory"),
    (Join-Path $env:PUBLIC "Desktop")
) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -Unique

foreach ($dp in $desktopPaths) {
    foreach ($lnk in @("RupeeCRM.lnk", "Billsoft.lnk")) {
        $lnkPath = Join-Path $dp $lnk
        if (Test-Path $lnkPath) {
            Remove-Item -Path $lnkPath -Force -ErrorAction SilentlyContinue
            Write-Success "Removed Desktop shortcut: $lnkPath"
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
        for ($i = 0; $i -lt 5; $i++) {
            try {
                Remove-Item -Path $dir -Recurse -Force -ErrorAction SilentlyContinue
                if (-not (Test-Path $dir)) { break }
            } catch {}
            Start-Sleep -Milliseconds 300
        }
        if (Test-Path $dir) {
            cmd.exe /c "rmdir /s /q `"$dir`" >nul 2>nul"
        }
        if (-not (Test-Path $dir)) {
            Write-Success "Removed application files from $dir"
        } else {
            Write-WarnMsg "Could not remove some files in $dir (may be in use or locked)"
        }
    }
}

# ------------------------------------------------------------------------------
# 5. Customer Data Handling
# ------------------------------------------------------------------------------
$primaryDataDir = Join-Path $env:APPDATA "SimpleBilling"
$allDataDirs = @(
    (Join-Path $env:APPDATA "SimpleBilling"),
    (Join-Path $env:APPDATA "RupeeCRM"),
    (Join-Path $env:APPDATA "Billsoft"),
    (Join-Path $env:LOCALAPPDATA "RupeeCRM"),
    (Join-Path $env:LOCALAPPDATA "SimpleBilling"),
    (Join-Path $env:LOCALAPPDATA "Billsoft"),
    (Join-Path $env:USERPROFILE ".simplebilling"),
    (Join-Path $env:USERPROFILE ".rupeecrm"),
    (Join-Path $env:USERPROFILE ".billsoft"),
    (Join-Path $env:ProgramData "RupeeCRM"),
    (Join-Path $env:ProgramData "SimpleBilling"),
    "C:\RupeeCRM",
    "C:\SimpleBilling"
)

if ($PurgeAllCustomerData) {
    if (-not $Quiet) {
        Write-Host ""
        Write-Host "======================================================" -ForegroundColor Red
        Write-Host "        CAUTION: PERMANENT DATA PURGE REQUESTED        " -ForegroundColor White
        Write-Host "======================================================" -ForegroundColor Red
        Write-Host "This will PERMANENTLY delete your customer database, invoices, backups, WebView2 caches, and reports in:" -ForegroundColor Yellow
        foreach ($d in $allDataDirs) {
            if (Test-Path $d) {
                Write-Host "  - $d" -ForegroundColor White
            }
        }
        Write-Host ""
        $confirm = Read-Host "Are you sure you want to permanently delete all customer data? (Type YES to confirm)"
        if ($confirm -ne "YES") {
            Write-Host "Data purge cancelled. Customer database preserved." -ForegroundColor Green
            $PurgeAllCustomerData = $false
        }
    }
}

if ($PurgeAllCustomerData) {
    Write-Step "Purging all database files, backups, and browser state..."
    foreach ($d in $allDataDirs) {
        if (Test-Path $d) {
            for ($i = 0; $i -lt 3; $i++) {
                try {
                    Remove-Item -Path $d -Recurse -Force -ErrorAction SilentlyContinue
                    if (-not (Test-Path $d)) { break }
                } catch {}
                Start-Sleep -Milliseconds 200
            }
            if (Test-Path $d) {
                cmd.exe /c "attrib -r -s -h `"$d\*.*`" /s /d >nul 2>nul"
                cmd.exe /c "rmdir /s /q `"$d`" >nul 2>nul"
            }
            if (-not (Test-Path $d)) {
                Write-Success "Purged data directory: $d"
            } else {
                Write-WarnMsg "Could not completely purge $d (may contain locked files)"
            }
        }
    }
} else {
    if (Test-Path $primaryDataDir) {
        Write-Host ""
        Write-Host "======================================================" -ForegroundColor Green
        Write-Host "              CUSTOMER DATA PRESERVED                 " -ForegroundColor White
        Write-Host "======================================================" -ForegroundColor Green
        Write-Host "Your invoices, customer records, and database remain safely stored in:" -ForegroundColor Gray
        Write-Host "  $primaryDataDir" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "If you reinstall RupeeCRM in the future, all your data will be restored automatically." -ForegroundColor Gray
        Write-Host "(To completely wipe all data for a fresh start, run: .\tools\uninstall-windows.ps1 -PurgeData or deleteall.bat)" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "======================================================" -ForegroundColor Green
Write-Host "          RupeeCRM Uninstallation Complete            " -ForegroundColor White
Write-Host "======================================================" -ForegroundColor Green
Write-Host ""
