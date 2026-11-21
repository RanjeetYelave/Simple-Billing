; ==============================================================================
; RupeeCRM Desktop NSIS Lifecycle & Legacy Migration Script
; ==============================================================================

!include "LogicLib.nsh"

!macro customInit
  ; 1. Terminate running RupeeCRM electron process to release locked binaries
  nsExec::Exec 'cmd.exe /c taskkill /F /IM RupeeCRM.exe 2>nul'
  Pop $0

  ; 2. Terminate background Java processes executing RupeeCRM or Launcher (CIM + WMIC fallback)
  nsExec::Exec 'powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object { ($$_.Name -like \"*java*.exe\") -and ($$_.CommandLine -match \"(?i)(rupeecrm|billsoft|RupeeCRMLauncher|RupeeCRM-Launcher)\") } | Stop-Process -Force -ErrorAction SilentlyContinue"'
  Pop $0
  nsExec::Exec 'cmd.exe /c wmic process where "name=''javaw.exe'' and (commandline like ''%rupeecrm%'' or commandline like ''%billsoft%'' or commandline like ''%RupeeCRMLauncher%'')" call terminate 2>nul'
  Pop $0
  nsExec::Exec 'cmd.exe /c wmic process where "name=''java.exe'' and (commandline like ''%rupeecrm%'' or commandline like ''%billsoft%'' or commandline like ''%RupeeCRMLauncher%'')" call terminate 2>nul'
  Pop $0

  ; 3. Terminate legacy RupeeCRM backend process holding port 28080 only if verified as RupeeCRM/Launcher
  nsExec::Exec 'powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "$$conn = Get-NetTCPConnection -LocalPort 28080 -State Listen -ErrorAction SilentlyContinue; if ($$conn) { $$p = Get-CimInstance Win32_Process -Filter \"\"ProcessId = $$($$conn.OwningProcess)\"\" -ErrorAction SilentlyContinue; if ($$p -and ($$p.CommandLine -match \"\"(?i)(rupeecrm|billsoft|RupeeCRMLauncher|RupeeCRM-Launcher)\"\")) { Stop-Process -Id $$p.ProcessId -Force -ErrorAction SilentlyContinue } }"'
  Pop $0

  ; 4. Synchronously poll for process termination (up to 5 seconds)
  nsExec::Exec 'powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "for ($$i=0; $$i -lt 20; $$i++) { $$procs = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object { ($$_.Name -eq \"RupeeCRM.exe\") -or (($$_.Name -like \"*java*.exe\") -and ($$_.CommandLine -match \"(?i)(rupeecrm|billsoft|RupeeCRMLauncher|RupeeCRM-Launcher)\")) }; if (-not $$procs) { break }; Start-Sleep -Milliseconds 250 }"'
  Pop $0

  ; 5. Test if target war in $INSTDIR is locked and wait for file handle release if needed (up to 5 seconds)
  ${If} ${FileExists} "$INSTDIR\resources\rupeecrm.war"
    nsExec::Exec 'powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "$$f = \"$INSTDIR\resources\rupeecrm.war\"; for ($$i=0; $$i -lt 20; $$i++) { try { $$s = [System.IO.File]::Open($$f, [System.IO.FileMode]::Open, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None); $$s.Close(); $$s.Dispose(); break } catch { Start-Sleep -Milliseconds 250 } }"'
    Pop $0
  ${EndIf}

  ; 6. Clean legacy Windows Startup folder VBS script from v1.0.280
  ${If} ${FileExists} "$SMSTARTUP\RupeeCRM.vbs"
    Delete "$SMSTARTUP\RupeeCRM.vbs"
  ${EndIf}

  ; 7. Clean legacy Registry Run entry from v1.0.280
  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "RupeeCRMService"
!macroend

!macro customInstall
  ; Remove stale legacy jpackage binary directories if present in install dir (without touching user data)
  ${If} ${FileExists} "$INSTDIR\app\RupeeCRM.cfg"
    RMDir /r "$INSTDIR\app"
  ${EndIf}
  ${If} ${FileExists} "$INSTDIR\runtime\bin\java.dll"
    RMDir /r "$INSTDIR\runtime"
  ${EndIf}

  ; Purge obsolete AppData runtime WAR binaries, staging fragments and update handoff tokens without touching customer data or backups
  Delete "$LOCALAPPDATA\RupeeCRM\app\rupeecrm.war"
  Delete "$LOCALAPPDATA\RupeeCRM\app\rupeecrm.war.bak"
  Delete "$LOCALAPPDATA\RupeeCRM\rupeecrm.war"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update-tx\rupeecrm-update.war"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update-tx\rupeecrm-update.war.part"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update-tx\manifest.json"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update-tx\manifest.sig"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update.handoff"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update.lock"

  ; Purge any obsolete version override files that could hijack runtime version reporting
  Delete "$LOCALAPPDATA\RupeeCRM\data\.billsoft-version"
  Delete "$LOCALAPPDATA\RupeeCRM\.billsoft-version"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\.billsoft-version"
  Delete "$INSTDIR\resources\.billsoft-version"
  Delete "$INSTDIR\.billsoft-version"

  ; Clean up legacy All-Users shortcuts that could shadow per-user desktop shortcut
  SetShellVarContext all
  Delete "$DESKTOP\RupeeCRM.lnk"
  Delete "$SMPROGRAMS\RupeeCRM.lnk"
  SetShellVarContext current

  ; Ensure legacy startup VBS and registry entries are purged
  ${If} ${FileExists} "$SMSTARTUP\RupeeCRM.vbs"
    Delete "$SMSTARTUP\RupeeCRM.vbs"
  ${EndIf}
  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "RupeeCRMService"

  ; Post-extraction verification: ensure backend WAR binary is present and not skipped due to sharing violation
  ${If} ${FileExists} "$INSTDIR\resources\rupeecrm.war"
    ; rupeecrm.war verified present
  ${Else}
    MessageBox MB_ICONSTOP|MB_OK "RupeeCRM installation error: The core backend component (resources\rupeecrm.war) was not extracted successfully. Please ensure no background process is holding open files, and rerun the installer."
    Abort
  ${EndIf}
!macroend

!macro customUnInstall
  ; 1. Terminate running RupeeCRM electron process to release locked binaries
  nsExec::Exec 'cmd.exe /c taskkill /F /IM RupeeCRM.exe 2>nul'
  Pop $0

  ; 2. Terminate background Java processes executing RupeeCRM or Launcher
  nsExec::Exec 'powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object { ($$_.Name -like \"*java*.exe\") -and ($$_.CommandLine -match \"(?i)(rupeecrm|billsoft|RupeeCRMLauncher|RupeeCRM-Launcher)\") } | Stop-Process -Force -ErrorAction SilentlyContinue"'
  Pop $0

  ; 3. Purge obsolete runtime WAR binaries, staging fragments and update handoff tokens without touching customer data or backups
  Delete "$LOCALAPPDATA\RupeeCRM\app\rupeecrm.war"
  Delete "$LOCALAPPDATA\RupeeCRM\app\rupeecrm.war.bak"
  Delete "$LOCALAPPDATA\RupeeCRM\rupeecrm.war"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update-tx\rupeecrm-update.war"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update-tx\rupeecrm-update.war.part"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update-tx\manifest.json"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update-tx\manifest.sig"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update.handoff"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update.lock"
  Delete "$LOCALAPPDATA\RupeeCRM\.billsoft-version"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\.billsoft-version"
!macroend
