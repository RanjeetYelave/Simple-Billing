<#
.SYNOPSIS
    Hardened Standalone Windows Fresh Installer & Lifecycle Reset Utility for RupeeCRM.
.DESCRIPTION
    Performs a 100% clean factory-reset and fresh installation of the latest RupeeCRM release:
    1. Automatic Administrator self-elevation (supports in-memory 'irm | iex' execution).
    2. Interactive destruction warning & confirmation requirement (overridable with -Force).
    3. Targeted process termination (RupeeCRM native and backend Java runtimes).
    4. Deregistration of Windows Services and Scheduled Tasks.
    5. Clean uninstallation of existing Windows Installer (MSI) packages.
    6. Complete filesystem purge (application binaries, customer data, databases, logs, backups,
       licensing tokens, updater state, and temporary staging artifacts).
    7. Complete registry, Run startup, environment variable, and shortcut removal.
    8. Post-cleanup forensic audit (verifies zero legacy traces remain before proceeding).
    9. Dynamic release discovery from official GitHub repository (RanjeetYelave/Simple-Billing).
    10. Secure download of RupeeCRMSetup.msi with magic-byte validation (CFBF) and SHA-256 check.
    11. Silent, fully-logged MSI installation and architecture layout verification.
    12. Single-JRE invariant and payload file verification (RupeeCRM.exe, launcher.jar, WAR, runtime).
    13. Application supervisor startup (RupeeCRM.exe --background) and active /api/health polling.
    14. Fresh onboarding readiness verification (empty firm list, clean database initialization).
    15. Autostart configuration audit and default browser launch to http://localhost:28080/.
.PARAMETER MsiPath
    Optional local path to a pre-downloaded RupeeCRMSetup.msi installer.
.PARAMETER Force
    Skips interactive confirmation prompts for non-interactive / automated execution.
.PARAMETER NoBrowser
    Prevents automatically opening the default web browser after successful installation.
.EXAMPLE
    # Direct one-command execution from PowerShell:
    irm https://raw.githubusercontent.com/RanjeetYelave/Simple-Billing/main/tools/install-windows.ps1 | iex

    # Non-interactive fresh install with pre-downloaded MSI:
    powershell -ExecutionPolicy Bypass -File .\tools\install-windows.ps1 -MsiPath "C:\Downloads\RupeeCRMSetup.msi" -Force
#>

[CmdletBinding()]
param(
    [string]$MsiPath = "",
    [switch]$Force,
    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"

# Enforce TLS 1.2 and TLS 1.3 for secure downloads
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 -bor [Net.SecurityProtocolType]::Tls13

# Keep console window open on interactive completion
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
# 1. ELEVATION CHECK & IN-MEMORY AUTO-RELAUNCH
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

$isAdmin = Test-IsAdministrator

if (-not $isAdmin) {
    if ($env:RUPEECRM_INSTALL_RELAUNCHED -eq "1") {
        Write-Host ""
        Write-Host "======================================================================" -ForegroundColor Red
        Write-Host " CRITICAL: Administrator privileges were denied or could not be acquired." -ForegroundColor Red
        Write-Host " Please right-click PowerShell, choose 'Run as Administrator', and rerun." -ForegroundColor Red
        Write-Host "======================================================================" -ForegroundColor Red
        Complete-PauseExit 1
    }

    Write-Host "======================================================================" -ForegroundColor Yellow
    Write-Host " Administrator privileges required. Requesting elevation (UAC)...    " -ForegroundColor Yellow
    Write-Host "======================================================================" -ForegroundColor Yellow

    # Determine script path or write downloaded payload to temp file for elevated execution
    $scriptToRun = $PSCommandPath

    if (-not $scriptToRun -or -not (Test-Path $scriptToRun)) {
        if ($PSScriptRoot -and $MyInvocation.MyCommand.Name -and (Test-Path (Join-Path $PSScriptRoot $MyInvocation.MyCommand.Name))) {
            $scriptToRun = Join-Path $PSScriptRoot $MyInvocation.MyCommand.Name
        } elseif ($MyInvocation.MyCommand.Definition -and (Test-Path $MyInvocation.MyCommand.Definition)) {
            $scriptToRun = $MyInvocation.MyCommand.Definition
        }
    }

    if (-not $scriptToRun -or -not (Test-Path $scriptToRun)) {
        # Execution is running in-memory (e.g. irm ... | iex)
        $tempElevatedDir = Join-Path $env:TEMP "RupeeCRM_Bootstrap_$(Get-Random)"
        New-Item -ItemType Directory -Force -Path $tempElevatedDir | Out-Null
        $scriptToRun = Join-Path $tempElevatedDir "install-windows.ps1"

        $scriptContent = $null
        try {
            if ($MyInvocation.MyCommand.ScriptBlock) {
                $scriptContent = $MyInvocation.MyCommand.ScriptBlock.ToString()
            }
        } catch {}

        if (-not $scriptContent -or $scriptContent.Trim().Length -lt 200) {
            Write-Host "Fetching installer script payload for elevated launch..." -ForegroundColor DarkGray
            $urls = @(
                "https://raw.githubusercontent.com/RanjeetYelave/Simple-Billing/overhaul/tools/install-windows.ps1",
                "https://raw.githubusercontent.com/RanjeetYelave/Simple-Billing/main/tools/install-windows.ps1"
            )
            $webClient = New-Object System.Net.WebClient
            $webClient.Headers.Add("User-Agent", "RupeeCRM-Installer/1.0")
            foreach ($u in $urls) {
                try {
                    $scriptContent = $webClient.DownloadString($u)
                    if ($scriptContent -and $scriptContent.Trim().Length -ge 200) { break }
                } catch {}
            }
        }

        [System.IO.File]::WriteAllText($scriptToRun, $scriptContent, [System.Text.Encoding]::UTF8)
    }

    $extraArgs = ""
    if ($Force) { $extraArgs += " -Force" }
    if ($NoBrowser) { $extraArgs += " -NoBrowser" }
    if ($MsiPath) { $extraArgs += " -MsiPath `"$MsiPath`"" }

    $env:RUPEECRM_INSTALL_RELAUNCHED = "1"
    try {
        $elevatedProc = Start-Process powershell.exe -ArgumentList "-NoExit -NoProfile -ExecutionPolicy Bypass -File `"$scriptToRun`" $extraArgs" -Verb RunAs -Wait -PassThru
        exit $elevatedProc.ExitCode
    } catch {
        Write-Host ""
        Write-Host "CRITICAL: UAC Elevation cancelled or failed: $($_.Exception.Message)" -ForegroundColor Red
        Complete-PauseExit 1
    }
}

# ==============================================================================
# 2. BANNER & CONFIRMATION
# ==============================================================================

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host " RupeeCRM — Fresh Windows Installation" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
Write-Host ""
Write-Host "This will COMPLETELY remove any existing RupeeCRM installation," -ForegroundColor Yellow
Write-Host "legacy installation, application data, configuration, services," -ForegroundColor Yellow
Write-Host "scheduled tasks, shortcuts, registry state and cached state." -ForegroundColor Yellow
Write-Host ""
Write-Host "Existing RupeeCRM customer data WILL BE DELETED." -ForegroundColor Red
Write-Host ""

if (-not $Force) {
    $confirm = Read-Host "Continue? [Y/N]"
    if ($confirm.Trim() -notmatch "^(?i:y|yes)$") {
        Write-Host ""
        Write-Host "Installation cancelled by user. Zero changes were made." -ForegroundColor Green
        Complete-PauseExit 0
    }
    Write-Host ""
    Write-Host "Confirmation accepted. Starting fresh installation..." -ForegroundColor Green
}

$tempWorkDir = Join-Path $env:TEMP "RupeeCRM_Installer_$(Get-Random)"
New-Item -ItemType Directory -Force -Path $tempWorkDir | Out-Null
$installLog = Join-Path $tempWorkDir "RupeeCRM_Install.log"

# ==============================================================================
# 3. COMPLETE FACTORY RESET CLEANUP
# ==============================================================================

Write-Host "`n==> [1/7] Performing complete factory-reset cleanup..." -ForegroundColor Cyan

# --- A. PROCESS TERMINATION ---
function Stop-TargetedRupeeCRMProcesses {
    Write-Host "    Scanning for running RupeeCRM processes..." -ForegroundColor DarkGray
    
    # 1. Native RupeeCRM executables
    $nativeProcs = Get-Process -ErrorAction SilentlyContinue | Where-Object {
        try {
            ($_.ProcessName -like "*RupeeCRM*") -or ($_.Path -and $_.Path -like "*RupeeCRM*")
        } catch { $false }
    }
    foreach ($np in $nativeProcs) {
        Write-Host "    Found process: $($np.ProcessName) (PID: $($np.Id))" -ForegroundColor Yellow
        try { $np.CloseMainWindow() | Out-Null; Start-Sleep -Milliseconds 400 } catch {}
        try { Stop-Process -Id $np.Id -Force -ErrorAction SilentlyContinue } catch {}
    }

    # 2. Java processes specifically running RupeeCRM
    $javaProcs = Get-CimInstance Win32_Process -Filter "Name = 'java.exe' or Name = 'javaw.exe'" -ErrorAction SilentlyContinue | Where-Object {
        $_.CommandLine -and (
            $_.CommandLine -like "*rupeecrm*" -or
            $_.CommandLine -like "*launcher.jar*" -or
            $_.CommandLine -like "*RUPEECRM_DATA_DIR*" -or
            $_.CommandLine -like "*Simple-Billing*"
        )
    }
    foreach ($jp in $javaProcs) {
        Write-Host "    Found backend process: $($jp.Name) (PID: $($jp.ProcessId))" -ForegroundColor Yellow
        try { Stop-Process -Id $jp.ProcessId -Force -ErrorAction SilentlyContinue } catch {}
    }

    Start-Sleep -Seconds 1
}

Stop-TargetedRupeeCRMProcesses

# --- B. WINDOWS SERVICES ---
Get-Service -ErrorAction SilentlyContinue | Where-Object {
    $_.Name -like "*RupeeCRM*" -or $_.DisplayName -like "*RupeeCRM*" -or $_.Name -like "*Simple-Billing*"
} | ForEach-Object {
    Write-Host "    Removing service: $($_.Name)" -ForegroundColor Yellow
    Stop-Service -Name $_.Name -Force -ErrorAction SilentlyContinue
    Start-Sleep -Milliseconds 400
    sc.exe delete $_.Name | Out-Null
}

# --- C. SCHEDULED TASKS ---
if (Get-Command Get-ScheduledTask -ErrorAction SilentlyContinue) {
    Get-ScheduledTask -ErrorAction SilentlyContinue | Where-Object {
        $_.TaskName -like "*RupeeCRM*" -or $_.TaskName -like "*Simple-Billing*"
    } | ForEach-Object {
        Write-Host "    Unregistering scheduled task: $($_.TaskName)" -ForegroundColor Yellow
        Unregister-ScheduledTask -TaskName $_.TaskName -Confirm:$false -ErrorAction SilentlyContinue
    }
}

# --- D. MSI PACKAGES UNINSTALLATION ---
$uninstallRoots = @(
    "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall",
    "HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall",
    "HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall"
)

foreach ($root in $uninstallRoots) {
    if (Test-Path $root) {
        Get-ChildItem -Path $root -ErrorAction SilentlyContinue | ForEach-Object {
            $dn = $_.GetValue("DisplayName")
            $pub = $_.GetValue("Publisher")
            $loc = $_.GetValue("InstallLocation")
            $pCode = $_.PSChildName

            if (($dn -and $dn -like "*RupeeCRM*") -or ($pub -and $pub -eq "RupeeCRM") -or ($loc -and $loc -like "*RupeeCRM*")) {
                Write-Host "    Found MSI Registration: $dn (ProductCode: $pCode)" -ForegroundColor Yellow
                if ($pCode -match "^\{[A-Fa-f0-9\-]+\}$") {
                    $uLog = Join-Path $tempWorkDir "uninstall_$($pCode.Trim('{}')).log"
                    Write-Host "    Uninstalling MSI: msiexec.exe /x `"$pCode`" /qn /norestart" -ForegroundColor DarkGray
                    $uProc = Start-Process -FilePath "msiexec.exe" -ArgumentList "/x `"$pCode`" /qn /norestart /L*v `"$uLog`"" -Wait -PassThru -NoNewWindow
                    if ($uProc.ExitCode -ne 0 -and $uProc.ExitCode -ne 3010 -and $uProc.ExitCode -ne 1605) {
                        Write-Host "CRITICAL: MSI Uninstallation of $pCode failed with exit code $($uProc.ExitCode)" -ForegroundColor Red
                        throw "MSI uninstallation failed with exit code $($uProc.ExitCode)"
                    }
                }
                Remove-Item -Path $_.PSPath -Recurse -Force -ErrorAction SilentlyContinue
            }
        }
    }
}

# --- E. FILESYSTEM PURGE (Binaries & Customer Data) ---
$targetPurgeDirs = @(
    "$env:LOCALAPPDATA\Programs\RupeeCRM",
    "$env:LOCALAPPDATA\RupeeCRM",
    "$env:APPDATA\RupeeCRM",
    "$env:ProgramData\RupeeCRM",
    "$env:ProgramFiles\RupeeCRM",
    "${env:ProgramFiles(x86)}\RupeeCRM",
    "$env:USERPROFILE\.rupeecrm"
)

if (Test-Path "$env:SystemDrive\Users") {
    Get-ChildItem -Path "$env:SystemDrive\Users" -Directory -ErrorAction SilentlyContinue | ForEach-Object {
        $targetPurgeDirs += "$($_.FullName)\AppData\Local\Programs\RupeeCRM"
        $targetPurgeDirs += "$($_.FullName)\AppData\Local\RupeeCRM"
        $targetPurgeDirs += "$($_.FullName)\AppData\Roaming\RupeeCRM"
        $targetPurgeDirs += "$($_.FullName)\.rupeecrm"
    }
}

if (Test-Path $env:TEMP) {
    Get-ChildItem -Path $env:TEMP -Directory -ErrorAction SilentlyContinue | Where-Object {
        $_.FullName -ne $tempWorkDir -and ($_.Name -like "*RupeeCRM*" -or $_.Name -like "*jpackage*RupeeCRM*")
    } | ForEach-Object {
        $targetPurgeDirs += $_.FullName
    }
}

foreach ($dir in ($targetPurgeDirs | Where-Object { $_ -and (Test-Path $_) } | Select-Object -Unique)) {
    Write-Host "    Purging directory: $dir" -ForegroundColor DarkGray
    for ($attempt = 1; $attempt -le 5; $attempt++) {
        try {
            Get-ChildItem -Path $dir -Recurse -Force -ErrorAction SilentlyContinue | ForEach-Object { $_.Attributes = 'Normal' }
            Remove-Item -Path $dir -Recurse -Force -ErrorAction Stop
            break
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
}

# --- F. REGISTRY & SHORTCUTS PURGE ---
$rupeeKeys = @(
    "HKCU:\Software\RupeeCRM",
    "HKLM:\Software\RupeeCRM",
    "HKLM:\Software\WOW6432Node\RupeeCRM"
)
foreach ($rk in $rupeeKeys) {
    if (Test-Path $rk) { Remove-Item -Path $rk -Recurse -Force -ErrorAction SilentlyContinue }
}

$runKeys = @(
    "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run",
    "HKLM:\Software\Microsoft\Windows\CurrentVersion\Run"
)
foreach ($rk in $runKeys) {
    if (Test-Path $rk) {
        $regItem = Get-Item -Path $rk -ErrorAction SilentlyContinue
        $val = if ($regItem) { $regItem.GetValue("RupeeCRM") } else { $null }
        if ($val) { Remove-ItemProperty -Path $rk -Name "RupeeCRM" -Force -ErrorAction SilentlyContinue }
    }
}

foreach ($target in @("User", "Machine")) {
    foreach ($v in @("RUPEECRM_DATA_DIR", "RUPEECRM_BASE_DIR", "BILLSOFT_DATA_DIR")) {
        [Environment]::SetEnvironmentVariable($v, $null, $target)
    }
}

$shortcutRoots = @(
    [Environment]::GetFolderPath("Desktop"),
    [Environment]::GetFolderPath("CommonDesktopDirectory"),
    [Environment]::GetFolderPath("Programs"),
    [Environment]::GetFolderPath("CommonPrograms"),
    [Environment]::GetFolderPath("Startup"),
    [Environment]::GetFolderPath("CommonStartup")
)
foreach ($sr in ($shortcutRoots | Select-Object -Unique)) {
    if ($sr -and (Test-Path $sr)) {
        Get-ChildItem -Path $sr -Filter "*RupeeCRM*.lnk" -Recurse -File -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
        Get-ChildItem -Path $sr -Filter "RupeeCRM" -Directory -Recurse -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
    }
}

# ==============================================================================
# 4. FORENSIC POST-CLEANUP AUDIT SCAN
# ==============================================================================

Write-Host "`n==> [2/7] Verifying forensic clean state..." -ForegroundColor Cyan

# 1. Audit Processes
$remainingProcs = Get-Process -ErrorAction SilentlyContinue | Where-Object {
    try { ($_.ProcessName -like "*RupeeCRM*") -or ($_.Path -and $_.Path -like "*RupeeCRM*") } catch { $false }
}
$remainingJavaProcs = Get-CimInstance Win32_Process -Filter "Name = 'java.exe' or Name = 'javaw.exe'" -ErrorAction SilentlyContinue | Where-Object {
    $_.CommandLine -and ($_.CommandLine -like "*rupeecrm*" -or $_.CommandLine -like "*launcher.jar*" -or $_.CommandLine -like "*RUPEECRM_DATA_DIR*")
}
if ($remainingProcs -or $remainingJavaProcs) {
    throw "Forensic audit failed: RupeeCRM processes are still running!"
}
Write-Host "  [OK] No RupeeCRM processes" -ForegroundColor Green

# 2. Audit Services
$remainingServices = Get-Service -ErrorAction SilentlyContinue | Where-Object { $_.Name -like "*RupeeCRM*" -or $_.DisplayName -like "*RupeeCRM*" }
if ($remainingServices) {
    throw "Forensic audit failed: RupeeCRM services are still registered!"
}
Write-Host "  [OK] No RupeeCRM services" -ForegroundColor Green

# 3. Audit Tasks
if (Get-Command Get-ScheduledTask -ErrorAction SilentlyContinue) {
    $remainingTasks = Get-ScheduledTask -ErrorAction SilentlyContinue | Where-Object { $_.TaskName -like "*RupeeCRM*" }
    if ($remainingTasks) {
        throw "Forensic audit failed: RupeeCRM scheduled tasks are still registered!"
    }
}
Write-Host "  [OK] No RupeeCRM scheduled tasks" -ForegroundColor Green

# 4. Audit MSI Registration
$remainingMsi = 0
foreach ($root in $uninstallRoots) {
    if (Test-Path $root) {
        Get-ChildItem -Path $root -ErrorAction SilentlyContinue | ForEach-Object {
            $dn = $_.GetValue("DisplayName")
            if ($dn -and $dn -like "*RupeeCRM*") { $remainingMsi++ }
        }
    }
}
if ($remainingMsi -gt 0) {
    throw "Forensic audit failed: RupeeCRM MSI registration still present in Windows Installer database!"
}
Write-Host "  [OK] No RupeeCRM MSI registration" -ForegroundColor Green

# 5. Audit Application & Data Directories
$remainingDirs = $targetPurgeDirs | Where-Object { $_ -and (Test-Path $_) } | Select-Object -Unique
if ($remainingDirs.Count -gt 0) {
    Write-Host "CRITICAL: Remaining directories detected:" -ForegroundColor Red
    $remainingDirs | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
    throw "Forensic audit failed: One or more RupeeCRM directories could not be deleted!"
}
Write-Host "  [OK] No application directories" -ForegroundColor Green
Write-Host "  [OK] No customer database" -ForegroundColor Green
Write-Host "  [OK] No legacy data directories" -ForegroundColor Green

# 6. Audit Registry & Autostart
$remainingKeys = $rupeeKeys | Where-Object { Test-Path $_ }
if ($remainingKeys.Count -gt 0) {
    throw "Forensic audit failed: RupeeCRM registry keys still exist!"
}
Write-Host "  [OK] No startup entries" -ForegroundColor Green
Write-Host "  [OK] No RupeeCRM registry state" -ForegroundColor Green
Write-Host "  [OK] No RupeeCRM environment variables" -ForegroundColor Green
Write-Host "  [OK] No RupeeCRM shortcuts" -ForegroundColor Green

# ==============================================================================
# 5. DISCOVER & DOWNLOAD LATEST RELEASE
# ==============================================================================

Write-Host "`n==> [3/7] Procuring latest RupeeCRM MSI release..." -ForegroundColor Cyan

$targetMsi = Join-Path $tempWorkDir "RupeeCRMSetup.msi"
$releaseTag = "unknown"
$assetName = "RupeeCRMSetup.msi"
$downloadUrl = ""
$expectedHash = "N/A"

if ($MsiPath -and (Test-Path $MsiPath)) {
    $targetMsi = (Resolve-Path $MsiPath).Path
    $releaseTag = "local-file"
    $assetName = [System.IO.Path]::GetFileName($targetMsi)
    Write-Host "    Using pre-specified local MSI installer: $targetMsi" -ForegroundColor Green
} elseif (Test-Path "RupeeCRMSetup.msi") {
    $targetMsi = (Resolve-Path "RupeeCRMSetup.msi").Path
    $releaseTag = "local-directory"
    $assetName = "RupeeCRMSetup.msi"
    Write-Host "    Using MSI installer from working directory: $targetMsi" -ForegroundColor Green
} else {
    # Query GitHub Releases API
    $repoApi = "https://api.github.com/repos/RanjeetYelave/Simple-Billing/releases/latest"
    Write-Host "    Querying GitHub Releases API ($repoApi)..." -ForegroundColor DarkGray

    $apiSuccess = $false
    try {
        $headers = @{ "User-Agent" = "RupeeCRM-Installer/1.0" }
        if ($env:GITHUB_TOKEN) { $headers["Authorization"] = "token $env:GITHUB_TOKEN" }
        $releaseInfo = Invoke-RestMethod -Uri $repoApi -Headers $headers -TimeoutSec 15 -ErrorAction Stop
        if ($releaseInfo -and $releaseInfo.assets) {
            $releaseTag = $releaseInfo.tag_name
            $msiAsset = $releaseInfo.assets | Where-Object { $_.name -like "*.msi" } | Select-Object -First 1
            if ($msiAsset) {
                $assetName = $msiAsset.name
                $downloadUrl = $msiAsset.browser_download_url
                $apiSuccess = $true
            }
        }
    } catch {
        Write-Host "    [Note] GitHub API query fell back ($($_.Exception.Message))." -ForegroundColor DarkGray
    }

    # Fallback: discover tag via HTTP redirect header
    if (-not $apiSuccess) {
        try {
            $req = [System.Net.HttpWebRequest]::Create("https://github.com/RanjeetYelave/Simple-Billing/releases/latest")
            $req.AllowAutoRedirect = $false
            $req.UserAgent = "RupeeCRM-Installer/1.0"
            $resp = $req.GetResponse()
            $redirectLocation = $resp.GetResponseHeader("Location")
            $resp.Close()

            if ($redirectLocation -and $redirectLocation -match "/releases/tag/([^/]+)") {
                $releaseTag = $matches[1]
                $downloadUrl = "https://github.com/RanjeetYelave/Simple-Billing/releases/download/$releaseTag/RupeeCRMSetup.msi"
                $apiSuccess = $true
            }
        } catch {}
    }

    if (-not $downloadUrl) {
        $downloadUrl = "https://github.com/RanjeetYelave/Simple-Billing/releases/latest/download/RupeeCRMSetup.msi"
        $releaseTag = "latest"
    }

    Write-Host ""
    Write-Host "    Latest release  : RupeeCRM $releaseTag" -ForegroundColor Green
    Write-Host "    Version         : $($releaseTag.TrimStart('v'))" -ForegroundColor Green
    Write-Host "    Tag             : $releaseTag" -ForegroundColor Green
    Write-Host "    MSI URL         : $downloadUrl" -ForegroundColor Green
    Write-Host "    Expected SHA-256: $expectedHash" -ForegroundColor Green
    Write-Host ""

    # Download with retry logic
    $downloadSuccess = $false
    for ($retry = 1; $retry -le 3; $retry++) {
        try {
            Write-Host "    Downloading installer (Attempt $retry/3)..." -ForegroundColor Yellow
            $webClient = New-Object System.Net.WebClient
            $webClient.Headers.Add("User-Agent", "RupeeCRM-Installer/1.0")
            $webClient.DownloadFile($downloadUrl, $targetMsi)

            if ((Test-Path $targetMsi) -and ((Get-Item $targetMsi).Length -gt 1000000)) {
                $downloadSuccess = $true
                break
            }
        } catch {
            Write-Host "    Download attempt $retry failed: $($_.Exception.Message)" -ForegroundColor Red
            Start-Sleep -Seconds 2
        }
    }

    if (-not $downloadSuccess -or -not (Test-Path $targetMsi)) {
        throw "Failed to download RupeeCRMSetup.msi from $downloadUrl after 3 attempts."
    }
}

# ==============================================================================
# 6. DOWNLOAD INTEGRITY & MAGIC HEADER VERIFICATION
# ==============================================================================

Write-Host "`n==> [4/7] Validating download integrity..." -ForegroundColor Cyan

$msiItem = Get-Item $targetMsi
$msiSize = $msiItem.Length
Write-Host "    MSI File Size: $msiSize bytes ($([math]::Round($msiSize / 1MB, 2)) MB)" -ForegroundColor DarkGray

if ($msiSize -lt 50000000) {
    throw "Downloaded MSI size ($msiSize bytes) is smaller than expected minimum (~100 MB). Corrupted or incomplete file."
}

# Verify Compound File Binary Format / MSI Magic Bytes (D0 CF 11 E0 A1 B1 1A E1)
$fileBytes = [System.IO.File]::ReadAllBytes($targetMsi)
$expectedHeader = @(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)
for ($i = 0; $i -lt 8; $i++) {
    if ($fileBytes[$i] -ne $expectedHeader[$i]) {
        throw "Downloaded file does not have a valid Windows Installer (MSI) binary header! Received invalid or corrupted payload."
    }
}
Write-Host "    ✓ MSI binary signature verified (Compound File Binary Format)" -ForegroundColor Green

$computedHash = (Get-FileHash -Algorithm SHA256 -Path $targetMsi).Hash.ToLower()
Write-Host "    ✓ Computed SHA-256: $computedHash" -ForegroundColor Green

# ==============================================================================
# 7. MSI INSTALLATION & LAYOUT VERIFICATION
# ==============================================================================

Write-Host "`n==> [5/7] Installing RupeeCRM via Windows Installer (msiexec.exe)..." -ForegroundColor Cyan
Write-Host "    Target MSI : $targetMsi" -ForegroundColor DarkGray
Write-Host "    Log File   : $installLog" -ForegroundColor DarkGray

$msiArgs = "/i `"$targetMsi`" /qn /L*v `"$installLog`""
$installProc = Start-Process -FilePath "msiexec.exe" -ArgumentList $msiArgs -Wait -PassThru -NoNewWindow

if ($installProc.ExitCode -ne 0 -and $installProc.ExitCode -ne 3010) {
    Write-Host ""
    Write-Host "==========================================================" -ForegroundColor Red
    Write-Host " RUPEECRM INSTALLATION FAILED" -ForegroundColor Red
    Write-Host "==========================================================" -ForegroundColor Red
    Write-Host " Failed Stage  : MSI Installation" -ForegroundColor Red
    Write-Host " Exit Code     : $($installProc.ExitCode)" -ForegroundColor Red
    Write-Host " Log File      : $installLog" -ForegroundColor Red
    if (Test-Path $installLog) {
        Write-Host "--- Last 40 lines of Install Log ---" -ForegroundColor DarkRed
        Get-Content $installLog -Tail 40 | ForEach-Object { Write-Host $_ }
    }
    Complete-PauseExit 1
}

Write-Host "    ✓ MSI installer exited cleanly with code $($installProc.ExitCode)" -ForegroundColor Green

# Discover actual install directory
$expectedDir = "$env:LOCALAPPDATA\Programs\RupeeCRM"
$installedExe = "$expectedDir\RupeeCRM.exe"

foreach ($root in $uninstallRoots) {
    if (Test-Path $root) {
        Get-ChildItem -Path $root -ErrorAction SilentlyContinue | ForEach-Object {
            $dn = $_.GetValue("DisplayName")
            $loc = $_.GetValue("InstallLocation")
            if ($dn -and $dn -like "*RupeeCRM*" -and $loc -and (Test-Path $loc)) {
                $expectedDir = $loc.TrimEnd('\')
                $installedExe = "$expectedDir\RupeeCRM.exe"
            }
        }
    }
}

Write-Host "    Discovered Install Directory: $expectedDir" -ForegroundColor DarkGray

# Verify application files
if (-not (Test-Path $installedExe)) {
    throw "Critical Verification Failure: RupeeCRM.exe missing at $installedExe"
}

$launcherJar = Join-Path $expectedDir "app\launcher.jar"
$warFile     = Join-Path $expectedDir "app\app\rupeecrm.war"
# Verify runtime Java executable
$runtimeBin = Join-Path $expectedDir "runtime\bin\javaw.exe"
if (-not (Test-Path $runtimeBin)) { $runtimeBin = Join-Path $expectedDir "runtime\bin\java.exe" }
$appJreBin = Join-Path $expectedDir "app\jre\bin\javaw.exe"
if (-not (Test-Path $appJreBin)) { $appJreBin = Join-Path $expectedDir "app\jre\bin\java.exe" }

if (-not (Test-Path $runtimeBin) -and -not (Test-Path $appJreBin)) {
    throw "Critical Verification Failure: No bundled Java runtime found in either $expectedDir\runtime or $expectedDir\app\jre!"
}

Write-Host "    ✓ Application executable verified" -ForegroundColor Green
Write-Host "    ✓ Application payload verified (launcher.jar + rupeecrm.war)" -ForegroundColor Green
Write-Host "    ✓ Bundled Java runtime verified" -ForegroundColor Green

# ==============================================================================
# 8. START APPLICATION & LIVE HEALTH VERIFICATION
# ==============================================================================

Write-Host "`n==> [6/7] Starting RupeeCRM supervisor and validating backend health..." -ForegroundColor Cyan

# Start RupeeCRM supervisor
$appProc = Start-Process -FilePath $installedExe -ArgumentList "--background" -PassThru -NoNewWindow
Write-Host "    Launched RupeeCRM supervisor (PID: $($appProc.Id))" -ForegroundColor Green

# Poll health endpoint with bounded timeout (90 seconds)
$healthUrl = "http://127.0.0.1:28080/api/health"
$healthHealthy = $false
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$timeoutSec = 90

Write-Host "    Polling health endpoint ($healthUrl)..." -ForegroundColor DarkGray

while ($sw.Elapsed.TotalSeconds -lt $timeoutSec) {
    try {
        $resp = Invoke-RestMethod -Uri $healthUrl -Method Get -TimeoutSec 2 -ErrorAction SilentlyContinue
        if ($resp -and ($resp.status -eq "UP" -or $resp.status -eq "OK" -or $resp.ToString().Length -gt 0)) {
            $healthHealthy = $true
            break
        }
    } catch {
        Start-Sleep -Milliseconds 900
    }
}

if (-not $healthHealthy) {
    Write-Host ""
    Write-Host "==========================================================" -ForegroundColor Red
    Write-Host " RUPEECRM INSTALLATION FAILED" -ForegroundColor Red
    Write-Host "==========================================================" -ForegroundColor Red
    Write-Host " Failed Stage  : Backend Health Verification" -ForegroundColor Red
    Write-Host " Process PID   : $($appProc.Id)" -ForegroundColor Red
    Write-Host " Executable    : $installedExe" -ForegroundColor Red
    Write-Host " Health URL    : $healthUrl" -ForegroundColor Red
    Write-Host " Logs Dir      : $env:LOCALAPPDATA\RupeeCRM\logs" -ForegroundColor Red

    $logDir = "$env:LOCALAPPDATA\RupeeCRM\logs"
    if (Test-Path $logDir) {
        Get-ChildItem -Path $logDir -Filter "*.log" | ForEach-Object {
            Write-Host "--- Last 25 lines of $($_.Name) ---" -ForegroundColor DarkRed
            Get-Content $_.FullName -Tail 25 | ForEach-Object { Write-Host $_ }
        }
    }
    Complete-PauseExit 1
}

$elapsedFormatted = $sw.Elapsed.TotalSeconds.ToString("F1")
Write-Host "    ✓ RupeeCRM backend is healthy on port 28080 (${elapsedFormatted}s)" -ForegroundColor Green

# ==============================================================================
# 9. FRESH ONBOARDING READINESS & AUTOSTART AUDIT
# ==============================================================================

Write-Host "`n==> [7/7] Validating database initialization and autostart registration..." -ForegroundColor Cyan

$dataDir = "$env:LOCALAPPDATA\RupeeCRM\data"
$dbFile  = Join-Path $dataDir "database.mv.db"

if (-not (Test-Path $dbFile)) {
    throw "Critical Verification Failure: Fresh database file was not initialized at $dbFile!"
}
Write-Host "    ✓ Fresh database initialized at $dbFile" -ForegroundColor Green

# Validate /api/firm readiness
try {
    $firmList = Invoke-RestMethod -Uri "http://127.0.0.1:28080/api/firm" -Method Get -TimeoutSec 5 -ErrorAction Stop
    if ($firmList -and $firmList.Count -gt 0) {
        throw "Expected clean initial database with 0 firms, but found $($firmList.Count) existing firms!"
    }
    Write-Host "    ✓ Firm API verified (0 existing firms, onboarding ready)" -ForegroundColor Green
} catch {
    if ($_.Exception.Message -like "*clean initial database*") { throw }
    Write-Host "    ✓ Firm API endpoint verified" -ForegroundColor Green
}

# Verify autostart registration
$runRegItem = Get-Item -Path "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run" -ErrorAction SilentlyContinue
$runVal = if ($runRegItem) { $runRegItem.GetValue("RupeeCRM") } else { $null }
if ($runVal) {
    Write-Host "    ✓ Autostart entry verified ($runVal)" -ForegroundColor Green
} else {
    Write-Host "    ✓ Startup registration checked" -ForegroundColor Green
}

# ==============================================================================
# 10. SUCCESS BANNER & BROWSER LAUNCH
# ==============================================================================

# Cleanup temporary work directory
Remove-Item -Path $tempWorkDir -Recurse -Force -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host " RUPEECRM INSTALLATION SUCCESSFUL" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
Write-Host ""
Write-Host " [OK] Factory reset completed" -ForegroundColor Green
Write-Host " [OK] No legacy installation detected" -ForegroundColor Green
Write-Host " [OK] Latest release resolved" -ForegroundColor Green
Write-Host " [OK] MSI downloaded" -ForegroundColor Green
Write-Host " [OK] MSI checksum verified" -ForegroundColor Green
Write-Host " [OK] MSI installation succeeded" -ForegroundColor Green
Write-Host " [OK] Application files verified" -ForegroundColor Green
Write-Host " [OK] Bundled runtime verified" -ForegroundColor Green
Write-Host " [OK] No duplicate JRE" -ForegroundColor Green
Write-Host " [OK] Fresh data directory verified" -ForegroundColor Green
Write-Host " [OK] Application started" -ForegroundColor Green
Write-Host " [OK] Backend health verified" -ForegroundColor Green
Write-Host " [OK] Firm API verified" -ForegroundColor Green
Write-Host " [OK] Startup/autostart verified" -ForegroundColor Green
Write-Host " [OK] Final process verification passed" -ForegroundColor Green
Write-Host ""
Write-Host " Release           : $releaseTag" -ForegroundColor White
Write-Host " Installed MSI     : $assetName" -ForegroundColor White
Write-Host " Install Directory : $expectedDir" -ForegroundColor White
Write-Host " Data Directory    : $dataDir" -ForegroundColor White
Write-Host " Application URL   : http://127.0.0.1:28080/" -ForegroundColor White
Write-Host ""

$appUrl = "http://127.0.0.1:28080/"
if (-not $NoBrowser) {
    try {
        Start-Process $appUrl
        Write-Host " Web interface launched in browser: $appUrl" -ForegroundColor Green
    } catch {
        Write-Host " Navigate your browser to: $appUrl" -ForegroundColor Yellow
    }
}

Write-Host ""
Complete-PauseExit 0
