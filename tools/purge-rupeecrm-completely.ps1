# ==============================================================================
# RupeeCRM Complete Forensic Purge & Factory Reset Utility
# ==============================================================================
#
# PURPOSE:
# Administrator-level, DESTRUCTIVE "factory reset" and forensic cleanup tool.
# Completely wipes all RupeeCRM binaries, customer databases, configuration,
# licenses, backups, logs, updater state, registry state, MSI registrations,
# services, scheduled tasks, and legacy installation remnants.
#
# After successful execution, the system is in a pristine state identical to a
# machine where RupeeCRM has NEVER been installed.
#
# USAGE:
#   Normal (Interactive):  .\tools\purge-rupeecrm-completely.ps1
#   Automated / CI:       .\tools\purge-rupeecrm-completely.ps1 -Force
#   Dry Run (Report only): .\tools\purge-rupeecrm-completely.ps1 -DryRun
#   Diagnostics / Scan:   .\tools\purge-rupeecrm-completely.ps1 -Diagnostics
# ==============================================================================

[CmdletBinding()]
param(
    [switch]$DryRun,
    [switch]$Diagnostics,
    [switch]$Force,
    [switch]$NoElevation
)

$ErrorActionPreference = "Stop"
$CONFIRMATION_PHRASE = "DELETE EVERYTHING AND START FRESH"

# ------------------------------------------------------------------------------
# 0. Administrator Elevation Handling
# ------------------------------------------------------------------------------
function Test-IsAdmin {
    if ($IsWindows -or $env:OS -eq "Windows_NT") {
        $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
        $principal = New-Object Security.Principal.WindowsPrincipal($identity)
        return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    }
    return $true
}

if (-not $NoElevation -and -not (Test-IsAdmin) -and -not $DryRun -and -not $Diagnostics) {
    Write-Host "Elevating privileges to Administrator..." -ForegroundColor Yellow
    $scriptPath = $MyInvocation.MyCommand.Path
    $argList = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$scriptPath`"")
    if ($Force) { $argList += "-Force" }
    
    try {
        $proc = Start-Process -FilePath "powershell.exe" -ArgumentList $argList -Verb RunAs -PassThru -Wait
        exit $proc.ExitCode
    } catch {
        Write-Error "Administrator elevation required to perform complete forensic purge. Aborting."
        exit 1
    }
}

# ------------------------------------------------------------------------------
# 1. Forensic Discovery & Identification Helpers
# ------------------------------------------------------------------------------

function Test-IsRupeeCrmPath {
    param([string]$Path)
    if ([string]::IsNullOrWhiteSpace($Path)) { return $false }
    $norm = $Path.Replace('/', '\').TrimEnd('\')
    
    # Specific known target directory patterns
    if ($norm -like "*\AppData\Local\Programs\RupeeCRM" -or
        $norm -like "*\AppData\Local\RupeeCRM" -or
        $norm -like "*\AppData\Roaming\RupeeCRM" -or
        $norm -like "*\ProgramData\RupeeCRM" -or
        $norm -like "*\Program Files\RupeeCRM" -or
        $norm -like "*\Program Files (x86)\RupeeCRM" -or
        $norm -like "*\.rupeecrm") {
        return $true
    }
    return $false
}

function Get-RupeeCrmProcesses {
    $matched = @()
    $candidates = Get-Process -ErrorAction SilentlyContinue
    foreach ($p in $candidates) {
        try {
            $path = $p.Path
            $name = $p.ProcessName
            $cmd = $null
            
            # Check via WMI/CIM for commandline arguments if available
            try {
                $wmi = Get-CimInstance Win32_Process -Filter "ProcessId = $($p.Id)" -ErrorAction SilentlyContinue
                if ($wmi) { $cmd = $wmi.CommandLine }
            } catch {}

            $isMatch = $false
            if ($name -ieq "RupeeCRM" -or $name -ieq "RupeeCRMSetup") {
                $isMatch = $true
            } elseif ($path -and (Test-IsRupeeCrmPath $path)) {
                $isMatch = $true
            } elseif ($cmd -and ($cmd -like "*rupeecrm*" -or $cmd -like "*billsoft*" -or $cmd -like "*Simple-Billing*")) {
                $isMatch = $true
            }

            if ($isMatch) {
                $matched += [PSCustomObject]@{
                    Id          = $p.Id
                    ProcessName = $p.ProcessName
                    Path        = $path
                    CommandLine = $cmd
                }
            }
        } catch {}
    }
    return $matched
}

function Get-RupeeCrmMsiProducts {
    $matched = @()
    $regPaths = @(
        "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall",
        "HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall",
        "HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall"
    )

    foreach ($rp in $regPaths) {
        if (Test-Path $rp) {
            $keys = Get-ChildItem -Path $rp -ErrorAction SilentlyContinue
            foreach ($k in $keys) {
                $dispName = $k.GetValue("DisplayName")
                $pub      = $k.GetValue("Publisher")
                $instLoc  = $k.GetValue("InstallLocation")
                $uninst   = $k.GetValue("UninstallString")

                if (($dispName -and $dispName -like "*RupeeCRM*") -or
                    ($pub -and $pub -like "*RupeeCRM*") -or
                    ($instLoc -and (Test-IsRupeeCrmPath $instLoc)) -or
                    ($uninst -and $uninst -like "*RupeeCRM*")) {
                    
                    $matched += [PSCustomObject]@{
                        ProductCode     = $k.PSChildName
                        DisplayName     = $dispName
                        InstallLocation = $instLoc
                        UninstallString = $uninst
                        RegistryPath    = $k.PSPath
                    }
                }
            }
        }
    }
    return $matched
}

function Get-RupeeCrmServices {
    $matched = @()
    $services = Get-CimInstance Win32_Service -ErrorAction SilentlyContinue
    foreach ($s in $services) {
        if ($s.Name -like "*RupeeCRM*" -or $s.DisplayName -like "*RupeeCRM*" -or
            ($s.PathName -and (Test-IsRupeeCrmPath $s.PathName))) {
            $matched += [PSCustomObject]@{
                Name        = $s.Name
                DisplayName = $s.DisplayName
                PathName    = $s.PathName
                State       = $s.State
            }
        }
    }
    return $matched
}

function Get-RupeeCrmScheduledTasks {
    $matched = @()
    try {
        $tasks = Get-ScheduledTask -ErrorAction SilentlyContinue
        foreach ($t in $tasks) {
            if ($t.TaskName -like "*RupeeCRM*" -or $t.TaskPath -like "*RupeeCRM*") {
                $matched += [PSCustomObject]@{
                    TaskName = $t.TaskName
                    TaskPath = $t.TaskPath
                    State    = $t.State
                }
            }
        }
    } catch {}
    return $matched
}

function Get-RupeeCrmRegistryEntries {
    $matched = @()
    
    # Run keys
    $runPaths = @(
        "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run",
        "HKLM:\Software\Microsoft\Windows\CurrentVersion\Run",
        "HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Run"
    )
    foreach ($rp in $runPaths) {
        if (Test-Path $rp) {
            $item = Get-ItemProperty -Path $rp -ErrorAction SilentlyContinue
            if ($item) {
                foreach ($prop in $item.PSObject.Properties) {
                    if ($prop.Name -like "*RupeeCRM*" -or ($prop.Value -is [string] -and $prop.Value -like "*RupeeCRM*")) {
                        $matched += [PSCustomObject]@{
                            Type  = "RunKey"
                            Path  = $rp
                            Name  = $prop.Name
                            Value = $prop.Value
                        }
                    }
                }
            }
        }
    }

    # Dedicated software keys
    $softwareKeys = @(
        "HKCU:\Software\RupeeCRM",
        "HKLM:\Software\RupeeCRM",
        "HKLM:\Software\WOW6432Node\RupeeCRM"
    )
    foreach ($sk in $softwareKeys) {
        if (Test-Path $sk) {
            $matched += [PSCustomObject]@{
                Type  = "SoftwareKey"
                Path  = $sk
                Name  = "(Default)"
                Value = "Entire Key Tree"
            }
        }
    }

    return $matched
}

function Get-RupeeCrmShortcuts {
    $matched = @()
    $shortcutRoots = @(
        "$env:USERPROFILE\Desktop",
        "$env:PUBLIC\Desktop",
        "$env:APPDATA\Microsoft\Windows\Start Menu\Programs",
        "$env:ProgramData\Microsoft\Windows\Start Menu\Programs",
        "$env:APPDATA\Microsoft\Windows\Start Menu\Programs\Startup",
        "$env:ProgramData\Microsoft\Windows\Start Menu\Programs\Startup"
    )

    foreach ($sr in $shortcutRoots) {
        if ($sr -and (Test-Path $sr)) {
            $links = Get-ChildItem -Path $sr -Filter "*RupeeCRM*.lnk" -Recurse -File -ErrorAction SilentlyContinue
            foreach ($lnk in $links) {
                $matched += [PSCustomObject]@{
                    Path = $lnk.FullName
                    Name = $lnk.Name
                }
            }
            # Also check RupeeCRM subfolders in Start Menu
            $menuFolders = Get-ChildItem -Path $sr -Filter "*RupeeCRM*" -Directory -Recurse -ErrorAction SilentlyContinue
            foreach ($mf in $menuFolders) {
                $matched += [PSCustomObject]@{
                    Path = $mf.FullName
                    Name = $mf.Name
                }
            }
        }
    }
    return $matched
}

function Get-RupeeCrmFilesystemArtifacts {
    $matched = @()
    
    $candidateDirectories = @(
        "$env:LOCALAPPDATA\Programs\RupeeCRM",
        "$env:LOCALAPPDATA\RupeeCRM",
        "$env:APPDATA\RupeeCRM",
        "$env:USERPROFILE\.rupeecrm",
        "$env:PROGRAMDATA\RupeeCRM",
        "$env:ProgramFiles\RupeeCRM",
        "${env:ProgramFiles(x86)}\RupeeCRM"
    )

    foreach ($dir in $candidateDirectories) {
        if ($dir -and (Test-Path $dir)) {
            $matched += [PSCustomObject]@{
                Type = "Directory"
                Path = (Resolve-Path $dir).Path
            }
        }
    }

    # Search temporary directories for RupeeCRM update & lock artifacts
    $tempRoots = @("$env:TEMP", "$env:TMP")
    foreach ($tr in $tempRoots) {
        if ($tr -and (Test-Path $tr)) {
            $tempItems = Get-ChildItem -Path $tr -Filter "*rupeecrm*" -ErrorAction SilentlyContinue
            foreach ($ti in $tempItems) {
                $matched += [PSCustomObject]@{
                    Type = if ($ti.PSIsContainer) { "TempDirectory" } else { "TempFile" }
                    Path = $ti.FullName
                }
            }
        }
    }

    return $matched
}

function Get-RupeeCrmEnvironmentVariables {
    $matched = @()
    $varNames = @("RUPEECRM_BASE_DIR", "RUPEECRM_DATA_DIR", "BILLSOFT_DATA_DIR")
    foreach ($vn in $varNames) {
        # Process scope
        if ([Environment]::GetEnvironmentVariable($vn, "Process")) {
            $matched += [PSCustomObject]@{ Scope = "Process"; Name = $vn; Value = [Environment]::GetEnvironmentVariable($vn, "Process") }
        }
        # User scope
        if ([Environment]::GetEnvironmentVariable($vn, "User")) {
            $matched += [PSCustomObject]@{ Scope = "User"; Name = $vn; Value = [Environment]::GetEnvironmentVariable($vn, "User") }
        }
        # Machine scope
        if ([Environment]::GetEnvironmentVariable($vn, "Machine")) {
            $matched += [PSCustomObject]@{ Scope = "Machine"; Name = $vn; Value = [Environment]::GetEnvironmentVariable($vn, "Machine") }
        }
    }
    return $matched
}

# ------------------------------------------------------------------------------
# 2. Comprehensive Forensic Scan Aggregator
# ------------------------------------------------------------------------------
function Get-RupeeCrmForensicReport {
    return [PSCustomObject]@{
        Processes            = Get-RupeeCrmProcesses
        MsiProducts          = Get-RupeeCrmMsiProducts
        Services             = Get-RupeeCrmServices
        ScheduledTasks       = Get-RupeeCrmScheduledTasks
        RegistryEntries      = Get-RupeeCrmRegistryEntries
        Shortcuts            = Get-RupeeCrmShortcuts
        FilesystemArtifacts  = Get-RupeeCrmFilesystemArtifacts
        EnvironmentVariables = Get-RupeeCrmEnvironmentVariables
    }
}

function Print-ForensicReport {
    param(
        [Parameter(Mandatory=$true)]$Report,
        [string]$Prefix = "[DISCOVERED]"
    )

    Write-Host ""
    Write-Host "=========================================================="
    Write-Host " RupeeCRM Forensic Inventory Scan Report"
    Write-Host "=========================================================="
    
    Write-Host "1. Running Processes:"
    if ($Report.Processes.Count -gt 0) {
        foreach ($p in $Report.Processes) {
            Write-Host "  $Prefix Process: $($p.ProcessName) (PID: $($p.Id)) -> $($p.Path)" -ForegroundColor Yellow
        }
    } else { Write-Host "  [CLEAN] No running RupeeCRM processes found." -ForegroundColor Green }

    Write-Host "2. Windows Installer / MSI Registrations:"
    if ($Report.MsiProducts.Count -gt 0) {
        foreach ($m in $Report.MsiProducts) {
            Write-Host "  $Prefix MSI Product: $($m.DisplayName) (Code: $($m.ProductCode)) -> $($m.InstallLocation)" -ForegroundColor Yellow
        }
    } else { Write-Host "  [CLEAN] No RupeeCRM MSI installations registered." -ForegroundColor Green }

    Write-Host "3. Windows Services:"
    if ($Report.Services.Count -gt 0) {
        foreach ($s in $Report.Services) {
            Write-Host "  $Prefix Service: $($s.Name) ($($s.DisplayName)) -> $($s.PathName)" -ForegroundColor Yellow
        }
    } else { Write-Host "  [CLEAN] No RupeeCRM Windows services found." -ForegroundColor Green }

    Write-Host "4. Scheduled Tasks:"
    if ($Report.ScheduledTasks.Count -gt 0) {
        foreach ($t in $Report.ScheduledTasks) {
            Write-Host "  $Prefix Task: $($t.TaskPath)\$($t.TaskName)" -ForegroundColor Yellow
        }
    } else { Write-Host "  [CLEAN] No RupeeCRM scheduled tasks found." -ForegroundColor Green }

    Write-Host "5. Startup & Software Registry State:"
    if ($Report.RegistryEntries.Count -gt 0) {
        foreach ($r in $Report.RegistryEntries) {
            Write-Host "  $Prefix Registry [$($r.Type)]: $($r.Path)\$($r.Name) = $($r.Value)" -ForegroundColor Yellow
        }
    } else { Write-Host "  [CLEAN] No RupeeCRM registry entries found." -ForegroundColor Green }

    Write-Host "6. Desktop & Start Menu Shortcuts:"
    if ($Report.Shortcuts.Count -gt 0) {
        foreach ($sc in $Report.Shortcuts) {
            Write-Host "  $Prefix Shortcut: $($sc.Path)" -ForegroundColor Yellow
        }
    } else { Write-Host "  [CLEAN] No RupeeCRM shortcuts found." -ForegroundColor Green }

    Write-Host "7. Filesystem Directories & Data Trees:"
    if ($Report.FilesystemArtifacts.Count -gt 0) {
        foreach ($fs in $Report.FilesystemArtifacts) {
            Write-Host "  $Prefix [$($fs.Type)]: $($fs.Path)" -ForegroundColor Yellow
        }
    } else { Write-Host "  [CLEAN] No RupeeCRM directories or temp files found." -ForegroundColor Green }

    Write-Host "8. Environment Variables:"
    if ($Report.EnvironmentVariables.Count -gt 0) {
        foreach ($ev in $Report.EnvironmentVariables) {
            Write-Host "  $Prefix EnvVar ($($ev.Scope)): $($ev.Name) = $($ev.Value)" -ForegroundColor Yellow
        }
    } else { Write-Host "  [CLEAN] No RupeeCRM environment variables found." -ForegroundColor Green }

    Write-Host "=========================================================="
}

# ------------------------------------------------------------------------------
# 3. Robust Deletion Helper with Lock Retries
# ------------------------------------------------------------------------------
function Remove-ItemWithRetry {
    param(
        [Parameter(Mandatory=$true)][string]$Path,
        [int]$MaxRetries = 5,
        [int]$DelaySeconds = 1
    )

    if (-not (Test-Path $Path)) { return }

    for ($i = 1; $i -le $MaxRetries; $i++) {
        try {
            if (Test-Path $Path -PathType Container) {
                Remove-Item -Path $Path -Recurse -Force -ErrorAction Stop
            } else {
                Remove-Item -Path $Path -Force -ErrorAction Stop
            }
            if (-not (Test-Path $Path)) {
                Write-Host "  [DELETED] $Path" -ForegroundColor Green
                return
            }
        } catch {
            if ($i -eq $MaxRetries) {
                Write-Host "  [WARNING] Could not remove '$Path' on attempt $i/$MaxRetries: $($_.Exception.Message)" -ForegroundColor Red
            } else {
                Write-Host "  [LOCKED] File or folder '$Path' is busy. Retrying in ${DelaySeconds}s ($i/$MaxRetries)..." -ForegroundColor DarkYellow
                Start-Sleep -Seconds $DelaySeconds
            }
        }
    }
}

# ------------------------------------------------------------------------------
# 4. Mode Routing: Diagnostics / ScanOnly Mode
# ------------------------------------------------------------------------------
if ($Diagnostics) {
    Write-Host "Executing RupeeCRM Forensic Diagnostics Scan..." -ForegroundColor Cyan
    $scan = Get-RupeeCrmForensicReport
    Print-ForensicReport -Report $scan -Prefix "[FOUND]"
    
    $totalArtifacts = $scan.Processes.Count + $scan.MsiProducts.Count + $scan.Services.Count +
                      $scan.ScheduledTasks.Count + $scan.RegistryEntries.Count + $scan.Shortcuts.Count +
                      $scan.FilesystemArtifacts.Count + $scan.EnvironmentVariables.Count

    if ($totalArtifacts -eq 0) {
        Write-Host ""
        Write-Host "=========================================================="
        Write-Host " RUPEECRM CLEAN STATE: PASS" -ForegroundColor Green
        Write-Host " No RupeeCRM application, data, configuration, installer registration,"
        Write-Host " startup state, updater state, or legacy artifact was detected."
        Write-Host "=========================================================="
        exit 0
    } else {
        Write-Host ""
        Write-Host "=========================================================="
        Write-Host " RUPEECRM CLEAN STATE: FAIL" -ForegroundColor Red
        Write-Host " Total detected remnants: $totalArtifacts"
        Write-Host " Machine is NOT in a factory clean state."
        Write-Host "=========================================================="
        exit 1
    }
}

# ------------------------------------------------------------------------------
# 5. Mode Routing: DryRun Mode
# ------------------------------------------------------------------------------
if ($DryRun) {
    Write-Host "Executing RupeeCRM Purge in DRY-RUN Mode (No changes will be made)..." -ForegroundColor Cyan
    $scan = Get-RupeeCrmForensicReport
    Print-ForensicReport -Report $scan -Prefix "[WOULD REMOVE]"
    Write-Host "`n[DRY RUN COMPLETE] Zero modifications made to system." -ForegroundColor Cyan
    exit 0
}

# ------------------------------------------------------------------------------
# 6. Destructive Execution: Warning & Explicit Confirmation
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "==========================================================================" -ForegroundColor Red
Write-Host "                       RupeeCRM Complete Removal                          " -ForegroundColor Red
Write-Host "==========================================================================" -ForegroundColor Red
Write-Host " WARNING: This is a DESTRUCTIVE Administrator-level factory reset." -ForegroundColor Red
Write-Host " This will PERMANENTLY DELETE:" -ForegroundColor Red
Write-Host "  - All RupeeCRM Application Binaries (%LOCALAPPDATA%\Programs\RupeeCRM)"
Write-Host "  - All Customer Databases (%LOCALAPPDATA%\RupeeCRM\data\billsoft_database.mv.db)"
Write-Host "  - All Automated Snapshots & Backups (%LOCALAPPDATA%\RupeeCRM\backups)"
Write-Host "  - All Machine Identity, Licenses, & Settings (mid.dat, license.lic, etc.)"
Write-Host "  - All Supervisor & Server Logs (%LOCALAPPDATA%\RupeeCRM\logs)"
Write-Host "  - All Staged Updater Files (%LOCALAPPDATA%\RupeeCRM\staging)"
Write-Host "  - All Windows Installer MSI registrations, shortcuts, services, & tasks"
Write-Host "  - All Legacy Installation remnants across the entire system"
Write-Host "==========================================================================" -ForegroundColor Red
Write-Host ""

if (-not $Force) {
    Write-Host "To proceed, type the confirmation phrase exactly as shown below:" -ForegroundColor Yellow
    Write-Host "  $CONFIRMATION_PHRASE" -ForegroundColor Cyan
    Write-Host ""
    $userInput = Read-Host "Enter confirmation phrase"
    if ($userInput -cne $CONFIRMATION_PHRASE) {
        Write-Host "`nConfirmation phrase did not match. Aborting purge. No changes made." -ForegroundColor Yellow
        exit 1
    }
}

Write-Host "`nInitiating full forensic wipe of RupeeCRM..." -ForegroundColor Magenta

# ------------------------------------------------------------------------------
# Step 1: Terminate All RupeeCRM Processes
# ------------------------------------------------------------------------------
Write-Host "`n==> Step 1: Terminating all active RupeeCRM processes..."
$activeProcs = Get-RupeeCrmProcesses
foreach ($p in $activeProcs) {
    Write-Host "  Gracefully requesting termination of process $($p.ProcessName) (PID: $($p.Id))..."
    try {
        Stop-Process -Id $p.Id -ErrorAction SilentlyContinue
    } catch {}
}
Start-Sleep -Seconds 2

# Force terminate any stubborn processes
$activeProcs = Get-RupeeCrmProcesses
foreach ($p in $activeProcs) {
    Write-Host "  Force killing process $($p.ProcessName) (PID: $($p.Id))..."
    try {
        Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
    } catch {}
}
Start-Sleep -Seconds 1

# ------------------------------------------------------------------------------
# Step 2: Uninstall Registered MSI Packages
# ------------------------------------------------------------------------------
Write-Host "`n==> Step 2: Uninstalling registered RupeeCRM MSI packages..."
$msiProducts = Get-RupeeCrmMsiProducts
foreach ($m in $msiProducts) {
    Write-Host "  Invoking MSI uninstaller for ProductCode: $($m.ProductCode) ($($m.DisplayName))..."
    $proc = Start-Process -FilePath "msiexec.exe" -ArgumentList "/x $($m.ProductCode) /qn /norestart" -Wait -PassThru -NoNewWindow
    Write-Host "  MSI uninstaller exit code: $($proc.ExitCode)"
}

# ------------------------------------------------------------------------------
# Step 3: Remove Windows Services
# ------------------------------------------------------------------------------
Write-Host "`n==> Step 3: Removing RupeeCRM Windows Services..."
$services = Get-RupeeCrmServices
foreach ($s in $services) {
    Write-Host "  Stopping and removing service: $($s.Name)..."
    try {
        Stop-Service -Name $s.Name -Force -ErrorAction SilentlyContinue
        & sc.exe delete "$($s.Name)" | Out-Null
    } catch {}
}

# ------------------------------------------------------------------------------
# Step 4: Remove Scheduled Tasks
# ------------------------------------------------------------------------------
Write-Host "`n==> Step 4: Removing RupeeCRM Scheduled Tasks..."
$tasks = Get-RupeeCrmScheduledTasks
foreach ($t in $tasks) {
    Write-Host "  Unregistering task: $($t.TaskPath)\$($t.TaskName)..."
    try {
        Unregister-ScheduledTask -TaskName $t.TaskName -TaskPath $t.TaskPath -Confirm:$false -ErrorAction SilentlyContinue
    } catch {}
}

# ------------------------------------------------------------------------------
# Step 5: Clean Registry Keys & Startup Entries
# ------------------------------------------------------------------------------
Write-Host "`n==> Step 5: Removing RupeeCRM Registry state & startup entries..."
$regEntries = Get-RupeeCrmRegistryEntries
foreach ($r in $regEntries) {
    if ($r.Type -eq "RunKey") {
        Write-Host "  Removing Run entry '$($r.Name)' from $($r.Path)..."
        Remove-ItemProperty -Path $r.Path -Name $r.Name -Force -ErrorAction SilentlyContinue
    } elseif ($r.Type -eq "SoftwareKey") {
        Write-Host "  Deleting software key tree: $($r.Path)..."
        Remove-Item -Path $r.Path -Recurse -Force -ErrorAction SilentlyContinue
    }
}

# Also remove any leftover uninstall registry keys for RupeeCRM
$msiProducts = Get-RupeeCrmMsiProducts
foreach ($m in $msiProducts) {
    if ($m.RegistryPath -and (Test-Path $m.RegistryPath)) {
        Write-Host "  Cleaning leftover uninstall registry key: $($m.RegistryPath)..."
        Remove-Item -Path $m.RegistryPath -Recurse -Force -ErrorAction SilentlyContinue
    }
}

# ------------------------------------------------------------------------------
# Step 6: Remove Desktop & Start Menu Shortcuts
# ------------------------------------------------------------------------------
Write-Host "`n==> Step 6: Removing Desktop and Start Menu shortcuts..."
$shortcuts = Get-RupeeCrmShortcuts
foreach ($sc in $shortcuts) {
    Remove-ItemWithRetry -Path $sc.Path
}

# ------------------------------------------------------------------------------
# Step 7: Delete All Application Binaries, Customer Data, & Legacy Trees
# ------------------------------------------------------------------------------
Write-Host "`n==> Step 7: Permanently wiping all RupeeCRM files, databases, and trees..."
$fsArtifacts = Get-RupeeCrmFilesystemArtifacts
foreach ($fs in $fsArtifacts) {
    Remove-ItemWithRetry -Path $fs.Path
}

# ------------------------------------------------------------------------------
# Step 8: Remove Environment Variables
# ------------------------------------------------------------------------------
Write-Host "`n==> Step 8: Cleaning RupeeCRM Environment Variables..."
$envVars = Get-RupeeCrmEnvironmentVariables
foreach ($ev in $envVars) {
    Write-Host "  Removing environment variable '$($ev.Name)' from scope '$($ev.Scope)'..."
    [Environment]::SetEnvironmentVariable($ev.Name, $null, $ev.Scope)
}

# ------------------------------------------------------------------------------
# 7. Final Independent Forensic Scan & Quality Gate
# ------------------------------------------------------------------------------
Write-Host "`n==> Step 9: Performing final independent forensic scan..."
$finalScan = Get-RupeeCrmForensicReport

$remainingCount = $finalScan.Processes.Count + $finalScan.MsiProducts.Count + $finalScan.Services.Count +
                  $finalScan.ScheduledTasks.Count + $finalScan.RegistryEntries.Count + $finalScan.Shortcuts.Count +
                  $finalScan.FilesystemArtifacts.Count + $finalScan.EnvironmentVariables.Count

if ($remainingCount -gt 0) {
    Write-Host ""
    Write-Host "=========================================================="
    Write-Host " CRITICAL WARNING: FORENSIC PURGE INCOMPLETE!" -ForegroundColor Red
    Write-Host " The following remnants could not be removed:" -ForegroundColor Red
    Write-Host "=========================================================="
    Print-ForensicReport -Report $finalScan -Prefix "[FAILED TO REMOVE]"
    Write-Host ""
    throw "Forensic purge failed: $remainingCount RupeeCRM artifacts remain on the system."
} else {
    Write-Host ""
    Write-Host "=========================================================="
    Write-Host " RUPEECRM CLEAN STATE: PASS" -ForegroundColor Green
    Write-Host "=========================================================="
    Write-Host " No RupeeCRM application binaries, customer databases, configuration,"
    Write-Host " licenses, backups, logs, updater state, registry state, shortcuts,"
    Write-Host " services, tasks, or legacy remnants remain."
    Write-Host " The system is in a pristine state for fresh installation."
    Write-Host "=========================================================="
}
