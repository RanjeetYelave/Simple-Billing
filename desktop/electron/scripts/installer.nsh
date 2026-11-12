; ==============================================================================
; RupeeCRM Desktop NSIS Lifecycle & Legacy Migration Script
; ==============================================================================

!macro customInit
  ; 1. Terminate running RupeeCRM electron process to release locked binaries
  nsExec::Exec 'cmd.exe /c taskkill /F /IM RupeeCRM.exe 2>nul'

  ; 2. Terminate legacy RupeeCRM backend process holding port 28080 only if verified as RupeeCRM
  nsExec::Exec 'powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "$conn = Get-NetTCPConnection -LocalPort 28080 -State Listen -ErrorAction SilentlyContinue; if ($conn) { $p = Get-CimInstance Win32_Process -Filter \"ProcessId = $($conn.OwningProcess)\" -ErrorAction SilentlyContinue; if ($p -and ($p.CommandLine -match \"(?i)rupeecrm\" -or $p.CommandLine -match \"(?i)billsoft\")) { Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue } }"'

  ; 3. Clean legacy Windows Startup folder VBS script from v1.0.280
  ${If} ${FileExists} "$SMSTARTUP\RupeeCRM.vbs"
    Delete "$SMSTARTUP\RupeeCRM.vbs"
  ${EndIf}

  ; 4. Clean legacy Registry Run entry from v1.0.280
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

  ; Ensure legacy startup VBS and registry entries are purged
  ${If} ${FileExists} "$SMSTARTUP\RupeeCRM.vbs"
    Delete "$SMSTARTUP\RupeeCRM.vbs"
  ${EndIf}
  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "RupeeCRMService"
!macroend
