@echo off
setlocal EnableExtensions EnableDelayedExpansion

:: ==============================================================================
:: RupeeCRM — Complete Independent Forensic Uninstall & Factory Reset Utility
:: Standalone Windows Batch File (.BAT)
:: ==============================================================================

title RupeeCRM Complete Uninstallation

set "SCRIPT_VERSION=1.0.0"
set "FORCE_MODE=0"
set "NO_ELEVATION=0"
set "NO_PAUSE=0"
set "DRY_RUN=0"
set "FAILURES_COUNT=0"

:: Parse command line arguments
for %%A in (%*) do (
    if /i "%%A"=="/y" set "FORCE_MODE=1"
    if /i "%%A"=="-y" set "FORCE_MODE=1"
    if /i "%%A"=="/f" set "FORCE_MODE=1"
    if /i "%%A"=="-f" set "FORCE_MODE=1"
    if /i "%%A"=="/force" set "FORCE_MODE=1"
    if /i "%%A"=="--force" set "FORCE_MODE=1"
    if /i "%%A"=="--no-elevation" set "NO_ELEVATION=1"
    if /i "%%A"=="/no-elevation" set "NO_ELEVATION=1"
    if /i "%%A"=="--no-pause" set "NO_PAUSE=1"
    if /i "%%A"=="/no-pause" set "NO_PAUSE=1"
    if /i "%%A"=="--dry-run" set "DRY_RUN=1"
    if /i "%%A"=="/dry-run" set "DRY_RUN=1"
)

:: ==============================================================================
:: 0. ADMINISTRATOR ELEVATION CHECK
:: ==============================================================================

net session >nul 2>&1
if %errorLevel% neq 0 (
    if "%NO_ELEVATION%"=="0" (
        echo ======================================================================
        echo  Administrator privileges required. Requesting elevation (UAC)...
        echo ======================================================================
        echo.
        powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Start-Process cmd.exe -ArgumentList '/c \"\"%~f0\" %*\"' -Verb RunAs" >nul 2>&1
        if %errorLevel% equ 0 (
            exit /b 0
        ) else (
            echo [ERROR] Failed to acquire Administrator privileges via UAC.
            echo Please right-click this .bat file and select 'Run as Administrator'.
            echo.
            if "%NO_PAUSE%"=="0" pause
            exit /b 1
        )
    ) else (
        echo [WARNING] Running without Administrator privileges (--no-elevation specified).
        echo Some system-level services, registry keys, and paths may not be cleanable.
        echo.
    )
)

:: ==============================================================================
:: BANNER & INTERACTIVE CONFIRMATION
:: ==============================================================================

echo.
echo ======================================================================
echo           RUPEECRM COMPLETE UNINSTALL & FACTORY RESET UTILITY         
echo ======================================================================
echo.
echo WARNING: This permanently deletes RupeeCRM customer data, databases,
echo          backups, application binaries, services, shortcuts and configuration.
echo.
echo This utility will clean BOTH current and legacy installations, including:
echo   - Application Binaries  (%%LOCALAPPDATA%%\Programs\RupeeCRM, %%ProgramFiles%%)
echo   - Customer Databases    (%%LOCALAPPDATA%%\RupeeCRM\data\database.mv.db, etc.)
echo   - Automated Backups     (%%LOCALAPPDATA%%\RupeeCRM\backups)
echo   - Logs, Diagnostics     (%%LOCALAPPDATA%%\RupeeCRM\logs, updater staging)
echo   - Windows Services      (RupeeCRM, RupeeCRMService, Billsoft)
echo   - Scheduled Tasks       (RupeeCRM and Billsoft background tasks)
echo   - Desktop & Start Menu  (User and Public shortcuts and folders)
echo   - Windows Autostart     (HKCU and HKLM Run/RunOnce keys)
echo   - Windows Registry      (HKCU and HKLM Software\RupeeCRM and Billsoft)
echo   - MSI Registrations     (Windows Installer product uninstall keys)
echo.

if "%DRY_RUN%"=="1" (
    echo ======================================================================
    echo  DRY RUN MODE ENABLED - NO DESTRUCTIVE CHANGES WILL BE PERFORMED
    echo ======================================================================
    echo.
) else if "%FORCE_MODE%"=="1" (
    echo Force mode active. Proceeding with complete uninstallation...
    echo.
) else (
    echo Type DELETE to continue:
    set /p "USER_CONFIRM="
    echo.
    if /i not "!USER_CONFIRM!"=="DELETE" (
        echo Confirmation did not match. Aborting immediately with ZERO modifications.
        echo.
        if "%NO_PAUSE%"=="0" pause
        exit /b 0
    )
    echo Confirmation accepted. Commencing complete uninstallation...
    echo.
)

:: ==============================================================================
:: SECTION 1: STOP RUNNING APPLICATION / PROCESSES
:: ==============================================================================

echo [1/16] Stopping running RupeeCRM processes and background services...

:: 1. Native RupeeCRM executables
tasklist /fi "imagename eq RupeeCRM.exe" 2>nul | find /i "RupeeCRM.exe" >nul
if %errorLevel% equ 0 (
    echo   Found running process: RupeeCRM.exe
    if "%DRY_RUN%"=="0" (
        taskkill /f /im RupeeCRM.exe >nul 2>&1
        if %errorLevel% equ 0 (
            echo     -^> Successfully terminated RupeeCRM.exe
        ) else (
            echo     -^> [WARNING] Could not terminate RupeeCRM.exe
            set /a FAILURES_COUNT+=1
        )
    ) else (
        echo     -^> [WOULD TERMINATE] RupeeCRM.exe
    )
) else (
    echo   RupeeCRM.exe is not currently running.
)

:: 2. Java processes specifically running RupeeCRM / billsoft
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$dry = ('%DRY_RUN%' -eq '1'); $procs = Get-CimInstance Win32_Process -Filter 'Name = ''java.exe'' or Name = ''javaw.exe''' -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -and ($_.CommandLine -like '*rupeecrm*' -or $_.CommandLine -like '*launcher.jar*' -or $_.CommandLine -like '*RUPEECRM*' -or $_.CommandLine -like '*billsoft*' -or $_.CommandLine -like '*Simple-Billing*') }; if ($procs) { foreach ($p in $procs) { Write-Host ('  Found RupeeCRM backend process: ' + $p.Name + ' (PID: ' + $p.ProcessId + ')'); if (-not $dry) { try { Stop-Process -Id $p.ProcessId -Force -ErrorAction Stop; Write-Host ('    -> Successfully terminated PID ' + $p.ProcessId); } catch { Write-Host ('    -> [WARNING] Failed to terminate PID ' + $p.ProcessId + ': ' + $_.Exception.Message); exit 1; } } else { Write-Host ('    -> [WOULD TERMINATE] PID ' + $p.ProcessId); } } } else { Write-Host '  No RupeeCRM Java backend processes detected.'; }"
if %errorLevel% neq 0 set /a FAILURES_COUNT+=1

:: Wait briefly for file handles to be released
timeout /t 2 /nobreak >nul 2>&1

:: ==============================================================================
:: SECTION 2: WINDOWS SERVICES / BACKGROUND COMPONENTS
:: ==============================================================================

echo.
echo [2/16] Checking for and removing RupeeCRM Windows services...

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$dry = ('%DRY_RUN%' -eq '1'); $svcNames = @('RupeeCRM', 'RupeeCRMService', 'RupeeCRM Supervisor', 'RupeeCRMSupervisor', 'Billsoft', 'BillsoftService', 'Simple-Billing'); $svcs = Get-Service -ErrorAction SilentlyContinue | Where-Object { $name = $_.Name; $disp = $_.DisplayName; ($svcNames -contains $name) -or ($name -like '*RupeeCRM*') -or ($disp -like '*RupeeCRM*') -or ($name -like '*Billsoft*') -or ($disp -like '*Billsoft*') }; if ($svcs) { foreach ($s in $svcs) { Write-Host ('  Found Windows Service: ' + $s.Name + ' (' + $s.DisplayName + ')'); if (-not $dry) { try { Stop-Service -Name $s.Name -Force -ErrorAction SilentlyContinue; Start-Sleep -Milliseconds 400; & sc.exe delete $s.Name | Out-Null; Write-Host ('    -> Service stopped and deleted: ' + $s.Name); } catch { Write-Host ('    -> [WARNING] Failed to remove service ' + $s.Name + ': ' + $_.Exception.Message); exit 1; } } else { Write-Host ('    -> [WOULD REMOVE SERVICE] ' + $s.Name); } } } else { Write-Host '  No RupeeCRM Windows services found.'; }"
if %errorLevel% neq 0 set /a FAILURES_COUNT+=1

:: ==============================================================================
:: SECTION 3: WINDOWS SCHEDULED TASKS
:: ==============================================================================

echo.
echo [3/16] Checking for RupeeCRM scheduled tasks...

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$dry = ('%DRY_RUN%' -eq '1'); if (Get-Command Get-ScheduledTask -ErrorAction SilentlyContinue) { $tasks = Get-ScheduledTask -ErrorAction SilentlyContinue | Where-Object { $_.TaskName -like '*RupeeCRM*' -or $_.TaskName -like '*Billsoft*' -or $_.TaskName -like '*Simple-Billing*' }; if ($tasks) { foreach ($t in $tasks) { Write-Host ('  Found Scheduled Task: ' + $t.TaskName); if (-not $dry) { try { Unregister-ScheduledTask -TaskName $t.TaskName -Confirm:$false -ErrorAction Stop; Write-Host ('    -> Unregistered scheduled task: ' + $t.TaskName); } catch { Write-Host ('    -> [WARNING] Failed to unregister task ' + $t.TaskName + ': ' + $_.Exception.Message); exit 1; } } else { Write-Host ('    -> [WOULD UNREGISTER TASK] ' + $t.TaskName); } } } else { Write-Host '  No RupeeCRM scheduled tasks found.'; } } else { Write-Host '  Scheduled task management cmdlet not available, skipping.'; }"
if %errorLevel% neq 0 set /a FAILURES_COUNT+=1

:: ==============================================================================
:: SECTION 4: WINDOWS UNINSTALL REGISTRATION & MSI PACKAGES
:: ==============================================================================

echo.
echo [4/16] Checking for Windows Installer and ARP uninstall registrations...

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$dry = ('%DRY_RUN%' -eq '1'); $roots = @('HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall', 'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall', 'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall'); $found = $false; foreach ($r in $roots) { if (Test-Path $r) { Get-ChildItem -Path $r -ErrorAction SilentlyContinue | ForEach-Object { $dn = $_.GetValue('DisplayName'); $pub = $_.GetValue('Publisher'); $loc = $_.GetValue('InstallLocation'); $uStr = $_.GetValue('UninstallString'); $pCode = $_.PSChildName; if (($dn -and ($dn -like '*RupeeCRM*' -or $dn -like '*Billsoft*')) -or ($pub -and ($pub -eq 'RupeeCRM' -or $pub -eq 'Billsoft')) -or ($loc -and ($loc -like '*RupeeCRM*' -or $loc -like '*Billsoft*')) -or ($uStr -and ($uStr -like '*RupeeCRM*' -or $uStr -like '*Billsoft*'))) { $found = $true; Write-Host ('  Found Uninstall Entry: ''' + $dn + ''' (Key: ' + $pCode + ')'); if (-not $dry) { if ($pCode -match '^\{[A-Fa-f0-9\-]+\}$') { Write-Host ('    Executing msiexec.exe /x ' + $pCode + ' /qn /norestart ...'); $uProc = Start-Process 'msiexec.exe' -ArgumentList ('/x ' + $pCode + ' /qn /norestart') -Wait -PassThru -NoNewWindow; Write-Host ('    MSI uninstaller finished with exit code ' + $uProc.ExitCode); } Remove-Item -Path $_.PSPath -Recurse -Force -ErrorAction SilentlyContinue; Write-Host ('    -> Removed uninstall registry key: ' + $_.PSPath); } else { Write-Host ('    -> [WOULD REMOVE UNINSTALL REGISTRATION] ' + $_.PSPath); } } } } }; if (-not $found) { Write-Host '  No RupeeCRM uninstall registrations found in registry.'; }"
if %errorLevel% neq 0 set /a FAILURES_COUNT+=1

:: ==============================================================================
:: SECTION 5 & 6: CURRENT & LEGACY INSTALLATION DIRECTORIES
:: ==============================================================================

echo.
echo [5/16] Removing RupeeCRM and legacy application installation directories...

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$dry = ('%DRY_RUN%' -eq '1'); $dirs = @(\"$env:LOCALAPPDATA\Programs\RupeeCRM\", \"$env:LOCALAPPDATA\Programs\Billsoft\", \"$env:ProgramFiles\RupeeCRM\", \"$env:ProgramFiles\Billsoft\", \"${env:ProgramFiles(x86)}\RupeeCRM\", \"${env:ProgramFiles(x86)}\Billsoft\"); if (Test-Path \"$env:SystemDrive\Users\") { Get-ChildItem -Path \"$env:SystemDrive\Users\" -Directory -ErrorAction SilentlyContinue | ForEach-Object { $dirs += \"$($_.FullName)\AppData\Local\Programs\RupeeCRM\"; $dirs += \"$($_.FullName)\AppData\Local\Programs\Billsoft\"; } }; $uniqueDirs = $dirs | Where-Object { $_ -and (Test-Path $_) } | Select-Object -Unique; if ($uniqueDirs) { foreach ($d in $uniqueDirs) { Write-Host ('  Found Installation Directory: ' + $d); if (-not $dry) { $removed = $false; for ($i = 1; $i -le 5; $i++) { try { Get-ChildItem -Path $d -Recurse -Force -ErrorAction SilentlyContinue | ForEach-Object { $_.Attributes = 'Normal' }; Remove-Item -Path $d -Recurse -Force -ErrorAction Stop; $removed = $true; break; } catch { Start-Sleep -Milliseconds 400; } } if ($removed -or -not (Test-Path $d)) { Write-Host ('    -> [OK] Successfully deleted: ' + $d); } else { Write-Host ('    -> [ERROR] Failed to delete installation directory: ' + $d); exit 1; } } else { Write-Host ('    -> [WOULD DELETE] ' + $d); } } } else { Write-Host '  No active installation directories found.'; }"
if %errorLevel% neq 0 set /a FAILURES_COUNT+=1

:: ==============================================================================
:: SECTION 7: CUSTOMER DATABASE & APPLICATION DATA
:: ==============================================================================

echo.
echo [6/16] Removing customer databases and application data...

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$dry = ('%DRY_RUN%' -eq '1'); $dataDirs = @(\"$env:LOCALAPPDATA\RupeeCRM\", \"$env:LOCALAPPDATA\Billsoft\", \"$env:APPDATA\RupeeCRM\", \"$env:APPDATA\Billsoft\", \"$env:ProgramData\RupeeCRM\", \"$env:ProgramData\Billsoft\", \"$env:USERPROFILE\.rupeecrm\", \"$env:USERPROFILE\.billsoft\"); if (Test-Path \"$env:SystemDrive\Users\") { Get-ChildItem -Path \"$env:SystemDrive\Users\" -Directory -ErrorAction SilentlyContinue | ForEach-Object { $dataDirs += \"$($_.FullName)\AppData\Local\RupeeCRM\"; $dataDirs += \"$($_.FullName)\AppData\Local\Billsoft\"; $dataDirs += \"$($_.FullName)\AppData\Roaming\RupeeCRM\"; $dataDirs += \"$($_.FullName)\AppData\Roaming\Billsoft\"; $dataDirs += \"$($_.FullName)\.rupeecrm\"; $dataDirs += \"$($_.FullName)\.billsoft\"; } }; $uniqueDataDirs = $dataDirs | Where-Object { $_ -and (Test-Path $_) } | Select-Object -Unique; if ($uniqueDataDirs) { foreach ($d in $uniqueDataDirs) { Write-Host ('  Found Data Directory: ' + $d); if (-not $dry) { $removed = $false; for ($i = 1; $i -le 5; $i++) { try { Get-ChildItem -Path $d -Recurse -Force -ErrorAction SilentlyContinue | ForEach-Object { $_.Attributes = 'Normal' }; Remove-Item -Path $d -Recurse -Force -ErrorAction Stop; $removed = $true; break; } catch { Start-Sleep -Milliseconds 400; } } if ($removed -or -not (Test-Path $d)) { Write-Host ('    -> [OK] Successfully purged customer data directory: ' + $d); } else { Write-Host ('    -> [ERROR] Could not delete data directory: ' + $d); exit 1; } } else { Write-Host ('    -> [WOULD PURGE DATA] ' + $d); } } } else { Write-Host '  No customer data directories found.'; }"
if %errorLevel% neq 0 set /a FAILURES_COUNT+=1

:: ==============================================================================
:: SECTION 8: BACKUPS
:: ==============================================================================

echo.
echo [7/16] Removing backup archives...

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$dry = ('%DRY_RUN%' -eq '1'); $backupPaths = @(\"$env:LOCALAPPDATA\RupeeCRM\backups\", \"$env:LOCALAPPDATA\Billsoft\backups\", \"$env:USERPROFILE\RupeeCRM_Backups\", \"$env:USERPROFILE\Billsoft_Backups\"); foreach ($b in ($backupPaths | Where-Object { Test-Path $_ })) { Write-Host ('  Found Backup Directory: ' + $b); if (-not $dry) { try { Remove-Item -Path $b -Recurse -Force -ErrorAction Stop; Write-Host ('    -> [OK] Deleted backup directory: ' + $b); } catch { Write-Host ('    -> [ERROR] Failed to delete backup directory: ' + $b); exit 1; } } else { Write-Host ('    -> [WOULD DELETE] ' + $b); } }"
if %errorLevel% neq 0 set /a FAILURES_COUNT+=1

:: ==============================================================================
:: SECTION 9: DESKTOP SHORTCUTS
:: ==============================================================================

echo.
echo [8/16] Removing desktop shortcuts...

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$dry = ('%DRY_RUN%' -eq '1'); $desktopDirs = @([Environment]::GetFolderPath('Desktop'), [Environment]::GetFolderPath('CommonDesktopDirectory')); if (Test-Path \"$env:SystemDrive\Users\") { Get-ChildItem -Path \"$env:SystemDrive\Users\" -Directory -ErrorAction SilentlyContinue | ForEach-Object { $desktopDirs += \"$($_.FullName)\Desktop\"; } }; $foundAny = $false; foreach ($d in ($desktopDirs | Where-Object { $_ -and (Test-Path $_) } | Select-Object -Unique)) { Get-ChildItem -Path $d -Include '*RupeeCRM*.lnk', '*Billsoft*.lnk', '*RupeeCRM*.url', '*Billsoft*.url' -File -ErrorAction SilentlyContinue | ForEach-Object { $foundAny = $true; Write-Host ('  Found Desktop Shortcut: ' + $_.FullName); if (-not $dry) { Remove-Item -Path $_.FullName -Force -ErrorAction SilentlyContinue; Write-Host ('    -> [OK] Deleted shortcut: ' + $_.FullName); } else { Write-Host ('    -> [WOULD DELETE SHORTCUT] ' + $_.FullName); } } }; if (-not $foundAny) { Write-Host '  No RupeeCRM desktop shortcuts found.'; }"
if %errorLevel% neq 0 set /a FAILURES_COUNT+=1

:: ==============================================================================
:: SECTION 10: START MENU SHORTCUTS & FOLDERS
:: ==============================================================================

echo.
echo [9/16] Removing Start Menu shortcuts and program groups...

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$dry = ('%DRY_RUN%' -eq '1'); $menuDirs = @([Environment]::GetFolderPath('Programs'), [Environment]::GetFolderPath('CommonPrograms')); if (Test-Path \"$env:SystemDrive\Users\") { Get-ChildItem -Path \"$env:SystemDrive\Users\" -Directory -ErrorAction SilentlyContinue | ForEach-Object { $menuDirs += \"$($_.FullName)\AppData\Roaming\Microsoft\Windows\Start Menu\Programs\"; } }; $foundAny = $false; foreach ($m in ($menuDirs | Where-Object { $_ -and (Test-Path $_) } | Select-Object -Unique)) { Get-ChildItem -Path $m -Filter '*RupeeCRM*.lnk' -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object { $foundAny = $true; Write-Host ('  Found Start Menu Shortcut: ' + $_.FullName); if (-not $dry) { Remove-Item -Path $_.FullName -Force -ErrorAction SilentlyContinue; Write-Host ('    -> [OK] Deleted shortcut: ' + $_.FullName); } else { Write-Host ('    -> [WOULD DELETE SHORTCUT] ' + $_.FullName); } }; Get-ChildItem -Path $m -Filter '*Billsoft*.lnk' -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object { $foundAny = $true; Write-Host ('  Found Start Menu Shortcut: ' + $_.FullName); if (-not $dry) { Remove-Item -Path $_.FullName -Force -ErrorAction SilentlyContinue; Write-Host ('    -> [OK] Deleted shortcut: ' + $_.FullName); } else { Write-Host ('    -> [WOULD DELETE SHORTCUT] ' + $_.FullName); } }; Get-ChildItem -Path $m -Include 'RupeeCRM', 'Billsoft' -Directory -Recurse -ErrorAction SilentlyContinue | ForEach-Object { $foundAny = $true; Write-Host ('  Found Start Menu Folder: ' + $_.FullName); if (-not $dry) { Remove-Item -Path $_.FullName -Recurse -Force -ErrorAction SilentlyContinue; Write-Host ('    -> [OK] Deleted folder: ' + $_.FullName); } else { Write-Host ('    -> [WOULD DELETE FOLDER] ' + $_.FullName); } }; }; if (-not $foundAny) { Write-Host '  No RupeeCRM Start Menu shortcuts or folders found.'; }"
if %errorLevel% neq 0 set /a FAILURES_COUNT+=1

:: ==============================================================================
:: SECTION 11: STARTUP / AUTOSTART ITEMS
:: ==============================================================================

echo.
echo [10/16] Removing autostart and startup entries...

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$dry = ('%DRY_RUN%' -eq '1'); $runKeys = @('HKCU:\Software\Microsoft\Windows\CurrentVersion\Run', 'HKCU:\Software\Microsoft\Windows\CurrentVersion\RunOnce', 'HKLM:\Software\Microsoft\Windows\CurrentVersion\Run', 'HKLM:\Software\Microsoft\Windows\CurrentVersion\RunOnce', 'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Run'); $valNames = @('RupeeCRM', 'Billsoft', 'Simple-Billing', 'RupeeCRMSupervisor'); $foundAny = $false; foreach ($rk in $runKeys) { if (Test-Path $rk) { $regItem = Get-Item -Path $rk -ErrorAction SilentlyContinue; if ($regItem) { foreach ($v in $valNames) { $val = $regItem.GetValue($v); if ($val) { $foundAny = $true; Write-Host ('  Found Startup Entry: ' + $rk + '\' + $v + ' -> ' + $val); if (-not $dry) { Remove-ItemProperty -Path $rk -Name $v -Force -ErrorAction SilentlyContinue; Write-Host ('    -> [OK] Removed autostart registry value: ' + $v); } else { Write-Host ('    -> [WOULD REMOVE AUTOSTART ENTRY] ' + $v); } } } } } }; $startupDirs = @([Environment]::GetFolderPath('Startup'), [Environment]::GetFolderPath('CommonStartup')); foreach ($sd in ($startupDirs | Where-Object { $_ -and (Test-Path $_) })) { Get-ChildItem -Path $sd -Include '*RupeeCRM*.lnk', '*Billsoft*.lnk' -File -ErrorAction SilentlyContinue | ForEach-Object { $foundAny = $true; Write-Host ('  Found Startup Folder Shortcut: ' + $_.FullName); if (-not $dry) { Remove-Item -Path $_.FullName -Force -ErrorAction SilentlyContinue; Write-Host ('    -> [OK] Removed startup shortcut: ' + $_.FullName); } else { Write-Host ('    -> [WOULD REMOVE SHORTCUT] ' + $_.FullName); } } }; if (-not $foundAny) { Write-Host '  No RupeeCRM autostart entries found.'; }"
if %errorLevel% neq 0 set /a FAILURES_COUNT+=1

:: ==============================================================================
:: SECTION 12: WINDOWS REGISTRY CLEANUP
:: ==============================================================================

echo.
echo [11/16] Cleaning application registry keys...

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$dry = ('%DRY_RUN%' -eq '1'); $appKeys = @('HKCU:\Software\RupeeCRM', 'HKCU:\Software\Billsoft', 'HKLM:\Software\RupeeCRM', 'HKLM:\Software\Billsoft', 'HKLM:\Software\WOW6432Node\RupeeCRM', 'HKLM:\Software\WOW6432Node\Billsoft'); $foundAny = $false; foreach ($k in $appKeys) { if (Test-Path $k) { $foundAny = $true; Write-Host ('  Found Registry Key: ' + $k); if (-not $dry) { Remove-Item -Path $k -Recurse -Force -ErrorAction SilentlyContinue; Write-Host ('    -> [OK] Deleted registry key: ' + $k); } else { Write-Host ('    -> [WOULD DELETE KEY] ' + $k); } } }; if (-not $foundAny) { Write-Host '  No RupeeCRM application registry keys found.'; }"
if %errorLevel% neq 0 set /a FAILURES_COUNT+=1

:: ==============================================================================
:: SECTION 13: ENVIRONMENT VARIABLES
:: ==============================================================================

echo.
echo [12/16] Cleaning RupeeCRM environment variables...

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$dry = ('%DRY_RUN%' -eq '1'); $vars = @('RUPEECRM_DATA_DIR', 'RUPEECRM_BASE_DIR', 'BILLSOFT_DATA_DIR'); $foundAny = $false; foreach ($target in @('User', 'Machine')) { foreach ($v in $vars) { $val = [Environment]::GetEnvironmentVariable($v, $target); if ($val) { $foundAny = $true; Write-Host ('  Found Environment Variable: [' + $target + '] ' + $v + ' = ' + $val); if (-not $dry) { [Environment]::SetEnvironmentVariable($v, $null, $target); Write-Host ('    -> [OK] Cleared environment variable: ' + $v); } else { Write-Host ('    -> [WOULD CLEAR VARIABLE] ' + $v); } } } }; if (-not $foundAny) { Write-Host '  No RupeeCRM environment variables configured.'; }"
if %errorLevel% neq 0 set /a FAILURES_COUNT+=1

:: ==============================================================================
:: SECTION 14: TEMP / CACHE / UPDATE RESIDUE
:: ==============================================================================

echo.
echo [13/16] Cleaning temporary files, cache, and update residue...

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$dry = ('%DRY_RUN%' -eq '1'); $tempRoots = @($env:TEMP, \"$env:LOCALAPPDATA\Temp\"); if (Test-Path \"$env:SystemDrive\Users\") { Get-ChildItem -Path \"$env:SystemDrive\Users\" -Directory -ErrorAction SilentlyContinue | ForEach-Object { $tempRoots += \"$($_.FullName)\AppData\Local\Temp\"; } }; $foundAny = $false; foreach ($tr in ($tempRoots | Where-Object { $_ -and (Test-Path $_) } | Select-Object -Unique)) { Get-ChildItem -Path $tr -ErrorAction SilentlyContinue | Where-Object { $_.Name -like '*rupeecrm*' -or $_.Name -like '*billsoft*' -or $_.Name -like '*jpackage*RupeeCRM*' } | ForEach-Object { $foundAny = $true; Write-Host ('  Found Temporary Item: ' + $_.FullName); if (-not $dry) { Remove-Item -Path $_.FullName -Recurse -Force -ErrorAction SilentlyContinue; Write-Host ('    -> [OK] Deleted temp artifact: ' + $_.FullName); } else { Write-Host ('    -> [WOULD DELETE TEMP ITEM] ' + $_.FullName); } } }; if (-not $foundAny) { Write-Host '  No RupeeCRM temporary files found.'; }"
if %errorLevel% neq 0 set /a FAILURES_COUNT+=1

:: ==============================================================================
:: SECTION 15: INSTALLER CACHE / STAGING
:: ==============================================================================

echo.
echo [14/16] Cleaning staging and installer cache...

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$dry = ('%DRY_RUN%' -eq '1'); $stagingDirs = @(\"$env:LOCALAPPDATA\RupeeCRM\staging\", \"$env:LOCALAPPDATA\RupeeCRM\update\", \"$env:LOCALAPPDATA\Billsoft\staging\", \"$env:LOCALAPPDATA\Billsoft\update\"); foreach ($st in ($stagingDirs | Where-Object { Test-Path $_ })) { Write-Host ('  Found Staging Directory: ' + $st); if (-not $dry) { Remove-Item -Path $st -Recurse -Force -ErrorAction SilentlyContinue; Write-Host ('    -> [OK] Deleted staging directory: ' + $st); } else { Write-Host ('    -> [WOULD DELETE STAGING] ' + $st); } }"
if %errorLevel% neq 0 set /a FAILURES_COUNT+=1

:: ==============================================================================
:: SECTION 16: FILE ASSOCIATIONS
:: ==============================================================================

echo.
echo [15/16] Cleaning file associations...

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$dry = ('%DRY_RUN%' -eq '1'); $classKeys = @('HKCU:\Software\Classes\.rupeecrm', 'HKCU:\Software\Classes\RupeeCRM.Document', 'HKLM:\Software\Classes\.rupeecrm', 'HKLM:\Software\Classes\RupeeCRM.Document'); foreach ($ck in $classKeys) { if (Test-Path $ck) { Write-Host ('  Found File Association Key: ' + $ck); if (-not $dry) { Remove-Item -Path $ck -Recurse -Force -ErrorAction SilentlyContinue; Write-Host ('    -> [OK] Removed file association key: ' + $ck); } else { Write-Host ('    -> [WOULD REMOVE KEY] ' + $ck); } } }"
if %errorLevel% neq 0 set /a FAILURES_COUNT+=1

:: ==============================================================================
:: SECTION 17: FINAL VERIFICATION PASS & AUDIT
:: ==============================================================================

echo.
echo [16/16] Performing post-uninstall forensic verification audit...
echo.

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$dry = ('%DRY_RUN%' -eq '1'); if ($dry) { Write-Host '==================================================' -ForegroundColor Cyan; Write-Host ' CLEANUP VERIFICATION (DRY RUN)' -ForegroundColor Cyan; Write-Host '==================================================' -ForegroundColor Cyan; Write-Host '[INFO] Dry Run complete. No files or settings were modified.'; exit 0; }; $issues = [System.Collections.Generic.List[string]]::new(); Write-Host '==================================================' -ForegroundColor Cyan; Write-Host ' CLEANUP VERIFICATION' -ForegroundColor Cyan; Write-Host '==================================================' -ForegroundColor Cyan; $procs = Get-Process -ErrorAction SilentlyContinue | Where-Object { ($_.ProcessName -like '*RupeeCRM*') -or ($_.Path -and $_.Path -like '*RupeeCRM*') }; $javaProcs = Get-CimInstance Win32_Process -Filter 'Name = ''java.exe'' or Name = ''javaw.exe''' -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -and ($_.CommandLine -like '*rupeecrm*' -or $_.CommandLine -like '*launcher.jar*' -or $_.CommandLine -like '*RUPEECRM*' -or $_.CommandLine -like '*billsoft*') }; if ($procs -or $javaProcs) { Write-Host '[FAIL] RupeeCRM processes: still running' -ForegroundColor Red; $issues.Add('RupeeCRM processes are still running'); } else { Write-Host '[OK] RupeeCRM processes: none' -ForegroundColor Green; }; $svcs = Get-Service -ErrorAction SilentlyContinue | Where-Object { $_.Name -like '*RupeeCRM*' -or $_.DisplayName -like '*RupeeCRM*' -or $_.Name -like '*Billsoft*' }; if ($svcs) { Write-Host '[FAIL] RupeeCRM services: still present' -ForegroundColor Red; $issues.Add('RupeeCRM Windows service remains installed'); } else { Write-Host '[OK] RupeeCRM services: none' -ForegroundColor Green; }; $installDir = \"$env:LOCALAPPDATA\Programs\RupeeCRM\"; $progFiles = \"$env:ProgramFiles\RupeeCRM\"; if ((Test-Path $installDir) -or (Test-Path $progFiles)) { Write-Host '[FAIL] Installation directory: still present' -ForegroundColor Red; $issues.Add('Installation directory still exists'); } else { Write-Host '[OK] Installation directory: removed' -ForegroundColor Green; }; $dataDb = \"$env:LOCALAPPDATA\RupeeCRM\data\database.mv.db\"; if (Test-Path $dataDb) { Write-Host '[FAIL] Customer data: still present' -ForegroundColor Red; $issues.Add('Customer database still exists at ' + $dataDb); } else { Write-Host '[OK] Customer data: removed' -ForegroundColor Green; }; $legacyDb = \"$env:LOCALAPPDATA\Billsoft\data\billsoft_database.mv.db\"; if (Test-Path $legacyDb) { Write-Host '[FAIL] Legacy database: still present' -ForegroundColor Red; $issues.Add('Legacy database still exists at ' + $legacyDb); } else { Write-Host '[OK] Legacy database: removed' -ForegroundColor Green; }; $desktopLnk1 = Join-Path ([Environment]::GetFolderPath('Desktop')) 'RupeeCRM.lnk'; $desktopLnk2 = Join-Path ([Environment]::GetFolderPath('CommonDesktopDirectory')) 'RupeeCRM.lnk'; if ((Test-Path $desktopLnk1) -or (Test-Path $desktopLnk2)) { Write-Host '[FAIL] Desktop shortcut: still present' -ForegroundColor Red; $issues.Add('Desktop shortcut still exists'); } else { Write-Host '[OK] Desktop shortcut: removed' -ForegroundColor Green; }; $menuLnk1 = Join-Path ([Environment]::GetFolderPath('Programs')) 'RupeeCRM.lnk'; $menuLnk2 = Join-Path ([Environment]::GetFolderPath('CommonPrograms')) 'RupeeCRM.lnk'; if ((Test-Path $menuLnk1) -or (Test-Path $menuLnk2)) { Write-Host '[FAIL] Start Menu shortcut: still present' -ForegroundColor Red; $issues.Add('Start Menu shortcut still exists'); } else { Write-Host '[OK] Start Menu shortcut: removed' -ForegroundColor Green; }; $runVal = (Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Run' -Name 'RupeeCRM' -ErrorAction SilentlyContinue).RupeeCRM; if ($runVal) { Write-Host '[FAIL] Startup registration: still present' -ForegroundColor Red; $issues.Add('Startup Run registry entry still exists'); } else { Write-Host '[OK] Startup registration: removed' -ForegroundColor Green; }; if (Get-Command Get-ScheduledTask -ErrorAction SilentlyContinue) { $tasks = Get-ScheduledTask -ErrorAction SilentlyContinue | Where-Object { $_.TaskName -like '*RupeeCRM*' }; if ($tasks) { Write-Host '[FAIL] Scheduled tasks: still present' -ForegroundColor Red; $issues.Add('Scheduled tasks still exist'); } else { Write-Host '[OK] Scheduled tasks: none' -ForegroundColor Green; } } else { Write-Host '[OK] Scheduled tasks: none' -ForegroundColor Green; }; $uninstKeys = @('HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall', 'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall'); $uninstFound = $false; foreach ($uk in $uninstKeys) { if (Test-Path $uk) { Get-ChildItem -Path $uk -ErrorAction SilentlyContinue | ForEach-Object { $dn = $_.GetValue('DisplayName'); if ($dn -and $dn -like '*RupeeCRM*') { $uninstFound = $true; } } } }; if ($uninstFound) { Write-Host '[FAIL] Registry uninstall entry: still present' -ForegroundColor Red; $issues.Add('Windows Uninstall registry entry still exists'); } else { Write-Host '[OK] Registry uninstall entry: removed' -ForegroundColor Green; }; $rupeeBase = \"$env:LOCALAPPDATA\RupeeCRM\"; if (Test-Path $rupeeBase) { Write-Host '[FAIL] Application/data directories: still present' -ForegroundColor Red; $issues.Add('Application/data directory still exists at ' + $rupeeBase); } else { Write-Host '[OK] Application/data directories: removed' -ForegroundColor Green; }; Write-Host ''; Write-Host '==================================================' -ForegroundColor Cyan; Write-Host ' RESULT' -ForegroundColor Cyan; Write-Host '==================================================' -ForegroundColor Cyan; if ($issues.Count -eq 0) { Write-Host 'COMPLETE UNINSTALL SUCCESSFUL' -ForegroundColor Green; Write-Host ''; Write-Host 'All RupeeCRM application files, customer databases,'; Write-Host 'backups, services, shortcuts, scheduled tasks,'; Write-Host 'and registry entries have been permanently removed.'; exit 0; } else { Write-Host 'PARTIAL CLEANUP' -ForegroundColor Red; Write-Host ''; Write-Host 'The following items could not be removed:'; $idx = 1; foreach ($iss in $issues) { Write-Host ('  ' + $idx + '. ' + $iss) -ForegroundColor Red; $idx++; }; exit 1; }"

set "FINAL_STATUS=%errorLevel%"

echo.
echo ======================================================================

if "%FINAL_STATUS%"=="0" (
    if "%DRY_RUN%"=="1" (
        echo Dry Run completed. Exit code: 0
    ) else (
        echo Uninstallation process completed successfully. Exit code: 0
    )
) else (
    echo Uninstallation completed with warnings or partial residue. Exit code: %FINAL_STATUS%
)

echo ======================================================================
echo.

if "%NO_PAUSE%"=="0" (
    echo Press any key to exit . . .
    pause >nul
)

exit /b %FINAL_STATUS%
