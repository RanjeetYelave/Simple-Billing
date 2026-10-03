<#
.SYNOPSIS
    Standalone Forensic Cleanup & Complete Factory Reset Utility for RupeeCRM.
.DESCRIPTION
    Permanently removes all RupeeCRM application binaries, databases, customer data,
    licenses, machine identities, backups, logs, registry keys, MSI installer entries,
    services, scheduled tasks, shortcuts, and legacy remnants from the system.
.PARAMETER DryRun
    Discovers and displays all artifacts without deleting anything.
#>

[CmdletBinding()]
param(
    [switch]$DryRun,
    [switch]$Force,
    [switch]$NoElevation,
    [switch]$Diagnostics
)

# Keep console open on exit under all conditions
function Pause-And-Exit {
    param([int]$Code = 0)
    try {
        if ([Environment]::UserInteractive -and -not [Console]::IsInputRedirected) {
            Write-Host ""
            Write-Host "Press Enter to exit this window..." -ForegroundColor Gray
            [void][System.Console]::ReadLine()
        }
    } catch {}
    exit $Code
}

# ==============================================================================
# 1. ROBUST ELEVATION HANDLER
# ==============================================================================

function Check-IsAdmin {
    try {
        $id = [Security.Principal.WindowsIdentity]::GetCurrent()
        $p = New-Object Security.Principal.WindowsPrincipal($id)
        return $p.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    } catch {
        return $false
    }
}

if (-not (Check-IsAdmin) -and -not $NoElevation) {
    Write-Host "======================================================================" -ForegroundColor Yellow
    Write-Host " Administrator privileges required. Requesting elevation (UAC)...    " -ForegroundColor Yellow
    Write-Host "======================================================================" -ForegroundColor Yellow

    $scriptFile = $MyInvocation.MyCommand.Definition
    if (-not $scriptFile) { $scriptFile = $PSCommandPath }

    $dryRunArg = if ($DryRun) { "-DryRun" } else { "" }

    try {
        Start-Process powershell.exe -ArgumentList "-NoExit -NoProfile -ExecutionPolicy Bypass -File `"$scriptFile`" $dryRunArg" -Verb RunAs
        exit 0
    } catch {
        Write-Host "Failed to elevate automatically: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "Please right-click PowerShell, choose 'Run as Administrator', and run this script." -ForegroundColor Yellow
        Pause-And-Exit 1
    }
}

# ==============================================================================
# 2. BANNER & CONFIRMATION
# ==============================================================================

Write-Host ""
Write-Host "======================================================================" -ForegroundColor Red
Write-Host "             RUPEECRM COMPLETE FACTORY RESET (DESTRUCTIVE)            " -ForegroundColor Red
Write-Host "======================================================================" -ForegroundColor Red
Write-Host ""
Write-Host " WARNING: This utility performs a full, permanent factory reset." -ForegroundColor Yellow
Write-Host " It will IRREVOCABLY DELETE all of the following:" -ForegroundColor Yellow
Write-Host "   - Application Binaries (%LOCALAPPDATA%\Programs\RupeeCRM)" -ForegroundColor DarkYellow
Write-Host "   - Customer Database & All Billing Data (%LOCALAPPDATA%\RupeeCRM\data)" -ForegroundColor DarkYellow
Write-Host "   - Automated Backups, Licensing & Machine ID Tokens" -ForegroundColor DarkYellow
Write-Host "   - Diagnostic Logs, Cache, Staging & Updater State" -ForegroundColor DarkYellow
Write-Host "   - Windows Registry Keys & Startup Autostart Entries" -ForegroundColor DarkYellow
Write-Host "   - MSI Installer Registrations & Shortcuts" -ForegroundColor DarkYellow
Write-Host ""

if ($Diagnostics) {
    Write-Host "======================================================================" -ForegroundColor Cyan
    Write-Host " DIAGNOSTICS AUDIT MODE - SCANNING SYSTEM STATE" -ForegroundColor Cyan
    Write-Host "======================================================================" -ForegroundColor Cyan
    Write-Host ""
} elseif ($DryRun) {
    Write-Host "======================================================================" -ForegroundColor Cyan
    Write-Host " DRY RUN MODE ENABLED - NO DESTRUCTIVE CHANGES WILL BE PERFORMED" -ForegroundColor Cyan
    Write-Host "======================================================================" -ForegroundColor Cyan
    Write-Host ""
} elseif ($Force) {
    Write-Host "Non-interactive force switch supplied. Commencing complete purge..." -ForegroundColor Red
} else {
    $expectedPhrase = "DELETE EVERYTHING AND START FRESH"
    Write-Host "To confirm complete factory reset, type EXACTLY:" -ForegroundColor White
    Write-Host "  $expectedPhrase" -ForegroundColor Magenta
    Write-Host ""
    $inputPhrase = Read-Host "Type confirmation phrase"

    if ($inputPhrase.Trim() -ne $expectedPhrase) {
        Write-Host ""
        Write-Host "Confirmation did not match. Aborting immediately with ZERO modifications." -ForegroundColor Green
        Pause-And-Exit 0
    }
    Write-Host ""
    Write-Host "Confirmation accepted. Commencing complete purge..." -ForegroundColor Red
}

# ==============================================================================
# 3. DISCOVERY & PURGE EXECUTION
# ==============================================================================

$stats = @{
    Processes   = 0
    MSI         = 0
    Services    = 0
    Tasks       = 0
    Directories = 0
    Registry    = 0
    Shortcuts   = 0
    Environment = 0
    Failures    = 0
}

# --- A. PROCESS CLEANUP ---
Write-Host "`n[1/8] Checking for running RupeeCRM processes..." -ForegroundColor Cyan
$procs = Get-Process -ErrorAction SilentlyContinue | Where-Object {
    try {
        ($_.ProcessName -like "*RupeeCRM*") -or ($_.Path -and $_.Path -like "*RupeeCRM*")
    } catch { $false }
}

# Also check for RupeeCRM java processes
$javaProcs = Get-CimInstance Win32_Process -Filter "Name = 'java.exe' or Name = 'javaw.exe'" -ErrorAction SilentlyContinue | Where-Object {
    $_.CommandLine -and ($_.CommandLine -like "*rupeecrm*" -or $_.CommandLine -like "*launcher.jar*" -or $_.CommandLine -like "*RUPEECRM_DATA_DIR*")
}

foreach ($p in $procs) {
    Write-Host "  Found process: $($p.ProcessName) (PID: $($p.Id))" -ForegroundColor Yellow
    if (-not $DryRun) {
        try {
            Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
            $stats.Processes++
            Write-Host "    -> Terminated" -ForegroundColor Green
        } catch {
            Write-Host "    -> Failed to terminate PID $($p.Id)" -ForegroundColor Red
            $stats.Failures++
        }
    } else {
        Write-Host "    -> [WOULD TERMINATE]" -ForegroundColor Gray
    }
}

foreach ($jp in $javaProcs) {
    Write-Host "  Found backend process: $($jp.Name) (PID: $($jp.ProcessId))" -ForegroundColor Yellow
    if (-not $DryRun) {
        try {
            Stop-Process -Id $jp.ProcessId -Force -ErrorAction SilentlyContinue
            $stats.Processes++
            Write-Host "    -> Terminated" -ForegroundColor Green
        } catch {
            Write-Host "    -> Failed to terminate PID $($jp.ProcessId)" -ForegroundColor Red
            $stats.Failures++
        }
    } else {
        Write-Host "    -> [WOULD TERMINATE]" -ForegroundColor Gray
    }
}

# --- B. WINDOWS SERVICES ---
Write-Host "`n[2/8] Checking for RupeeCRM Windows services..." -ForegroundColor Cyan
$services = Get-Service -ErrorAction SilentlyContinue | Where-Object { $_.Name -like "*RupeeCRM*" -or $_.DisplayName -like "*RupeeCRM*" }
foreach ($s in $services) {
    Write-Host "  Found Service: $($s.Name)" -ForegroundColor Yellow
    if (-not $DryRun) {
        try {
            Stop-Service -Name $s.Name -Force -ErrorAction SilentlyContinue
            Start-Sleep -Milliseconds 500
            sc.exe delete $s.Name | Out-Null
            $stats.Services++
            Write-Host "    -> Stopped and removed" -ForegroundColor Green
        } catch {
            Write-Host "    -> Failed to remove service: $($_.Exception.Message)" -ForegroundColor Red
            $stats.Failures++
        }
    } else {
        Write-Host "    -> [WOULD REMOVE SERVICE]" -ForegroundColor Gray
    }
}

# --- C. SCHEDULED TASKS ---
Write-Host "`n[3/8] Checking for RupeeCRM Scheduled Tasks..." -ForegroundColor Cyan
if (Get-Command Get-ScheduledTask -ErrorAction SilentlyContinue) {
    $tasks = Get-ScheduledTask -ErrorAction SilentlyContinue | Where-Object { $_.TaskName -like "*RupeeCRM*" }
    foreach ($t in $tasks) {
        Write-Host "  Found Task: $($t.TaskName)" -ForegroundColor Yellow
        if (-not $DryRun) {
            try {
                Unregister-ScheduledTask -TaskName $t.TaskName -Confirm:$false -ErrorAction Stop
                $stats.Tasks++
                Write-Host "    -> Unregistered" -ForegroundColor Green
            } catch {
                Write-Host "    -> Failed to unregister task: $($_.Exception.Message)" -ForegroundColor Red
                $stats.Failures++
            }
        } else {
            Write-Host "    -> [WOULD UNREGISTER TASK]" -ForegroundColor Gray
        }
    }
}

# --- D. MSI INSTALLER DEREGISTRATION ---
Write-Host "`n[4/8] Checking for RupeeCRM MSI registrations..." -ForegroundColor Cyan
$uninstallKeys = @(
    "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall",
    "HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall",
    "HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall"
)

foreach ($keyPath in $uninstallKeys) {
    if (Test-Path $keyPath) {
        Get-ChildItem -Path $keyPath -ErrorAction SilentlyContinue | ForEach-Object {
            $dn = $_.GetValue("DisplayName")
            $pub = $_.GetValue("Publisher")
            if (($dn -and $dn -like "*RupeeCRM*") -or ($pub -and $pub -eq "RupeeCRM")) {
                $pCode = $_.PSChildName
                Write-Host "  Found MSI Registration: $dn (ProductCode: $pCode)" -ForegroundColor Yellow
                if (-not $DryRun) {
                    if ($pCode -match "^\{[A-Fa-f0-9\-]+\}$") {
                        Write-Host "    Running msiexec /x $pCode /qn..." -ForegroundColor DarkGray
                        Start-Process "msiexec.exe" -ArgumentList "/x `"$pCode`" /qn /norestart" -Wait -NoNewWindow
                    }
                    Remove-Item -Path $_.PSPath -Recurse -Force -ErrorAction SilentlyContinue
                    $stats.MSI++
                    Write-Host "    -> MSI registration removed" -ForegroundColor Green
                } else {
                    Write-Host "    -> [WOULD UNINSTALL MSI]" -ForegroundColor Gray
                }
            }
        }
    }
}

# --- E. DIRECTORIES & CUSTOMER DATA ---
Write-Host "`n[5/8] Checking for RupeeCRM directories and customer data..." -ForegroundColor Cyan
$targetDirs = @(
    "$env:LOCALAPPDATA\Programs\RupeeCRM",
    "$env:LOCALAPPDATA\RupeeCRM",
    "$env:APPDATA\RupeeCRM",
    "$env:ProgramData\RupeeCRM",
    "$env:ProgramFiles\RupeeCRM",
    "${env:ProgramFiles(x86)}\RupeeCRM",
    "$env:USERPROFILE\.rupeecrm"
)

# Search all local user profiles for roaming/local data
if (Test-Path "$env:SystemDrive\Users") {
    Get-ChildItem -Path "$env:SystemDrive\Users" -Directory -ErrorAction SilentlyContinue | ForEach-Object {
        $targetDirs += "$($_.FullName)\AppData\Local\RupeeCRM"
        $targetDirs += "$($_.FullName)\AppData\Roaming\RupeeCRM"
        $targetDirs += "$($_.FullName)\.rupeecrm"
    }
}

$uniqueDirs = $targetDirs | Where-Object { $_ -and (Test-Path $_) } | Select-Object -Unique

foreach ($dir in $uniqueDirs) {
    Write-Host "  Found Directory: $dir" -ForegroundColor Yellow
    if (-not $DryRun) {
        $removed = $false
        for ($i = 1; $i -le 3; $i++) {
            try {
                Get-ChildItem -Path $dir -Recurse -Force -ErrorAction SilentlyContinue | ForEach-Object {
                    $_.Attributes = 'Normal'
                }
                Remove-Item -Path $dir -Recurse -Force -ErrorAction Stop
                $removed = $true
                break
            } catch {
                Start-Sleep -Milliseconds 500
            }
        }

        if ($removed -or -not (Test-Path $dir)) {
            $stats.Directories++
            Write-Host "    -> Deleted" -ForegroundColor Green
        } else {
            Write-Host "    -> Failed to delete: $dir" -ForegroundColor Red
            $stats.Failures++
        }
    } else {
        Write-Host "    -> [WOULD DELETE]" -ForegroundColor Gray
    }
}

# --- F. REGISTRY HIVE & AUTOSTART KEYS ---
Write-Host "`n[6/8] Checking for RupeeCRM registry entries..." -ForegroundColor Cyan
$rupeeKeys = @(
    "HKCU:\Software\RupeeCRM",
    "HKLM:\Software\RupeeCRM",
    "HKLM:\Software\WOW6432Node\RupeeCRM"
)

foreach ($rk in $rupeeKeys) {
    if (Test-Path $rk) {
        Write-Host "  Found Registry Key: $rk" -ForegroundColor Yellow
        if (-not $DryRun) {
            Remove-Item -Path $rk -Recurse -Force -ErrorAction SilentlyContinue
            $stats.Registry++
            Write-Host "    -> Deleted" -ForegroundColor Green
        } else {
            Write-Host "    -> [WOULD DELETE KEY]" -ForegroundColor Gray
        }
    }
}

$runKeys = @(
    "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run",
    "HKLM:\Software\Microsoft\Windows\CurrentVersion\Run"
)

foreach ($rk in $runKeys) {
    if (Test-Path $rk) {
        $val = Get-ItemPropertyValue -Path $rk -Name "RupeeCRM" -ErrorAction SilentlyContinue
        if ($val) {
            Write-Host "  Found Autostart Entry: $rk\RupeeCRM -> $val" -ForegroundColor Yellow
            if (-not $DryRun) {
                Remove-ItemProperty -Path $rk -Name "RupeeCRM" -Force -ErrorAction SilentlyContinue
                $stats.Registry++
                Write-Host "    -> Removed" -ForegroundColor Green
            } else {
                Write-Host "    -> [WOULD REMOVE AUTOSTART]" -ForegroundColor Gray
            }
        }
    }
}

# --- G. SHORTCUTS & START MENU ---
Write-Host "`n[7/8] Checking for RupeeCRM desktop & start menu shortcuts..." -ForegroundColor Cyan
$shortcutRoots = @(
    [Environment]::GetFolderPath("Desktop"),
    [Environment]::GetFolderPath("CommonDesktopDirectory"),
    [Environment]::GetFolderPath("Programs"),
    [Environment]::GetFolderPath("CommonPrograms")
)

foreach ($sr in $shortcutRoots | Select-Object -Unique) {
    if ($sr -and (Test-Path $sr)) {
        # Check files
        Get-ChildItem -Path $sr -Filter "*RupeeCRM*.lnk" -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object {
            Write-Host "  Found Shortcut: $($_.FullName)" -ForegroundColor Yellow
            if (-not $DryRun) {
                Remove-Item -Path $_.FullName -Force -ErrorAction SilentlyContinue
                $stats.Shortcuts++
                Write-Host "    -> Deleted" -ForegroundColor Green
            } else {
                Write-Host "    -> [WOULD DELETE SHORTCUT]" -ForegroundColor Gray
            }
        }

        # Check folders
        Get-ChildItem -Path $sr -Filter "RupeeCRM" -Directory -Recurse -ErrorAction SilentlyContinue | ForEach-Object {
            Write-Host "  Found Menu Folder: $($_.FullName)" -ForegroundColor Yellow
            if (-not $DryRun) {
                Remove-Item -Path $_.FullName -Recurse -Force -ErrorAction SilentlyContinue
                $stats.Shortcuts++
                Write-Host "    -> Deleted" -ForegroundColor Green
            } else {
                Write-Host "    -> [WOULD DELETE MENU FOLDER]" -ForegroundColor Gray
            }
        }
    }
}

# --- H. ENVIRONMENT VARIABLES ---
Write-Host "`n[8/8] Checking for RupeeCRM environment variables..." -ForegroundColor Cyan
$envVars = @("RUPEECRM_DATA_DIR", "RUPEECRM_BASE_DIR", "BILLSOFT_DATA_DIR")
foreach ($target in @("User", "Machine")) {
    foreach ($v in $envVars) {
        $val = [Environment]::GetEnvironmentVariable($v, $target)
        if ($val) {
            Write-Host "  Found Environment Variable: [$target] $v = $val" -ForegroundColor Yellow
            if (-not $DryRun) {
                [Environment]::SetEnvironmentVariable($v, $null, $target)
                $stats.Environment++
                Write-Host "    -> Cleared" -ForegroundColor Green
            } else {
                Write-Host "    -> [WOULD CLEAR VARIABLE]" -ForegroundColor Gray
            }
        }
    }
}

# ==============================================================================
# 4. FINAL INDEPENDENT AUDIT SCAN
# ==============================================================================

Write-Host ""
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "                     POST-PURGE FORENSIC AUDIT                        " -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host " Summary of Actions Performed:" -ForegroundColor White
Write-Host "   Processes Terminated     : $($stats.Processes)" -ForegroundColor White
Write-Host "   Services Removed         : $($stats.Services)" -ForegroundColor White
Write-Host "   Scheduled Tasks Removed  : $($stats.Tasks)" -ForegroundColor White
Write-Host "   MSI Registrations Purged : $($stats.MSI)" -ForegroundColor White
Write-Host "   Directories/Data Purged  : $($stats.Directories)" -ForegroundColor White
Write-Host "   Registry Entries Cleared : $($stats.Registry)" -ForegroundColor White
Write-Host "   Shortcuts Removed        : $($stats.Shortcuts)" -ForegroundColor White
Write-Host "   Environment Vars Cleared : $($stats.Environment)" -ForegroundColor White
Write-Host "   Failures Encountered     : $($stats.Failures)" -ForegroundColor White
Write-Host ""

if ($DryRun) {
    Write-Host "======================================================================" -ForegroundColor Cyan
    Write-Host " DRY RUN COMPLETED - ZERO MODIFICATIONS WERE MADE" -ForegroundColor Cyan
    Write-Host "======================================================================" -ForegroundColor Cyan
    Pause-And-Exit 0
}

# Check if any directories remain
$remainingDirs = $targetDirs | Where-Object { $_ -and (Test-Path $_) } | Select-Object -Unique
$remainingKeys = $rupeeKeys | Where-Object { Test-Path $_ }

if ($remainingDirs.Count -gt 0 -or $remainingKeys.Count -gt 0 -or $stats.Failures -gt 0) {
    Write-Host "======================================================================" -ForegroundColor Red
    Write-Host "                  RUPEECRM COMPLETE CLEANUP: FAILED                   " -ForegroundColor Red
    Write-Host "======================================================================" -ForegroundColor Red
    Write-Host ""
    if ($remainingDirs.Count -gt 0) {
        Write-Host "Remaining Directories:" -ForegroundColor Red
        $remainingDirs | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
    }
    if ($remainingKeys.Count -gt 0) {
        Write-Host "Remaining Registry Keys:" -ForegroundColor Red
        $remainingKeys | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
    }
    Write-Host ""
    Pause-And-Exit 1
} else {
    Write-Host "======================================================================" -ForegroundColor Green
    Write-Host "                 RUPEECRM COMPLETE CLEANUP: SUCCESS                   " -ForegroundColor Green
    Write-Host "======================================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "No RupeeCRM application, customer data, configuration, registry state," -ForegroundColor Green
    Write-Host "installer registration, startup state, updater state, or known legacy artifact remains." -ForegroundColor Green
    Write-Host ""
    Write-Host "The next RupeeCRM installation can be treated as a genuine first-time installation." -ForegroundColor Green
    Write-Host ""
    Pause-And-Exit 0
}
