@echo off
setlocal EnableDelayedExpansion
title Billsoft - Full System Cleanup and Uninstaller
color 0C

echo ================================================================
echo               BILLSOFT COMPLETE SYSTEM PURGE
echo ================================================================
echo  WARNING: This tool will completely and permanently remove:
echo    - All running Billsoft background services and processes
echo    - All database files (database.mv.db) and historical backups
echo    - All invoices, products, customers, and HR staff records
echo    - Windows startup registry entries and startup scripts
echo    - Desktop and Start Menu shortcuts
echo    - Application logs and configuration files
echo ================================================================
echo.
set /p CONFIRM="Are you absolutely sure you want to delete EVERYTHING? (Type YES to confirm): "
if /i not "!CONFIRM!"=="YES" (
    echo.
    echo Operation cancelled by user. No files were removed.
    echo.
    pause
    exit /b 0
)

echo.
echo [1/6] Stopping running Billsoft processes...
taskkill /F /IM Billsoft.exe >nul 2>&1
wmic process where "commandline like '%%billsoft%%'" call terminate >nul 2>&1
wmic process where "commandline like '%%launcher.jar%%'" call terminate >nul 2>&1

echo [2/6] Removing Windows auto-start registrations...
reg delete "HKCU\Software\Microsoft\Windows\CurrentVersion\Run" /v "BillsoftService" /f >nul 2>&1
reg delete "HKLM\Software\Microsoft\Windows\CurrentVersion\Run" /v "BillsoftService" /f >nul 2>&1

if exist "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\Billsoft.vbs" (
    del /f /q "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\Billsoft.vbs" >nul 2>&1
    echo   - Removed Startup folder script (Billsoft.vbs)
)

echo [3/6] Purging Billsoft databases, backups, and app data...
if exist "%APPDATA%\SimpleBilling" (
    rd /s /q "%APPDATA%\SimpleBilling" >nul 2>&1
    echo   - Purged %APPDATA%\SimpleBilling
)
if exist "%USERPROFILE%\.billsoft" (
    rd /s /q "%USERPROFILE%\.billsoft" >nul 2>&1
    echo   - Purged %USERPROFILE%\.billsoft
)
if exist "%USERPROFILE%\.simplebilling" (
    rd /s /q "%USERPROFILE%\.simplebilling" >nul 2>&1
    echo   - Purged %USERPROFILE%\.simplebilling
)
if exist "%LOCALAPPDATA%\SimpleBilling" (
    rd /s /q "%LOCALAPPDATA%\SimpleBilling" >nul 2>&1
    echo   - Purged %LOCALAPPDATA%\SimpleBilling
)

echo [4/6] Removing Shortcuts...
if exist "%USERPROFILE%\Desktop\Billsoft.lnk" (
    del /f /q "%USERPROFILE%\Desktop\Billsoft.lnk" >nul 2>&1
    echo   - Removed Desktop shortcut
)
if exist "%PUBLIC%\Desktop\Billsoft.lnk" (
    del /f /q "%PUBLIC%\Desktop\Billsoft.lnk" >nul 2>&1
    echo   - Removed Public Desktop shortcut
)
if exist "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Billsoft" (
    rd /s /q "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Billsoft" >nul 2>&1
    echo   - Removed Start Menu shortcuts
)

echo [5/6] Checking for local installer files...
if exist "%LOCALAPPDATA%\Programs\Billsoft" (
    rd /s /q "%LOCALAPPDATA%\Programs\Billsoft" >nul 2>&1
    echo   - Removed installed binaries in %LOCALAPPDATA%\Programs\Billsoft
)

echo [6/6] Cleanup complete!
echo.
color 0A
echo ================================================================
echo  SUCCESS: All Billsoft data, databases, processes, and
echo           startup services have been completely purged.
echo ================================================================
echo.
pause
exit /b 0
