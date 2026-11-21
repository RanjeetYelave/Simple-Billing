; ==============================================================================
; RupeeCRM Desktop NSIS Lifecycle & Windows Installation/Uninstallation Script
; ==============================================================================

!include "LogicLib.nsh"

!macro customInit
  ; 1. Signal graceful HTTP shutdown to RupeeCRM backend on port 28080
  nsExec::Exec 'powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "try { Invoke-WebRequest -Uri \"http://127.0.0.1:28080/api/shutdown\" -Method POST -TimeoutSec 2 -UseBasicParsing -ErrorAction SilentlyContinue } catch {}"'
  Pop $0

  ; 2. Terminate running RupeeCRM electron process to release locked binaries
  nsExec::Exec 'cmd.exe /c taskkill /F /IM RupeeCRM.exe 2>nul'
  Pop $0

  ; 3. Terminate background Java processes executing RupeeCRM or Launcher (CIM + WMIC fallback)
  nsExec::Exec 'powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object { ($$_.Name -like \"*java*.exe\") -and ($$_.CommandLine -match \"(?i)(rupeecrm|billsoft|RupeeCRMLauncher|RupeeCRM-Launcher|launcher\.jar)\") } | Stop-Process -Force -ErrorAction SilentlyContinue"'
  Pop $0
  nsExec::Exec 'cmd.exe /c wmic process where "name=''javaw.exe'' and (commandline like ''%rupeecrm%'' or commandline like ''%billsoft%'' or commandline like ''%RupeeCRMLauncher%'')" call terminate 2>nul'
  Pop $0
  nsExec::Exec 'cmd.exe /c wmic process where "name=''java.exe'' and (commandline like ''%rupeecrm%'' or commandline like ''%billsoft%'' or commandline like ''%RupeeCRMLauncher%'')" call terminate 2>nul'
  Pop $0

  ; 4. Terminate legacy RupeeCRM backend process holding port 28080 only if verified as RupeeCRM/Launcher
  nsExec::Exec 'powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "$$conn = Get-NetTCPConnection -LocalPort 28080 -State Listen -ErrorAction SilentlyContinue; if ($$conn) { $$p = Get-CimInstance Win32_Process -Filter \"\"ProcessId = $$($$conn.OwningProcess)\"\" -ErrorAction SilentlyContinue; if ($$p -and ($$p.CommandLine -match \"\"(?i)(rupeecrm|billsoft|RupeeCRMLauncher|RupeeCRM-Launcher)\"\")) { Stop-Process -Id $$p.ProcessId -Force -ErrorAction SilentlyContinue } }"'
  Pop $0

  ; 5. Synchronously poll for process termination (up to 5 seconds)
  nsExec::Exec 'powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "for ($$i=0; $$i -lt 20; $$i++) { $$procs = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object { ($$_.Name -eq \"RupeeCRM.exe\") -or (($$_.Name -like \"*java*.exe\") -and ($$_.CommandLine -match \"(?i)(rupeecrm|billsoft|RupeeCRMLauncher|RupeeCRM-Launcher)\")) }; if (-not $$procs) { break }; Start-Sleep -Milliseconds 250 }"'
  Pop $0

  ; 6. Test if target war in $INSTDIR is locked and wait for file handle release if needed (up to 5 seconds)
  ${If} ${FileExists} "$INSTDIR\resources\rupeecrm.war"
    nsExec::Exec 'powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "$$f = \"$INSTDIR\resources\rupeecrm.war\"; for ($$i=0; $$i -lt 20; $$i++) { try { $$s = [System.IO.File]::Open($$f, [System.IO.FileMode]::Open, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None); $$s.Close(); $$s.Dispose(); break } catch { Start-Sleep -Milliseconds 250 } }"'
    Pop $0
  ${EndIf}

  ; 7. Clean legacy Windows Startup folder VBS script from v1.0.280
  ${If} ${FileExists} "$SMSTARTUP\RupeeCRM.vbs"
    Delete "$SMSTARTUP\RupeeCRM.vbs"
  ${EndIf}

  ; 8. Clean legacy Registry Run entries
  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "RupeeCRMService"
  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "RupeeCRM"
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
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update-state.json"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update-tx\rupeecrm-update.war"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update-tx\rupeecrm-update.war.part"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update-tx\manifest.json"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update-tx\manifest.sig"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update.handoff"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update.lock"
  Delete "$LOCALAPPDATA\RupeeCRM\.supervisor.ipc"
  Delete "$LOCALAPPDATA\RupeeCRM\.backend.pid"

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

; ------------------------------------------------------------------------------
; Macro: customRemoveFiles (called inside uninstaller before file deletion)
; Stops all RupeeCRM processes, releases port 28080, and cleanly deletes $INSTDIR
; ------------------------------------------------------------------------------
!macro customRemoveFiles
  ; 1. Signal graceful HTTP shutdown to RupeeCRM backend on port 28080
  nsExec::Exec 'powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "try { Invoke-WebRequest -Uri \"http://127.0.0.1:28080/api/shutdown\" -Method POST -TimeoutSec 2 -UseBasicParsing -ErrorAction SilentlyContinue } catch {}"'
  Pop $0

  ; 2. Terminate running RupeeCRM electron process to release locked binaries
  nsExec::Exec 'cmd.exe /c taskkill /F /IM RupeeCRM.exe 2>nul'
  Pop $0

  ; 3. Terminate background Java processes executing RupeeCRM or Launcher (CIM + WMIC fallback)
  nsExec::Exec 'powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object { ($$_.Name -like \"*java*.exe\") -and ($$_.CommandLine -match \"(?i)(rupeecrm|billsoft|RupeeCRMLauncher|RupeeCRM-Launcher|launcher\.jar)\") } | Stop-Process -Force -ErrorAction SilentlyContinue"'
  Pop $0
  nsExec::Exec 'cmd.exe /c wmic process where "name=''javaw.exe'' and (commandline like ''%rupeecrm%'' or commandline like ''%billsoft%'' or commandline like ''%RupeeCRMLauncher%'')" call terminate 2>nul'
  Pop $0
  nsExec::Exec 'cmd.exe /c wmic process where "name=''java.exe'' and (commandline like ''%rupeecrm%'' or commandline like ''%billsoft%'' or commandline like ''%RupeeCRMLauncher%'')" call terminate 2>nul'
  Pop $0

  ; 4. Terminate process holding port 28080 only if verified as RupeeCRM/Launcher
  nsExec::Exec 'powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "$$conn = Get-NetTCPConnection -LocalPort 28080 -State Listen -ErrorAction SilentlyContinue; if ($$conn) { $$p = Get-CimInstance Win32_Process -Filter \"\"ProcessId = $$($$conn.OwningProcess)\"\" -ErrorAction SilentlyContinue; if ($$p -and ($$p.CommandLine -match \"\"(?i)(rupeecrm|billsoft|RupeeCRMLauncher|RupeeCRM-Launcher)\"\")) { Stop-Process -Id $$p.ProcessId -Force -ErrorAction SilentlyContinue } }"'
  Pop $0

  ; 5. Synchronously poll for process termination (up to 5 seconds)
  nsExec::Exec 'powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "for ($$i=0; $$i -lt 20; $$i++) { $$procs = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object { ($$_.Name -eq \"RupeeCRM.exe\") -or (($$_.Name -like \"*java*.exe\") -and ($$_.CommandLine -match \"(?i)(rupeecrm|billsoft|RupeeCRMLauncher|RupeeCRM-Launcher)\")) }; if (-not $$procs) { break }; Start-Sleep -Milliseconds 250 }"'
  Pop $0

  ; 6. Delete all files in $INSTDIR
  RMDir /r "$INSTDIR"
  ${If} ${FileExists} "$INSTDIR"
    nsExec::Exec 'powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "for ($$i=0; $$i -lt 10; $$i++) { if (-not (Test-Path \"$INSTDIR\")) { break }; Remove-Item -LiteralPath \"$INSTDIR\" -Recurse -Force -ErrorAction SilentlyContinue; Start-Sleep -Milliseconds 250 }"'
    Pop $0
    ${If} ${FileExists} "$INSTDIR"
      RMDir /r "$INSTDIR"
    ${EndIf}
  ${EndIf}
!macroend

!macro customUnInstall
  ; 1. Final verification: ensure all RupeeCRM processes are stopped
  nsExec::Exec 'cmd.exe /c taskkill /F /IM RupeeCRM.exe 2>nul'
  Pop $0
  nsExec::Exec 'powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object { ($$_.Name -like \"*java*.exe\") -and ($$_.CommandLine -match \"(?i)(rupeecrm|billsoft|RupeeCRMLauncher|RupeeCRM-Launcher|launcher\.jar)\") } | Stop-Process -Force -ErrorAction SilentlyContinue"'
  Pop $0

  ; 2. Remove Windows Run registry entries to prevent autostart of missing executable
  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "RupeeCRM"
  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "RupeeCRMService"
  DeleteRegValue HKLM "Software\Microsoft\Windows\CurrentVersion\Run" "RupeeCRM"
  DeleteRegValue HKLM "Software\Microsoft\Windows\CurrentVersion\Run" "RupeeCRMService"

  ; 3. Remove Start Menu and Desktop shortcuts
  Delete "$DESKTOP\RupeeCRM.lnk"
  Delete "$SMPROGRAMS\RupeeCRM.lnk"
  RMDir /r "$SMPROGRAMS\RupeeCRM"
  ${If} ${FileExists} "$SMSTARTUP\RupeeCRM.vbs"
    Delete "$SMSTARTUP\RupeeCRM.vbs"
  ${EndIf}
  Delete "$SMSTARTUP\RupeeCRM.lnk"

  SetShellVarContext all
  Delete "$DESKTOP\RupeeCRM.lnk"
  Delete "$SMPROGRAMS\RupeeCRM.lnk"
  RMDir /r "$SMPROGRAMS\RupeeCRM"
  SetShellVarContext current

  ; 4. Purge runtime staging fragments, handoff tokens, lock tokens, and supervisor IPC
  ; CRITICAL: Under NO circumstances touch $LOCALAPPDATA\RupeeCRM\data or backups!
  Delete "$LOCALAPPDATA\RupeeCRM\app\rupeecrm.war"
  Delete "$LOCALAPPDATA\RupeeCRM\app\rupeecrm.war.bak"
  Delete "$LOCALAPPDATA\RupeeCRM\rupeecrm.war"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update-state.json"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update-tx\rupeecrm-update.war"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update-tx\rupeecrm-update.war.part"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update-tx\manifest.json"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update-tx\manifest.sig"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update.handoff"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\update.lock"
  Delete "$LOCALAPPDATA\RupeeCRM\.supervisor.ipc"
  Delete "$LOCALAPPDATA\RupeeCRM\.backend.pid"
  Delete "$LOCALAPPDATA\RupeeCRM\.supervisor.lock"
  Delete "$LOCALAPPDATA\RupeeCRM\.billsoft-version"
  Delete "$LOCALAPPDATA\RupeeCRM\staging\.billsoft-version"
  RMDir /r "$LOCALAPPDATA\RupeeCRM\staging\update-tx"

  ; 5. Verify installation directory cleanup: if $INSTDIR still exists due to delayed file unmapping, retry removal
  ${If} ${FileExists} "$INSTDIR"
    nsExec::Exec 'powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "for ($$i=0; $$i -lt 10; $$i++) { if (-not (Test-Path \"$INSTDIR\")) { break }; Remove-Item -LiteralPath \"$INSTDIR\" -Recurse -Force -ErrorAction SilentlyContinue; Start-Sleep -Milliseconds 250 }"'
    Pop $0
    ${If} ${FileExists} "$INSTDIR"
      RMDir /r "$INSTDIR"
    ${EndIf}
  ${EndIf}
!macroend
