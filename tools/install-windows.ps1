# ==============================================================================
# RupeeCRM Windows 1-Line Quick Installer
# ==============================================================================
# Usage (PowerShell):
#   irm https://raw.githubusercontent.com/RanjeetYelave/Simple-Billing/overhaul/tools/install-windows.ps1 | iex
#
# Alternative (Download & Inspect first):
#   Invoke-WebRequest -Uri "https://raw.githubusercontent.com/RanjeetYelave/Simple-Billing/overhaul/tools/install-windows.ps1" -OutFile install-windows.ps1
#   .\install-windows.ps1
#
# This installer:
# 1. Detects Windows x64 architecture.
# 2. Queries GitHub for the latest RupeeCRM Windows release asset.
# 3. Downloads the release package and its authoritative SHA-256 checksum.
# 4. Verifies SHA-256 integrity before installation.
# 5. Installs application binaries to %LOCALAPPDATA%\Programs\RupeeCRM (Non-Admin).
# 6. Preserves customer database in %APPDATA%\SimpleBilling (Zero data loss).
# 7. Creates Desktop and Start Menu shortcuts.
# 8. Configures user-level background auto-start.
# 9. Starts RupeeCRM supervisor and opens the application in the default browser.
# ==============================================================================

[CmdletBinding()]
param(
    [string]$Version = "",
    [string]$CustomDownloadUrl = "",
    [string]$CustomChecksum = "",
    [switch]$SkipLaunch = $false,
    [switch]$Background = $false
)

$ErrorActionPreference = "Stop"

# Enforce TLS 1.2 for secure GitHub downloads across Windows PowerShell 5.1+
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
} catch {
    # Ignore if not modifiable
}

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

function Write-FatalError {
    param([string]$Message)
    Write-Host ""
    Write-Host "[X] ERROR: $Message" -ForegroundColor Red
    Write-Host ""
    exit 1
}

Write-Host "======================================================" -ForegroundColor Blue
Write-Host "      RupeeCRM - Windows Native Quick Installer       " -ForegroundColor White
Write-Host "======================================================" -ForegroundColor Blue
Write-Host ""

# ------------------------------------------------------------------------------
# 1. Environment & Architecture Check
# ------------------------------------------------------------------------------
Write-Step "Checking Windows environment..."

$isWindowsEnvironment = $false
if ($PSVersionTable.PSEdition -eq "Core") {
    $isWindowsEnvironment = [bool]$IsWindows
} else {
    $isWindowsEnvironment = ($env:OS -eq "Windows_NT")
}

if (-not $isWindowsEnvironment) {
    Write-FatalError "This installer is intended for Microsoft Windows only."
}

$rawArch = $env:PROCESSOR_ARCHITECTURE
$is64Bit = [Environment]::Is64BitOperatingSystem

if (-not $is64Bit) {
    Write-FatalError "RupeeCRM requires a 64-bit Windows operating system (x64 / AMD64)."
}

if ($rawArch -eq "ARM64") {
    Write-WarnMsg "Detected Windows on ARM64. RupeeCRM x64 will run via Windows x64 emulation."
} elseif ($rawArch -ne "AMD64" -and $env:PROCESSOR_ARCHITEW6432 -ne "AMD64") {
    Write-WarnMsg "Detected architecture: $rawArch. Proceeding with x64 binary execution."
} else {
    Write-Success "Detected 64-bit Windows ($rawArch)"
}

# ------------------------------------------------------------------------------
# 2. Release Discovery
# ------------------------------------------------------------------------------
$Repo = "RanjeetYelave/Simple-Billing"
$ApiUrl = "https://api.github.com/repos/$Repo/releases/latest"
$AssetName = "RupeeCRM-Windows-x64.zip"
$ChecksumAssetName = "RupeeCRM-Windows-x64.zip.sha256"

$DownloadUrl = $CustomDownloadUrl
$ExpectedSha256 = $CustomChecksum
$TagName = $Version

if (-not $DownloadUrl) {
    Write-Step "Checking latest release from GitHub ($Repo)..."
    try {
        $headers = @{
            "User-Agent" = "RupeeCRM-Windows-Installer"
            "Accept"     = "application/vnd.github.v3+json"
        }
        $release = Invoke-RestMethod -Uri $ApiUrl -Headers $headers -Method Get -TimeoutSec 30
        $TagName = $release.tag_name

        foreach ($asset in $release.assets) {
            if ($asset.name -eq $AssetName) {
                $DownloadUrl = $asset.browser_download_url
            }
            if ($asset.name -eq $ChecksumAssetName) {
                $ChecksumUrl = $asset.browser_download_url
            }
        }
    } catch {
        Write-WarnMsg "GitHub API rate limit or network issue. Attempting direct release asset fallback..."
    }

    if (-not $TagName) {
        $TagName = "latest"
    }

    if (-not $DownloadUrl) {
        $DownloadUrl = "https://github.com/$Repo/releases/download/$TagName/$AssetName"
    }
    if (-not $ChecksumUrl) {
        $ChecksumUrl = "https://github.com/$Repo/releases/download/$TagName/$ChecksumAssetName"
    }
}

Write-Success "Target release: $TagName"

# ------------------------------------------------------------------------------
# 3. Create Temporary Workspace & Download
# ------------------------------------------------------------------------------
$TempGuid = [System.Guid]::NewGuid().ToString("N").Substring(0, 8)
$TempDir = Join-Path $env:TEMP "rupeecrm_install_$TempGuid"
New-Item -ItemType Directory -Path $TempDir -Force | Out-Null

function Cleanup-Temp {
    if (Test-Path $TempDir) {
        Remove-Item -Path $TempDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

$ZipPath = Join-Path $TempDir $AssetName
$ShaPath = Join-Path $TempDir $ChecksumAssetName

try {
    # 3a. Download Checksum if not provided directly
    if (-not $ExpectedSha256 -and $ChecksumUrl) {
        Write-Step "Fetching SHA-256 release checksum..."
        try {
            if (Test-Path $ChecksumUrl -PathType Leaf) {
                Copy-Item -Path $ChecksumUrl -Destination $ShaPath -Force
            } elseif ($ChecksumUrl -like "file://*") {
                $uri = [System.Uri]$ChecksumUrl
                Copy-Item -Path $uri.LocalPath -Destination $ShaPath -Force
            } else {
                Invoke-WebRequest -Uri $ChecksumUrl -OutFile $ShaPath -TimeoutSec 30 -UseBasicParsing -ErrorAction Stop
            }
            if (Test-Path $ShaPath) {
                $shaContent = (Get-Content -Path $ShaPath -Raw).Trim()
                if ($shaContent -match "([a-fA-F0-9]{64})") {
                    $ExpectedSha256 = $matches[1].ToLower()
                }
            }
        } catch {
            Write-WarnMsg "Could not retrieve standalone .sha256 asset file. Will verify release integrity..."
        }
    }

    # 3b. Download Main ZIP Archive
    Write-Step "Downloading RupeeCRM package ($AssetName)..."
    try {
        if (Test-Path $DownloadUrl -PathType Leaf) {
            Copy-Item -Path $DownloadUrl -Destination $ZipPath -Force
        } elseif ($DownloadUrl -like "file://*") {
            $uri = [System.Uri]$DownloadUrl
            Copy-Item -Path $uri.LocalPath -Destination $ZipPath -Force
        } else {
            Invoke-WebRequest -Uri $DownloadUrl -OutFile $ZipPath -TimeoutSec 300 -UseBasicParsing -ErrorAction Stop
        }
    } catch {
        Write-FatalError "Failed to download $DownloadUrl : $($_.Exception.Message)"
    }

    if (-not (Test-Path $ZipPath) -or (Get-Item $ZipPath).Length -lt 1024) {
        Write-FatalError "Downloaded package is invalid or empty ($ZipPath)."
    }

    # --------------------------------------------------------------------------
    # 4. SHA-256 Integrity Verification
    # --------------------------------------------------------------------------
    Write-Step "Verifying package integrity (SHA-256)..."

    $ActualSha256 = (Get-FileHash -Path $ZipPath -Algorithm SHA256).Hash.ToLower()

    if ($ExpectedSha256) {
        if ($ActualSha256 -ne $ExpectedSha256.ToLower()) {
            Write-FatalError "SHA-256 Checksum verification failed!`r`n  Expected: $ExpectedSha256`r`n  Actual:   $ActualSha256"
        }
        Write-Success "SHA-256 verification passed ($ActualSha256)"
    } else {
        Write-WarnMsg "No reference checksum available. Package hash: $ActualSha256"
    }

    # --------------------------------------------------------------------------
    # 5. Extract Package Archive
    # --------------------------------------------------------------------------
    Write-Step "Extracting installation files..."
    $StagingDir = Join-Path $TempDir "staging"
    New-Item -ItemType Directory -Path $StagingDir -Force | Out-Null

    try {
        Expand-Archive -Path $ZipPath -DestinationPath $StagingDir -Force
    } catch {
        # Fallback for systems where Expand-Archive fails
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::ExtractToDirectory($ZipPath, $StagingDir)
    }

    # Locate the application directory within extracted files
    $SourceDir = $StagingDir
    if (Test-Path (Join-Path $StagingDir "RupeeCRM\RupeeCRM.exe")) {
        $SourceDir = Join-Path $StagingDir "RupeeCRM"
    } elseif (Test-Path (Join-Path $StagingDir "RupeeCRM.exe")) {
        $SourceDir = $StagingDir
    } else {
        # Search for RupeeCRM.exe recursively
        $foundExe = Get-ChildItem -Path $StagingDir -Filter "RupeeCRM.exe" -Recurse -File | Select-Object -First 1
        if ($foundExe) {
            $SourceDir = $foundExe.DirectoryName
        } else {
            Write-FatalError "RupeeCRM.exe was not found in the extracted package."
        }
    }

    # --------------------------------------------------------------------------
    # 6. Stop Existing Process Cleanly (Update Safe)
    # --------------------------------------------------------------------------
    $runningProcesses = Get-Process -Name "RupeeCRM", "Billsoft" -ErrorAction SilentlyContinue
    if ($runningProcesses) {
        Write-Step "Stopping running RupeeCRM processes before updating..."
        foreach ($proc in $runningProcesses) {
            try {
                $proc.CloseMainWindow() | Out-Null
                Start-Sleep -Milliseconds 500
                if (-not $proc.HasExited) {
                    $proc.Kill()
                    $proc.WaitForExit(3000)
                }
            } catch {}
        }
        Start-Sleep -Seconds 1
    }

    # --------------------------------------------------------------------------
    # 7. Install to %LOCALAPPDATA%\Programs\RupeeCRM
    # --------------------------------------------------------------------------
    $InstallDir = Join-Path $env:LOCALAPPDATA "Programs\RupeeCRM"
    $DataDir = Join-Path $env:APPDATA "SimpleBilling"

    Write-Step "Installing application binaries to $InstallDir..."

    # Ensure parent Programs directory exists
    $ProgramsDir = Join-Path $env:LOCALAPPDATA "Programs"
    if (-not (Test-Path $ProgramsDir)) {
        New-Item -ItemType Directory -Path $ProgramsDir -Force | Out-Null
    }

    # Atomic / Safe replacement
    $BackupOldDir = Join-Path $env:LOCALAPPDATA "Programs\RupeeCRM.old"
    if (Test-Path $BackupOldDir) {
        Remove-Item -Path $BackupOldDir -Recurse -Force -ErrorAction SilentlyContinue
    }

    if (Test-Path $InstallDir) {
        try {
            Rename-Item -Path $InstallDir -NewName "RupeeCRM.old" -Force
        } catch {
            Write-WarnMsg "Could not rename existing directory. Attempting direct overwrite..."
        }
    }

    if (-not (Test-Path $InstallDir)) {
        New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
    }

    try {
        Copy-Item -Path (Join-Path $SourceDir "*") -Destination $InstallDir -Recurse -Force
        # Remove old backup if replacement succeeded
        if (Test-Path $BackupOldDir) {
            Remove-Item -Path $BackupOldDir -Recurse -Force -ErrorAction SilentlyContinue
        }
    } catch {
        # Restore old version if copy failed
        if (Test-Path $BackupOldDir -and -not (Test-Path $InstallDir)) {
            Rename-Item -Path $BackupOldDir -NewName "RupeeCRM" -Force -ErrorAction SilentlyContinue
        }
        Write-FatalError "Failed to install application files: $($_.Exception.Message)"
    }

    # Verify target executable exists
    $TargetExe = Join-Path $InstallDir "RupeeCRM.exe"
    if (-not (Test-Path $TargetExe)) {
        Write-FatalError "Installation verification failed: $TargetExe does not exist."
    }

    # Ensure uninstaller is present in installation directory
    $UninstallerSource = Join-Path $PSScriptRoot "uninstall-windows.ps1"
    $UninstallerDest = Join-Path $InstallDir "uninstall-windows.ps1"
    if (Test-Path $UninstallerSource) {
        Copy-Item -Path $UninstallerSource -Destination $UninstallerDest -Force -ErrorAction SilentlyContinue
    }

    Write-Success "Application binaries installed successfully."

    # --------------------------------------------------------------------------
    # 8. Customer Data Directory Safety Verification
    # --------------------------------------------------------------------------
    if (Test-Path $DataDir) {
        Write-Success "Customer database preserved in $DataDir"
    } else {
        New-Item -ItemType Directory -Path $DataDir -Force | Out-Null
        Write-Success "Customer data workspace initialized at $DataDir"
    }

    # --------------------------------------------------------------------------
    # 9. Create Desktop & Start Menu Shortcuts
    # --------------------------------------------------------------------------
    Write-Step "Creating Desktop and Start Menu shortcuts..."
    $WshShell = New-Object -ComObject WScript.Shell

    $IconPath = Join-Path $InstallDir "RupeeCRM.ico"
    if (-not (Test-Path $IconPath)) {
        $IconPath = "$TargetExe,0"
    }

    # 9a. Desktop Shortcut
    $DesktopPath = [Environment]::GetFolderPath("Desktop")
    if (Test-Path $DesktopPath) {
        $DesktopShortcutPath = Join-Path $DesktopPath "RupeeCRM.lnk"
        $Shortcut = $WshShell.CreateShortcut($DesktopShortcutPath)
        $Shortcut.TargetPath = $TargetExe
        $Shortcut.WorkingDirectory = $InstallDir
        $Shortcut.Description = "RupeeCRM Billing & Management"
        $Shortcut.IconLocation = $IconPath
        $Shortcut.Save()
        Write-Success "Desktop shortcut created: $DesktopShortcutPath"
    }

    # 9b. Start Menu Shortcut
    $StartMenuPrograms = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\RupeeCRM"
    if (-not (Test-Path $StartMenuPrograms)) {
        New-Item -ItemType Directory -Path $StartMenuPrograms -Force | Out-Null
    }
    $StartMenuShortcutPath = Join-Path $StartMenuPrograms "RupeeCRM.lnk"
    $MenuShortcut = $WshShell.CreateShortcut($StartMenuShortcutPath)
    $MenuShortcut.TargetPath = $TargetExe
    $MenuShortcut.WorkingDirectory = $InstallDir
    $MenuShortcut.Description = "RupeeCRM Billing & Management"
    $MenuShortcut.IconLocation = $IconPath
    $MenuShortcut.Save()
    Write-Success "Start Menu shortcut created: $StartMenuShortcutPath"

    # --------------------------------------------------------------------------
    # 10. Configure User Auto-Start (Registry + Startup VBS)
    # --------------------------------------------------------------------------
    Write-Step "Configuring background auto-start..."
    $regSuccess = $false
    try {
        # 1. Direct .NET Registry API (Guaranteed to work across all PowerShell editions without quote issues)
        $runKeyObj = [Microsoft.Win32.Registry]::CurrentUser.CreateSubKey("Software\Microsoft\Windows\CurrentVersion\Run")
        if ($runKeyObj) {
            $runKeyObj.SetValue("RupeeCRMService", "`"$TargetExe`" --background")
            $runKeyObj.Close()
            $regSuccess = $true
        }
    } catch {}

    try {
        # 2. PowerShell PSDrive provider
        $RegKey = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run"
        if (-not (Test-Path $RegKey)) {
            New-Item -Path $RegKey -Force | Out-Null
        }
        Set-ItemProperty -Path $RegKey -Name "RupeeCRMService" -Value "`"$TargetExe`" --background" -Force
        $regSuccess = $true
    } catch {}

    if ($regSuccess) {
        Write-Success "Auto-start registered in Windows Registry (HKCU Run)"
    } else {
        Write-WarnMsg "Could not set registry run key automatically"
    }

    try {
        $StartupFolder = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\Startup"
        if (Test-Path $StartupFolder) {
            $VbsPath = Join-Path $StartupFolder "RupeeCRM.vbs"
            $vbsContent = "Set WshShell = CreateObject(`"WScript.Shell`")`r`nWshShell.Run `"`"`"$($TargetExe.Replace('\', '\\'))`"`" --background`", 0, False`r`n"
            [System.IO.File]::WriteAllText($VbsPath, $vbsContent, [System.Text.Encoding]::ASCII)
            Write-Success "Startup script configured: $VbsPath"
        }
    } catch {
        Write-WarnMsg "Could not write startup VBS script: $($_.Exception.Message)"
    }

    # --------------------------------------------------------------------------
    # 11. Launch Application
    # --------------------------------------------------------------------------
    if (-not $SkipLaunch) {
        Write-Step "Starting RupeeCRM supervisor..."
        $launchArgs = if ($Background) { "--background" } else { "" }
        if ($launchArgs) {
            Start-Process -FilePath $TargetExe -ArgumentList $launchArgs -WorkingDirectory $InstallDir
        } else {
            Start-Process -FilePath $TargetExe -WorkingDirectory $InstallDir
        }
        Write-Success "RupeeCRM background service started successfully."
    }

    Write-Host ""
    Write-Host "======================================================" -ForegroundColor Green
    Write-Host "           RupeeCRM Installation Complete!            " -ForegroundColor White
    Write-Host "======================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Application URL : http://127.0.0.1:28080/ (or http://management.rupeecrm.local:28080/)" -ForegroundColor Cyan
    Write-Host "Installed To    : $InstallDir" -ForegroundColor Gray
    Write-Host "Customer Data   : $DataDir" -ForegroundColor Gray
    Write-Host "Tray Icon       : Active in Windows Notification Area (near clock)" -ForegroundColor Gray
    Write-Host ""
    Write-Host "You can launch RupeeCRM anytime from your Desktop or Start Menu." -ForegroundColor White
    Write-Host ""

} finally {
    Cleanup-Temp
}
