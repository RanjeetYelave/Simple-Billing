@echo off
setlocal EnableDelayedExpansion
title RupeeCRM - Full System Cleanup and Uninstaller
color 0C

echo ================================================================
echo               RUPEECRM COMPLETE SYSTEM PURGE
echo ================================================================
echo  WARNING: This tool will completely and permanently remove:
echo    - All running RupeeCRM / Billsoft / Java background services
echo    - All database files (database.mv.db) and historical backups
echo    - All invoices, products, customers, and business data
echo    - All WebView2 / browser local storage and cache files
echo    - Windows startup registry entries and startup scripts
echo    - Desktop and Start Menu shortcuts
echo    - Application binaries and configuration files
echo ================================================================
echo.
set "FORCE=0"
if /i "%~1"=="/y" set "FORCE=1"
if /i "%~1"=="-y" set "FORCE=1"
if /i "%~1"=="/quiet" set "FORCE=1"
if /i "%~1"=="-quiet" set "FORCE=1"
if /i "%~1"=="--quiet" set "FORCE=1"

if "!FORCE!"=="0" (
    set /p CONFIRM="Are you absolutely sure you want to delete EVERYTHING? (Type YES to confirm): "
    if /i not "!CONFIRM!"=="YES" (
        echo.
        echo Operation cancelled by user. No files were removed.
        echo.
        pause
        exit /b 0
    )
)

echo.
echo [1/6] Forcefully stopping all running RupeeCRM, Billsoft, and JVM processes...
taskkill /F /IM RupeeCRM.exe >nul 2>&1
taskkill /F /IM Billsoft.exe >nul 2>&1
taskkill /F /IM javaw.exe >nul 2>&1
taskkill /F /IM java.exe >nul 2>&1
taskkill /F /IM msedgewebview2.exe >nul 2>&1

:: PowerShell fallback process killer matching by process name or binary path
powershell -NoProfile -Command "Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -match '^(RupeeCRM|Billsoft|java|javaw)$' -or ($_.Path -and ($_.Path -like '*RupeeCRM*' -or $_.Path -like '*Billsoft*' -or $_.Path -like '*SimpleBilling*')) } | Stop-Process -Force -ErrorAction SilentlyContinue" >nul 2>&1

:: Allow file locks to be completely released by OS
timeout /t 2 /nobreak >nul 2>&1

echo [2/6] Removing Windows auto-start registrations...
reg delete "HKCU\Software\Microsoft\Windows\CurrentVersion\Run" /v "RupeeCRMService" /f >nul 2>&1
reg delete "HKLM\Software\Microsoft\Windows\CurrentVersion\Run" /v "RupeeCRMService" /f >nul 2>&1
reg delete "HKCU\Software\Microsoft\Windows\CurrentVersion\Run" /v "BillsoftService" /f >nul 2>&1
reg delete "HKLM\Software\Microsoft\Windows\CurrentVersion\Run" /v "BillsoftService" /f >nul 2>&1

reg delete "HKCU\Software\RupeeCRM" /f >nul 2>&1
reg delete "HKCU\Software\SimpleBilling" /f >nul 2>&1
reg delete "HKCU\Software\Billsoft" /f >nul 2>&1
reg delete "HKCU\Software\Microsoft\Windows\CurrentVersion\Uninstall\RupeeCRM" /f >nul 2>&1
reg delete "HKCU\Software\Microsoft\Windows\CurrentVersion\Uninstall\Billsoft" /f >nul 2>&1
reg delete "HKLM\Software\Microsoft\Windows\CurrentVersion\Uninstall\RupeeCRM" /f >nul 2>&1
reg delete "HKLM\Software\Microsoft\Windows\CurrentVersion\Uninstall\Billsoft" /f >nul 2>&1

if exist "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\RupeeCRM.vbs" (
    del /f /q /a "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\RupeeCRM.vbs" >nul 2>&1
    echo   - Removed Startup folder script (RupeeCRM.vbs)
)
if exist "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\Billsoft.vbs" (
    del /f /q /a "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\Billsoft.vbs" >nul 2>&1
    echo   - Removed legacy Startup folder script (Billsoft.vbs)
)

echo [3/6] Purging RupeeCRM databases, backups, browser data, and caches...

:: Use PowerShell for robust recursive file removal with lock recovery
powershell -NoProfile -Command "$dirs = @('$env:APPDATA\SimpleBilling', '$env:APPDATA\RupeeCRM', '$env:APPDATA\Billsoft', '$env:LOCALAPPDATA\SimpleBilling', '$env:LOCALAPPDATA\RupeeCRM', '$env:LOCALAPPDATA\Billsoft', '$env:USERPROFILE\.simplebilling', '$env:USERPROFILE\.rupeecrm', '$env:USERPROFILE\.billsoft', '$env:ProgramData\RupeeCRM', '$env:ProgramData\SimpleBilling', 'C:\RupeeCRM', 'C:\SimpleBilling'); foreach ($d in $dirs) { if (Test-Path $d) { for ($i=0; $i -lt 3; $i++) { try { Remove-Item -Path $d -Recurse -Force -ErrorAction SilentlyContinue; if (-not (Test-Path $d)) { break } } catch {} Start-Sleep -Milliseconds 300 } } }" >nul 2>&1

:: Standard CMD fallback deletion for all data locations
if exist "%APPDATA%\SimpleBilling" (
    attrib -r -s -h "%APPDATA%\SimpleBilling\*.*" /s /d >nul 2>&1
    rd /s /q "%APPDATA%\SimpleBilling" >nul 2>&1
    echo   - Purged %APPDATA%\SimpleBilling
)
if exist "%APPDATA%\RupeeCRM" (
    attrib -r -s -h "%APPDATA%\RupeeCRM\*.*" /s /d >nul 2>&1
    rd /s /q "%APPDATA%\RupeeCRM" >nul 2>&1
    echo   - Purged %APPDATA%\RupeeCRM
)
if exist "%LOCALAPPDATA%\RupeeCRM" (
    attrib -r -s -h "%LOCALAPPDATA%\RupeeCRM\*.*" /s /d >nul 2>&1
    rd /s /q "%LOCALAPPDATA%\RupeeCRM" >nul 2>&1
    echo   - Purged %LOCALAPPDATA%\RupeeCRM (WebView2 / Browser Local Storage)
)
if exist "%LOCALAPPDATA%\SimpleBilling" (
    attrib -r -s -h "%LOCALAPPDATA%\SimpleBilling\*.*" /s /d >nul 2>&1
    rd /s /q "%LOCALAPPDATA%\SimpleBilling" >nul 2>&1
    echo   - Purged %LOCALAPPDATA%\SimpleBilling
)
if exist "%USERPROFILE%\.simplebilling" (
    attrib -r -s -h "%USERPROFILE%\.simplebilling\*.*" /s /d >nul 2>&1
    rd /s /q "%USERPROFILE%\.simplebilling" >nul 2>&1
    echo   - Purged %USERPROFILE%\.simplebilling
)
if exist "%USERPROFILE%\.rupeecrm" (
    attrib -r -s -h "%USERPROFILE%\.rupeecrm\*.*" /s /d >nul 2>&1
    rd /s /q "%USERPROFILE%\.rupeecrm" >nul 2>&1
    echo   - Purged %USERPROFILE%\.rupeecrm
)
if exist "%USERPROFILE%\.billsoft" (
    attrib -r -s -h "%USERPROFILE%\.billsoft\*.*" /s /d >nul 2>&1
    rd /s /q "%USERPROFILE%\.billsoft" >nul 2>&1
    echo   - Purged %USERPROFILE%\.billsoft
)
if exist "%ProgramData%\RupeeCRM" (
    attrib -r -s -h "%ProgramData%\RupeeCRM\*.*" /s /d >nul 2>&1
    rd /s /q "%ProgramData%\RupeeCRM" >nul 2>&1
    echo   - Purged %ProgramData%\RupeeCRM
)
if exist "%ProgramData%\SimpleBilling" (
    attrib -r -s -h "%ProgramData%\SimpleBilling\*.*" /s /d >nul 2>&1
    rd /s /q "%ProgramData%\SimpleBilling" >nul 2>&1
    echo   - Purged %ProgramData%\SimpleBilling
)

echo [4/6] Removing Shortcuts...
if exist "%USERPROFILE%\Desktop\RupeeCRM.lnk" (
    del /f /q /a "%USERPROFILE%\Desktop\RupeeCRM.lnk" >nul 2>&1
    echo   - Removed Desktop shortcut (RupeeCRM)
)
if exist "%USERPROFILE%\Desktop\Billsoft.lnk" (
    del /f /q /a "%USERPROFILE%\Desktop\Billsoft.lnk" >nul 2>&1
    echo   - Removed legacy Desktop shortcut
)
if exist "%PUBLIC%\Desktop\RupeeCRM.lnk" (
    del /f /q /a "%PUBLIC%\Desktop\RupeeCRM.lnk" >nul 2>&1
    echo   - Removed Public Desktop shortcut
)
if exist "%PUBLIC%\Desktop\Billsoft.lnk" (
    del /f /q /a "%PUBLIC%\Desktop\Billsoft.lnk" >nul 2>&1
    echo   - Removed Public Desktop shortcut (legacy)
)
if exist "%APPDATA%\Microsoft\Windows\Start Menu\Programs\RupeeCRM" (
    rd /s /q "%APPDATA%\Microsoft\Windows\Start Menu\Programs\RupeeCRM" >nul 2>&1
    echo   - Removed Start Menu shortcuts (RupeeCRM)
)
if exist "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Billsoft" (
    rd /s /q "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Billsoft" >nul 2>&1
    echo   - Removed Start Menu shortcuts (legacy)
)

echo [5/6] Removing installed binaries...
powershell -NoProfile -Command "$appDirs = @('$env:LOCALAPPDATA\Programs\RupeeCRM', '$env:LOCALAPPDATA\Programs\RupeeCRM.old', '$env:LOCALAPPDATA\Programs\Billsoft', '$env:ProgramFiles\RupeeCRM', '${env:ProgramFiles(x86)}\RupeeCRM', '$env:ProgramFiles\Billsoft'); foreach ($d in $appDirs) { if ($d -and (Test-Path $d)) { for ($i=0; $i -lt 3; $i++) { try { Remove-Item -Path $d -Recurse -Force -ErrorAction SilentlyContinue; if (-not (Test-Path $d)) { break } } catch {} Start-Sleep -Milliseconds 300 } } }" >nul 2>&1

if exist "%LOCALAPPDATA%\Programs\RupeeCRM" (
    attrib -r -s -h "%LOCALAPPDATA%\Programs\RupeeCRM\*.*" /s /d >nul 2>&1
    rd /s /q "%LOCALAPPDATA%\Programs\RupeeCRM" >nul 2>&1
    echo   - Removed installed binaries in %LOCALAPPDATA%\Programs\RupeeCRM
)
if exist "%LOCALAPPDATA%\Programs\RupeeCRM.old" (
    attrib -r -s -h "%LOCALAPPDATA%\Programs\RupeeCRM.old\*.*" /s /d >nul 2>&1
    rd /s /q "%LOCALAPPDATA%\Programs\RupeeCRM.old" >nul 2>&1
    echo   - Removed %LOCALAPPDATA%\Programs\RupeeCRM.old
)
if exist "%LOCALAPPDATA%\Programs\Billsoft" (
    attrib -r -s -h "%LOCALAPPDATA%\Programs\Billsoft\*.*" /s /d >nul 2>&1
    rd /s /q "%LOCALAPPDATA%\Programs\Billsoft" >nul 2>&1
    echo   - Removed installed binaries in %LOCALAPPDATA%\Programs\Billsoft
)
if exist "%ProgramFiles%\RupeeCRM" (
    attrib -r -s -h "%ProgramFiles%\RupeeCRM\*.*" /s /d >nul 2>&1
    rd /s /q "%ProgramFiles%\RupeeCRM" >nul 2>&1
    echo   - Removed %ProgramFiles%\RupeeCRM
)

echo [6/6] Cleanup complete!
echo.
color 0A
echo ================================================================
echo  SUCCESS: All RupeeCRM data, databases, processes, and
echo           startup services have been completely purged.
echo  A fresh install will now start with a clean state.
echo ================================================================
echo.
pause
exit /b 0
