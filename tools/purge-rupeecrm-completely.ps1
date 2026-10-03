<#
.SYNOPSIS
    Standalone Forensic Cleanup & Complete Factory Reset Utility for RupeeCRM.
.DESCRIPTION
    Permanently removes all RupeeCRM application binaries, databases, customer data,
    licenses, machine identities, backups, logs, registry keys, MSI installer entries,
    services, scheduled tasks, shortcuts, and legacy remnants from the system.
    After successful execution, the machine state is identical to a clean machine
    where RupeeCRM has never been installed.
.PARAMETER DryRun
    Discovers and displays all artifacts that would be removed without performing
    any destructive actions, process kills, or registry modifications.
#>

[CmdletBinding()]
param(
    [switch]$DryRun
)

$ErrorActionPreference = "Continue"

# ==============================================================================
# 1. ELEVATION CHECK & AUTO-RELAUNCH
# ==============================================================================

function Test-IsAdministrator {
    $currentPrincipal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
    return $currentPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

if (-not (Test-IsAdministrator)) {
    if ($env:RUPEECRM_PURGE_RELAUNCHED -eq "1") {
        Write-Error "CRITICAL: Failed to acquire administrative privileges after relaunch. Please run PowerShell as Administrator."
        exit 1
    }

    Write-Host "Elevated Administrator privileges required. Relaunching..." -ForegroundColor Yellow
    $scriptPath = $PSCommandPath
    if (-not $scriptPath -or -not (Test-Path $scriptPath)) {
        $scriptPath = (Resolve-Path $MyInvocation.MyCommand.Path).Path
    }

    $argList = "-NoProfile -ExecutionPolicy Bypass -File `"$scriptPath`""
    if ($DryRun) {
        $argList += " -DryRun"
    }

    $env:RUPEECRM_PURGE_RELAUNCHED = "1"
    try {
        Start-Process -FilePath "powershell.exe" -ArgumentList $argList -Verb RunAs -Wait
        exit 0
    } catch {
        Write-Error "User cancelled Elevation prompt or RunAs failed: $($_.Exception.Message)"
        exit 1
    }
}

# ==============================================================================
# 2. SAFETY WARNING & MANDATORY CONFIRMATION
# ==============================================================================

Write-Host ""
Write-Host "======================================================================" -ForegroundColor Red
Write-Host "             RUPEECRM COMPLETE FACTORY RESET (DESTRUCTIVE)            " -ForegroundColor Red
Write-Host "======================================================================" -ForegroundColor Red
Write-Host ""
Write-Host " WARNING: This utility performs a full, permanent factory reset." -ForegroundColor Yellow
Write-Host " It will IRREVOCABLY DELETE all of the following:" -ForegroundColor Yellow
Write-Host "   • RupeeCRM Application Binaries (Current & Legacy)" -ForegroundColor DarkYellow
Write-Host "   • Customer Database (billsoft_database.mv.db and related)" -ForegroundColor DarkYellow
Write-Host "   • All Customer Billing, Inventory & Firm Data" -ForegroundColor DarkYellow
Write-Host "   • All Automated & Manual Backups (%LOCALAPPDATA%\RupeeCRM\backups)" -ForegroundColor DarkYellow
Write-Host "   • Licensing & Machine ID Tokens (license.lic, mid.dat)" -ForegroundColor DarkYellow
Write-Host "   • System Configuration, Cache & Staging Files" -ForegroundColor DarkYellow
Write-Host "   • Supervisor & Backend Diagnostic Logs" -ForegroundColor DarkYellow
Write-Host "   • Windows Registry Keys & Startup Autostart Entries" -ForegroundColor DarkYellow
Write-Host "   • MSI Installer Registration State" -ForegroundColor DarkYellow
Write-Host "   • Desktop and Start Menu Shortcuts" -ForegroundColor DarkYellow
Write-Host "   • Associated Background Services & Scheduled Tasks" -ForegroundColor DarkYellow
Write-Host ""

if ($DryRun) {
    Write-Host "======================================================================" -ForegroundColor Cyan
    Write-Host " DRY RUN MODE ENABLED - NO DESTRUCTIVE CHANGES WILL BE PERFORMED" -ForegroundColor Cyan
    Write-Host "======================================================================" -ForegroundColor Cyan
    Write-Host ""
} else {
    $expectedPhrase = "DELETE EVERYTHING AND START FRESH"
    Write-Host "To confirm complete destruction of all RupeeCRM data and application state," -ForegroundColor White
    Write-Host "type EXACTLY the following phrase:" -ForegroundColor White
    Write-Host "  $expectedPhrase" -ForegroundColor Magenta
    Write-Host ""
    $userInput = Read-Host "Confirmation Prompt"

    if ($userInput -cne $expectedPhrase) {
        Write-Host ""
        Write-Host "Confirmation phrase did not match. Aborting immediately with ZERO changes." -ForegroundColor Green
        exit 0
    }
    Write-Host ""
    Write-Host "Confirmation accepted. Commencing forensic discovery and purge..." -ForegroundColor Red
}

# ==============================================================================
# 3. DISCOVERY ENGINE (ARTIFACT INVENTORY)
# ==============================================================================

class DiscoveredArtifact {
    [string]$Category
    [string]$PathOrName
    [string]$Reason
    [string]$Action
    [object]$Data

    DiscoveredArtifact([string]$cat, [string]$target, [string]$reason, [string]$action, [object]$extraData = $null) {
        $this.Category   = $cat
        $this.PathOrName = $target
        $this.Reason     = $reason
        $this.Action     = $action
        $this.Data       = $extraData
    }
}

function Find-RupeeCrmArtifacts {
    $inventory = [System.Collections.Generic.List[DiscoveredArtifact]]::new()

    # --------------------------------------------------------------------------
    # A. PROCESSES
    # --------------------------------------------------------------------------
    $processes = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue
    foreach ($p in $processes) {
        $isRupee = $false
        $reason = ""

        $name = $p.Name
        $cmd = $p.CommandLine
        $exe = $p.ExecutablePath

        if ($name -match "^RupeeCRM(\.exe)?$" -or $exe -match "RupeeCRM(\.exe)?$") {
            $isRupee = $true
            $reason = "Process name or executable matches RupeeCRM"
        } elseif ($cmd -and ($cmd -match "rupeecrm\.war" -or $cmd -match "launcher\.jar" -or $cmd -match "com\.billing\.simple" -or $cmd -match "RUPEECRM_DATA_DIR")) {
            $isRupee = $true
            $reason = "Command line contains RupeeCRM arguments: $($p.CommandLine)"
        } elseif ($exe -and ($exe -match "\\RupeeCRM\\" -or $exe -match "\\Programs\\RupeeCRM\\")) {
            $isRupee = $true
            $reason = "Process executable is located inside RupeeCRM directory: $exe"
        }

        if ($isRupee) {
            $inventory.Add([DiscoveredArtifact]::new(
                "PROCESS",
                "$($p.Name) (PID: $($p.ProcessId))",
                $reason,
                "Terminate Process",
                $p.ProcessId
            ))
        }
    }

    # --------------------------------------------------------------------------
    # B. MSI / WINDOWS INSTALLER REGISTRATIONS
    # --------------------------------------------------------------------------
    $uninstallRegRoots = @(
        "HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall",
        "HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall",
        "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall"
    )

    foreach ($regRoot in $uninstallRegRoots) {
        if (Test-Path $regRoot) {
            $subkeys = Get-ChildItem -Path $regRoot -ErrorAction SilentlyContinue
            foreach ($sk in $subkeys) {
                try {
                    $dn = $sk.GetValue("DisplayName")
                    $pub = $sk.GetValue("Publisher")
                    $loc = $sk.GetValue("InstallLocation")
                    $unStr = $sk.GetValue("UninstallString")
                    $keyName = $sk.PSChildName

                    $isMsiMatch = $false
                    $reason = ""

                    if ($dn -and $dn -like "*RupeeCRM*") {
                        $isMsiMatch = $true
                        $reason = "MSI DisplayName matches '$dn'"
                    } elseif ($pub -and $pub -eq "RupeeCRM") {
                        $isMsiMatch = $true
                        $reason = "MSI Publisher matches '$pub'"
                    } elseif ($loc -and $loc -like "*RupeeCRM*") {
                        $isMsiMatch = $true
                        $reason = "MSI InstallLocation matches '$loc'"
                    }

                    if ($isMsiMatch) {
                        $inventory.Add([DiscoveredArtifact]::new(
                            "MSI",
                            "$regRoot\$keyName ($dn)",
                            $reason,
                            "Uninstall MSI / Deregister Product",
                            @{ KeyPath = $sk.PSPath; ProductCode = $keyName; UninstallString = $unStr; InstallLocation = $loc }
                        ))
                    }
                } catch {}
            }
        }
    }

    # --------------------------------------------------------------------------
    # C. WINDOWS SERVICES
    # --------------------------------------------------------------------------
    $services = Get-CimInstance Win32_Service -ErrorAction SilentlyContinue
    foreach ($s in $services) {
        if ($s.Name -match "^RupeeCRM" -or $s.DisplayName -match "RupeeCRM" -or ($s.PathName -and $s.PathName -match "RupeeCRM")) {
            $inventory.Add([DiscoveredArtifact]::new(
                "SERVICE",
                $s.Name,
                "Service Name/Path matches RupeeCRM: $($s.PathName)",
                "Stop and Delete Service",
                $s.Name
            ))
        }
    }

    # --------------------------------------------------------------------------
    # D. SCHEDULED TASKS
    # --------------------------------------------------------------------------
    if (Get-Command Get-ScheduledTask -ErrorAction SilentlyContinue) {
        $tasks = Get-ScheduledTask -ErrorAction SilentlyContinue
        foreach ($t in $tasks) {
            if ($t.TaskName -match "RupeeCRM" -or $t.TaskPath -match "RupeeCRM") {
                $inventory.Add([DiscoveredArtifact]::new(
                    "TASK",
                    "$($t.TaskPath)$($t.TaskName)",
                    "Task Name/Path matches RupeeCRM",
                    "Unregister Scheduled Task",
                    $t.TaskName
                ))
            }
        }
    }

    # --------------------------------------------------------------------------
    # E. INSTALLATION & APPLICATION BINARY DIRECTORIES
    # --------------------------------------------------------------------------
    $potentialBinDirs = @(
        "$env:LOCALAPPDATA\Programs\RupeeCRM",
        "$env:ProgramFiles\RupeeCRM",
        "${env:ProgramFiles(x86)}\RupeeCRM"
    )

    foreach ($dir in $potentialBinDirs) {
        if ($dir -and (Test-Path $dir)) {
            $inventory.Add([DiscoveredArtifact]::new(
                "INSTALLATION",
                $dir,
                "Authoritative or legacy application binary directory",
                "Delete Directory Tree"
            ))
        }
    }

    # --------------------------------------------------------------------------
    # F. CUSTOMER DATA, DATABASES, LOGS, BACKUPS & CONFIGURATION
    # --------------------------------------------------------------------------
    $userProfiles = @()
    if (Test-Path "$env:SystemDrive\Users") {
        $userProfiles = Get-ChildItem -Path "$env:SystemDrive\Users" -Directory -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }
    }
    if (-not $userProfiles -contains $env:USERPROFILE) {
        $userProfiles += $env:USERPROFILE
    }

    $potentialDataPaths = @(
        "$env:LOCALAPPDATA\RupeeCRM",
        "$env:APPDATA\RupeeCRM",
        "$env:ProgramData\RupeeCRM",
        "$env:USERPROFILE\.rupeecrm"
    )

    foreach ($up in $userProfiles) {
        $potentialDataPaths += "$up\AppData\Local\RupeeCRM"
        $potentialDataPaths += "$up\AppData\Roaming\RupeeCRM"
        $potentialDataPaths += "$up\.rupeecrm"
    }

    $uniqueDataPaths = $potentialDataPaths | Where-Object { $_ -and (Test-Path $_) } | Select-Object -Unique
    foreach ($dp in $uniqueDataPaths) {
        $inventory.Add([DiscoveredArtifact]::new(
            "DATA",
            $dp,
            "Authoritative customer data, database, backups, or logs directory",
            "Delete Data Directory Tree"
        ))
    }

    # --------------------------------------------------------------------------
    # G. REGISTRY KEYS & AUTOSTART VALUES
    # --------------------------------------------------------------------------
    $rupeeRegKeys = @(
        "HKCU:\Software\RupeeCRM",
        "HKLM:\Software\RupeeCRM",
        "HKLM:\Software\WOW6432Node\RupeeCRM"
    )
    foreach ($rk in $rupeeRegKeys) {
        if (Test-Path $rk) {
            $inventory.Add([DiscoveredArtifact]::new(
                "REGISTRY",
                $rk,
                "RupeeCRM configuration registry hive",
                "Delete Registry Key"
            ))
        }
    }

    $runKeys = @(
        "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run",
        "HKLM:\Software\Microsoft\Windows\CurrentVersion\Run",
        "HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Run"
    )
    foreach ($runKey in $runKeys) {
        if (Test-Path $runKey) {
            $prop = Get-ItemProperty -Path $runKey -ErrorAction SilentlyContinue
            if ($prop -and $prop.RupeeCRM) {
                $inventory.Add([DiscoveredArtifact]::new(
                    "STARTUP",
                    "$runKey\RupeeCRM -> $($prop.RupeeCRM)",
                    "RupeeCRM autostart registry entry",
                    "Remove Registry Value",
                    @{ KeyPath = $runKey; ValueName = "RupeeCRM" }
                ))
            }
        }
    }

    # --------------------------------------------------------------------------
    # H. SHORTCUTS & STARTUP FOLDER ENTRIES
    # --------------------------------------------------------------------------
    $shortcutDirs = @(
        [Environment]::GetFolderPath("Desktop"),
        [Environment]::GetFolderPath("CommonDesktopDirectory"),
        [Environment]::GetFolderPath("Programs"),
        [Environment]::GetFolderPath("CommonPrograms"),
        [Environment]::GetFolderPath("Startup"),
        [Environment]::GetFolderPath("CommonStartup")
    )

    foreach ($scDir in $shortcutDirs | Select-Object -Unique) {
        if ($scDir -and (Test-Path $scDir)) {
            $links = Get-ChildItem -Path $scDir -Filter "*.lnk" -Recurse -File -ErrorAction SilentlyContinue
            foreach ($lnk in $links) {
                $isMatch = $false
                $reason = ""
                if ($lnk.Name -like "*RupeeCRM*") {
                    $isMatch = $true
                    $reason = "Shortcut filename matches RupeeCRM"
                } else {
                    try {
                        $wsh = New-Object -ComObject WScript.Shell
                        $target = $wsh.CreateShortcut($lnk.FullName).TargetPath
                        if ($target -and $target -like "*RupeeCRM*") {
                            $isMatch = $true
                            $reason = "Shortcut target points to RupeeCRM: $target"
                        }
                    } catch {}
                }

                if ($isMatch) {
                    $inventory.Add([DiscoveredArtifact]::new(
                        "SHORTCUT",
                        $lnk.FullName,
                        $reason,
                        "Delete Shortcut File"
                    ))
                }
            }

            # Check for Start Menu folders named RupeeCRM
            $startMenuFolders = Get-ChildItem -Path $scDir -Filter "RupeeCRM" -Directory -Recurse -ErrorAction SilentlyContinue
            foreach ($smf in $startMenuFolders) {
                $inventory.Add([DiscoveredArtifact]::new(
                    "SHORTCUT",
                    $smf.FullName,
                    "Start Menu application group folder",
                    "Delete Directory Tree"
                ))
            }
        }
    }

    # --------------------------------------------------------------------------
    # I. ENVIRONMENT VARIABLES
    # --------------------------------------------------------------------------
    $envTargets = @("User", "Machine")
    $rupeeEnvNames = @("RUPEECRM_DATA_DIR", "RUPEECRM_BASE_DIR", "BILLSOFT_DATA_DIR")

    foreach ($target in $envTargets) {
        foreach ($varName in $rupeeEnvNames) {
            $val = [Environment]::GetEnvironmentVariable($varName, $target)
            if ($val) {
                $inventory.Add([DiscoveredArtifact]::new(
                    "ENVIRONMENT",
                    "[$target] $varName = $val",
                    "RupeeCRM custom environment variable",
                    "Delete Environment Variable",
                    @{ Target = $target; Name = $varName }
                ))
            }
        }
    }

    # --------------------------------------------------------------------------
    # J. TEMPORARY & STAGING ARTIFACTS
    # --------------------------------------------------------------------------
    $tempDirs = @($env:TEMP, $env:TMP) | Select-Object -Unique
    foreach ($td in $tempDirs) {
        if ($td -and (Test-Path $td)) {
            $tempMatches = Get-ChildItem -Path $td -ErrorAction SilentlyContinue | Where-Object {
                $_.Name -like "*rupeecrm*" -or $_.Name -like "*billsoft*"
            }
            foreach ($tm in $tempMatches) {
                $inventory.Add([DiscoveredArtifact]::new(
                    "TEMP",
                    $tm.FullName,
                    "Temporary installation / update artifact",
                    "Delete File/Directory"
                ))
            }
        }
    }

    return $inventory
}

# ==============================================================================
# 4. EXECUTION OR DRY RUN REPORTING
# ==============================================================================

$stats = @{
    ProcessesRemoved      = 0
    InstallationsRemoved  = 0
    DataLocationsRemoved  = 0
    RegistryRemoved       = 0
    MsiRemoved            = 0
    ServicesRemoved       = 0
    TasksRemoved          = 0
    ShortcutsRemoved      = 0
    StartupRemoved        = 0
    EnvironmentRemoved    = 0
    TempRemoved           = 0
    DeletionFailures      = 0
}

function Remove-DirectoryWithRetry {
    param(
        [string]$Path,
        [int]$MaxRetries = 5,
        [int]$DelaySeconds = 1
    )

    if (-not (Test-Path $Path)) { return $true }

    for ($i = 1; $i -le $MaxRetries; $i++) {
        try {
            # Clear read-only attributes
            Get-ChildItem -Path $Path -Recurse -Force -ErrorAction SilentlyContinue | ForEach-Object {
                if ($_.Attributes -band [System.IO.FileAttributes]::ReadOnly) {
                    $_.Attributes = $_.Attributes -band (-bnot [System.IO.FileAttributes]::ReadOnly)
                }
            }
            Remove-Item -Path $Path -Recurse -Force -ErrorAction Stop
            if (-not (Test-Path $Path)) {
                return $true
            }
        } catch {
            Write-Host " [Retry $i/$MaxRetries] Waiting for locks to release on $Path..." -ForegroundColor DarkGray
            Start-Sleep -Seconds $DelaySeconds
        }
    }

    # Final attempt check
    return (-not (Test-Path $Path))
}

Write-Host "==> Phase 1: Conducting Forensic Discovery..." -ForegroundColor Cyan
$discovered = Find-RupeeCrmArtifacts

Write-Host "Found $($discovered.Count) RupeeCRM artifacts across all system subsystems." -ForegroundColor Cyan
Write-Host ""

if ($discovered.Count -eq 0) {
    Write-Host "[NOT FOUND] No active or legacy RupeeCRM artifacts detected on this system." -ForegroundColor Green
}

if ($DryRun) {
    foreach ($item in $discovered) {
        Write-Host "[FOUND] [$($item.Category)] $($item.PathOrName)" -ForegroundColor Yellow
        Write-Host "        Reason: $($item.Reason)" -ForegroundColor DarkGray
        Write-Host "        Action: [WOULD REMOVE] $($item.Action)" -ForegroundColor Gray
    }
} else {
    # --------------------------------------------------------------------------
    # 1. PROCESS TERMINATION
    # --------------------------------------------------------------------------
    $processItems = $discovered | Where-Object { $_.Category -eq "PROCESS" }
    foreach ($item in $processItems) {
        $pidToKill = $item.Data
        Write-Host "==> Terminating Process: $($item.PathOrName)..." -ForegroundColor White
        try {
            $p = Get-Process -Id $pidToKill -ErrorAction SilentlyContinue
            if ($p) {
                # Attempt graceful close first
                $p.CloseMainWindow() | Out-Null
                Start-Sleep -Milliseconds 500
                if (-not $p.HasExited) {
                    $p.Kill()
                    $p.WaitForExit(3000) | Out-Null
                }
            }
            $stats.ProcessesRemoved++
            Write-Host "    ✓ Process terminated successfully" -ForegroundColor Green
        } catch {
            Write-Host "    ✗ Failed to terminate process (PID: $pidToKill): $($_.Exception.Message)" -ForegroundColor Red
            $stats.DeletionFailures++
        }
    }
    Start-Sleep -Seconds 1

    # --------------------------------------------------------------------------
    # 2. SERVICES CLEANUP
    # --------------------------------------------------------------------------
    $serviceItems = $discovered | Where-Object { $_.Category -eq "SERVICE" }
    foreach ($item in $serviceItems) {
        $sName = $item.Data
        Write-Host "==> Removing Service: $sName..." -ForegroundColor White
        try {
            Stop-Service -Name $sName -Force -ErrorAction SilentlyContinue
            Start-Sleep -Seconds 1
            sc.exe delete $sName | Out-Null
            $stats.ServicesRemoved++
            Write-Host "    ✓ Service stopped and unregistered" -ForegroundColor Green
        } catch {
            Write-Host "    ✗ Service removal failed: $($_.Exception.Message)" -ForegroundColor Red
            $stats.DeletionFailures++
        }
    }

    # --------------------------------------------------------------------------
    # 3. SCHEDULED TASKS CLEANUP
    # --------------------------------------------------------------------------
    $taskItems = $discovered | Where-Object { $_.Category -eq "TASK" }
    foreach ($item in $taskItems) {
        $tName = $item.Data
        Write-Host "==> Removing Scheduled Task: $tName..." -ForegroundColor White
        try {
            Unregister-ScheduledTask -TaskName $tName -Confirm:$false -ErrorAction Stop
            $stats.TasksRemoved++
            Write-Host "    ✓ Scheduled task unregistered" -ForegroundColor Green
        } catch {
            Write-Host "    ✗ Scheduled task removal failed: $($_.Exception.Message)" -ForegroundColor Red
            $stats.DeletionFailures++
        }
    }

    # --------------------------------------------------------------------------
    # 4. MSI PRODUCT UNINSTALLATION
    # --------------------------------------------------------------------------
    $msiItems = $discovered | Where-Object { $_.Category -eq "MSI" }
    foreach ($item in $msiItems) {
        $msiData = $item.Data
        $pCode = $msiData.ProductCode
        Write-Host "==> Uninstalling MSI Product: $($item.PathOrName)..." -ForegroundColor White
        
        $uninstalledCleanly = $false
        if ($pCode -match "^\{[A-Fa-f0-9\-]+\}$") {
            $msiArgs = "/x `"$pCode`" /qn /norestart"
            $proc = Start-Process -FilePath "msiexec.exe" -ArgumentList $msiArgs -Wait -PassThru -NoNewWindow
            if ($proc.ExitCode -eq 0 -or $proc.ExitCode -eq 3010 -or $proc.ExitCode -eq 1605) {
                $uninstalledCleanly = $true
            }
        }

        # Remove stale registry key if still present
        if (Test-Path $msiData.KeyPath) {
            Remove-Item -Path $msiData.KeyPath -Recurse -Force -ErrorAction SilentlyContinue
        }

        $stats.MsiRemoved++
        Write-Host "    ✓ MSI registration removed (ExitCode: $($proc.ExitCode))" -ForegroundColor Green
    }

    # --------------------------------------------------------------------------
    # 5. INSTALLATION & APPLICATION BINARY DELETION
    # --------------------------------------------------------------------------
    $installItems = $discovered | Where-Object { $_.Category -eq "INSTALLATION" }
    foreach ($item in $installItems) {
        Write-Host "==> Deleting Application Binaries: $($item.PathOrName)..." -ForegroundColor White
        if (Remove-DirectoryWithRetry -Path $item.PathOrName) {
            $stats.InstallationsRemoved++
            Write-Host "    ✓ Application binaries removed" -ForegroundColor Green
        } else {
            Write-Host "    ✗ Failed to completely delete directory: $($item.PathOrName)" -ForegroundColor Red
            $stats.DeletionFailures++
        }
    }

    # --------------------------------------------------------------------------
    # 6. CUSTOMER DATA, DATABASE, LICENSES & BACKUPS DELETION
    # --------------------------------------------------------------------------
    $dataItems = $discovered | Where-Object { $_.Category -eq "DATA" }
    foreach ($item in $dataItems) {
        Write-Host "==> Deleting Customer Data & Database Root: $($item.PathOrName)..." -ForegroundColor White
        if (Remove-DirectoryWithRetry -Path $item.PathOrName) {
            $stats.DataLocationsRemoved++
            Write-Host "    ✓ Customer data root and all databases removed" -ForegroundColor Green
        } else {
            Write-Host "    ✗ Failed to completely delete data directory: $($item.PathOrName)" -ForegroundColor Red
            $stats.DeletionFailures++
        }
    }

    # --------------------------------------------------------------------------
    # 7. REGISTRY KEYS & RUN AUTOSTART CLEANUP
    # --------------------------------------------------------------------------
    $regItems = $discovered | Where-Object { $_.Category -eq "REGISTRY" }
    foreach ($item in $regItems) {
        Write-Host "==> Deleting Registry Key: $($item.PathOrName)..." -ForegroundColor White
        try {
            Remove-Item -Path $item.PathOrName -Recurse -Force -ErrorAction SilentlyContinue
            $stats.RegistryRemoved++
            Write-Host "    ✓ Registry key removed" -ForegroundColor Green
        } catch {
            Write-Host "    ✗ Failed to remove registry key: $($_.Exception.Message)" -ForegroundColor Red
            $stats.DeletionFailures++
        }
    }

    $startupItems = $discovered | Where-Object { $_.Category -eq "STARTUP" }
    foreach ($item in $startupItems) {
        $stData = $item.Data
        Write-Host "==> Removing Startup Autostart Entry: $($item.PathOrName)..." -ForegroundColor White
        try {
            Remove-ItemProperty -Path $stData.KeyPath -Name $stData.ValueName -Force -ErrorAction SilentlyContinue
            $stats.StartupRemoved++
            Write-Host "    ✓ Startup entry removed" -ForegroundColor Green
        } catch {
            Write-Host "    ✗ Failed to remove startup entry: $($_.Exception.Message)" -ForegroundColor Red
            $stats.DeletionFailures++
        }
    }

    # --------------------------------------------------------------------------
    # 8. SHORTCUTS & START MENU GROUPS
    # --------------------------------------------------------------------------
    $shortcutItems = $discovered | Where-Object { $_.Category -eq "SHORTCUT" }
    foreach ($item in $shortcutItems) {
        Write-Host "==> Removing Shortcut / Menu Folder: $($item.PathOrName)..." -ForegroundColor White
        try {
            if (Test-Path $item.PathOrName) {
                Remove-Item -Path $item.PathOrName -Recurse -Force -ErrorAction Stop
            }
            $stats.ShortcutsRemoved++
            Write-Host "    ✓ Shortcut removed" -ForegroundColor Green
        } catch {
            Write-Host "    ✗ Failed to remove shortcut: $($_.Exception.Message)" -ForegroundColor Red
            $stats.DeletionFailures++
        }
    }

    # --------------------------------------------------------------------------
    # 9. ENVIRONMENT VARIABLES
    # --------------------------------------------------------------------------
    $envItems = $discovered | Where-Object { $_.Category -eq "ENVIRONMENT" }
    foreach ($item in $envItems) {
        $envData = $item.Data
        Write-Host "==> Removing Environment Variable: $($envData.Name) from $($envData.Target)..." -ForegroundColor White
        try {
            [Environment]::SetEnvironmentVariable($envData.Name, $null, $envData.Target)
            $stats.EnvironmentRemoved++
            Write-Host "    ✓ Environment variable removed" -ForegroundColor Green
        } catch {
            Write-Host "    ✗ Failed to remove environment variable: $($_.Exception.Message)" -ForegroundColor Red
            $stats.DeletionFailures++
        }
    }

    # --------------------------------------------------------------------------
    # 10. TEMPORARY FILES
    # --------------------------------------------------------------------------
    $tempItems = $discovered | Where-Object { $_.Category -eq "TEMP" }
    foreach ($item in $tempItems) {
        Write-Host "==> Deleting Temporary File: $($item.PathOrName)..." -ForegroundColor White
        try {
            if (Test-Path $item.PathOrName) {
                Remove-Item -Path $item.PathOrName -Recurse -Force -ErrorAction SilentlyContinue
            }
            $stats.TempRemoved++
            Write-Host "    ✓ Temporary file removed" -ForegroundColor Green
        } catch {
            Write-Host "    ✗ Failed to remove temporary file: $($_.Exception.Message)" -ForegroundColor Red
            $stats.DeletionFailures++
        }
    }
}

# ==============================================================================
# 5. FINAL INDEPENDENT FORENSIC SCAN & VERIFICATION
# ==============================================================================

Write-Host ""
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "       PHASE 2: INDEPENDENT POST-PURGE FORENSIC AUDIT SCAN           " -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host ""

$postAuditArtifacts = Find-RupeeCrmArtifacts

Write-Host "----------------- PURGE EXECUTION SUMMARY -----------------" -ForegroundColor White
Write-Host " Processes Terminated      : $($stats.ProcessesRemoved)" -ForegroundColor White
Write-Host " Binary Installations Purged : $($stats.InstallationsRemoved)" -ForegroundColor White
Write-Host " Customer Data Roots Purged: $($stats.DataLocationsRemoved)" -ForegroundColor White
Write-Host " Registry Keys Removed     : $($stats.RegistryRemoved)" -ForegroundColor White
Write-Host " MSI Registrations Purged  : $($stats.MsiRemoved)" -ForegroundColor White
Write-Host " Services Removed          : $($stats.ServicesRemoved)" -ForegroundColor White
Write-Host " Scheduled Tasks Removed   : $($stats.TasksRemoved)" -ForegroundColor White
Write-Host " Shortcuts Removed         : $($stats.ShortcutsRemoved)" -ForegroundColor White
Write-Host " Startup Entries Removed   : $($stats.StartupRemoved)" -ForegroundColor White
Write-Host " Environment Vars Cleared  : $($stats.EnvironmentRemoved)" -ForegroundColor White
Write-Host " Temporary Artifacts Purged: $($stats.TempRemoved)" -ForegroundColor White
Write-Host " Deletion Failures         : $($stats.DeletionFailures)" -ForegroundColor White
Write-Host "-----------------------------------------------------------" -ForegroundColor White
Write-Host ""

if ($DryRun) {
    Write-Host "======================================================================" -ForegroundColor Cyan
    Write-Host " DRY RUN COMPLETED: No modifications were made to the system." -ForegroundColor Cyan
    Write-Host "======================================================================" -ForegroundColor Cyan
    exit 0
}

if ($postAuditArtifacts.Count -gt 0 -or $stats.DeletionFailures -gt 0) {
    Write-Host "======================================================================" -ForegroundColor Red
    Write-Host "                  RUPEECRM COMPLETE CLEANUP: FAILED                   " -ForegroundColor Red
    Write-Host "======================================================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "The following $($postAuditArtifacts.Count) artifact(s) still remain on the system:" -ForegroundColor Red
    foreach ($rem in $postAuditArtifacts) {
        Write-Host "  • [$($rem.Category)] $($rem.PathOrName)" -ForegroundColor Red
        Write-Host "    Reason: $($rem.Reason)" -ForegroundColor DarkRed
    }
    Write-Host ""
    Write-Host "Failure: System is not in a pure pristine state. Please resolve file locks or permissions and re-run." -ForegroundColor Red
    exit 1
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
    exit 0
}
