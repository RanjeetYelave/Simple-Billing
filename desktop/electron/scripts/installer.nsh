; ==============================================================================
; RupeeCRM Desktop NSIS Lifecycle & Legacy Migration Script
; ==============================================================================

!include "LogicLib.nsh"

!macro customInit
  ; 1. Terminate running RupeeCRM electron process to release locked binaries
  nsExec::Exec 'cmd.exe /c taskkill /F /IM RupeeCRM.exe 2>nul'
  Pop $0

  ; 2. Terminate background Java processes executing RupeeCRM or Launcher (CIM + WMIC fallback)
  nsExec::Exec 'powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object { ($$_.Name -like \"*java*.exe\") -and ($$_.CommandLine -match \"(?i)(rupeecrm|billsoft|launcher)\") } | Stop-Process -Force -ErrorAction SilentlyContinue"'
  Pop $0
  nsExec::Exec 'cmd.exe /c wmic process where "name=''javaw.exe'' and (commandline like ''%rupeecrm%'' or commandline like ''%billsoft%'' or commandline like ''%launcher%'')" call terminate 2>nul'
  Pop $0
  nsExec::Exec 'cmd.exe /c wmic process where "name=''java.exe'' and (commandline like ''%rupeecrm%'' or commandline like ''%billsoft%'' or commandline like ''%launcher%'')" call terminate 2>nul'
  Pop $0

  ; 3. Terminate legacy RupeeCRM backend process holding port 28080 only if verified as RupeeCRM/Launcher
  nsExec::Exec 'powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "$$conn = Get-NetTCPConnection -LocalPort 28080 -State Listen -ErrorAction SilentlyContinue; if ($$conn) { $$p = Get-CimInstance Win32_Process -Filter \"\"ProcessId = $$($$conn.OwningProcess)\"\" -ErrorAction SilentlyContinue; if ($$p -and ($$p.CommandLine -match \"\"(?i)(rupeecrm|billsoft|launcher)\"\")) { Stop-Process -Id $$p.ProcessId -Force -ErrorAction SilentlyContinue } }"'
  Pop $0

  ; 4. Clean legacy Windows Startup folder VBS script from v1.0.280
  ${If} ${FileExists} "$SMSTARTUP\RupeeCRM.vbs"
    Delete "$SMSTARTUP\RupeeCRM.vbs"
  ${EndIf}

  ; 5. Clean legacy Registry Run entry from v1.0.280
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

  ; Purge obsolete AppData runtime WAR binaries and update handoff tokens without touching customer data or backups
  Delete "$LOCALAPPDATA\RupeeCRM\app\rupeecrm.war"
  Delete "$LOCALAPPDATA\RupeeCRM\app\rupeecrm.war.bak"
  Delete "$LOCALAPPDATA\RupeeCRM\rupeecrm.war"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update-tx\rupeecrm-update.war"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update.handoff"

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
!macroend

!macro customUnInstall
  ; 1. Terminate running RupeeCRM electron process to release locked binaries
  nsExec::Exec 'cmd.exe /c taskkill /F /IM RupeeCRM.exe 2>nul'
  Pop $0

  ; 2. Terminate background Java processes executing RupeeCRM or Launcher
  nsExec::Exec 'powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object { ($$_.Name -like \"*java*.exe\") -and ($$_.CommandLine -match \"(?i)(rupeecrm|billsoft|launcher)\") } | Stop-Process -Force -ErrorAction SilentlyContinue"'
  Pop $0

  ; 3. Purge obsolete runtime WAR binaries and update handoff tokens without touching customer data or backups
  Delete "$LOCALAPPDATA\RupeeCRM\app\rupeecrm.war"
  Delete "$LOCALAPPDATA\RupeeCRM\app\rupeecrm.war.bak"
  Delete "$LOCALAPPDATA\RupeeCRM\rupeecrm.war"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update-tx\rupeecrm-update.war"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update.handoff"
  Delete "$LOCALAPPDATA\RupeeCRM\.billsoft-version"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\.billsoft-version"
!macroend
