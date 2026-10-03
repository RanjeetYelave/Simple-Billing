# ==============================================================================
# Automated Test Suite for RupeeCRM Complete Forensic Purge Utility
# ==============================================================================

$ErrorActionPreference = "Stop"

Write-Host "=========================================================="
Write-Host " [TEST] Testing RupeeCRM Complete Forensic Purge Utility"
Write-Host "=========================================================="

# 1. Setup Mock RupeeCRM Environments (Current + Legacy + Data)
$mockCurrentDir = "$env:LOCALAPPDATA\Programs\RupeeCRM"
$mockDataDir    = "$env:LOCALAPPDATA\RupeeCRM\data"
$mockBackupsDir = "$env:LOCALAPPDATA\RupeeCRM\backups"
$mockLogsDir    = "$env:LOCALAPPDATA\RupeeCRM\logs"
$mockStagingDir = "$env:LOCALAPPDATA\RupeeCRM\staging"
$mockLegacyDir  = "$env:USERPROFILE\.rupeecrm"
$mockTempDir    = "$env:TEMP\rupeecrm_test_staging"

# Create directories
New-Item -ItemType Directory -Force -Path $mockCurrentDir | Out-Null
New-Item -ItemType Directory -Force -Path "$mockCurrentDir\app\app" | Out-Null
New-Item -ItemType Directory -Force -Path "$mockCurrentDir\runtime\bin" | Out-Null
New-Item -ItemType Directory -Force -Path $mockDataDir | Out-Null
New-Item -ItemType Directory -Force -Path $mockBackupsDir | Out-Null
New-Item -ItemType Directory -Force -Path $mockLogsDir | Out-Null
New-Item -ItemType Directory -Force -Path $mockStagingDir | Out-Null
New-Item -ItemType Directory -Force -Path $mockLegacyDir | Out-Null
New-Item -ItemType Directory -Force -Path $mockTempDir | Out-Null

# Populate mock binaries, databases, and artifacts
"MOCK_EXE" | Out-File -FilePath "$mockCurrentDir\RupeeCRM.exe" -Encoding ASCII
"MOCK_JAR" | Out-File -FilePath "$mockCurrentDir\app\launcher.jar" -Encoding ASCII
"MOCK_WAR" | Out-File -FilePath "$mockCurrentDir\app\app\rupeecrm.war" -Encoding ASCII
"MOCK_JAVA" | Out-File -FilePath "$mockCurrentDir\runtime\bin\javaw.exe" -Encoding ASCII
"MOCK_DATABASE" | Out-File -FilePath "$mockDataDir\database.mv.db" -Encoding ASCII
"MOCK_BACKUP" | Out-File -FilePath "$mockBackupsDir\backup_20261003.zip" -Encoding ASCII
"MOCK_LOG" | Out-File -FilePath "$mockLogsDir\supervisor.log" -Encoding ASCII
"MOCK_STAGED_WAR" | Out-File -FilePath "$mockStagingDir\rupeecrm-update.war" -Encoding ASCII
"MOCK_LEGACY_DATA" | Out-File -FilePath "$mockLegacyDir\legacy.dat" -Encoding ASCII
"MOCK_TEMP_DATA" | Out-File -FilePath "$mockTempDir\staged.tmp" -Encoding ASCII

# Setup Mock User Environment Variable
try {
    [Environment]::SetEnvironmentVariable("RUPEECRM_DATA_DIR", $mockDataDir, "User")
} catch {}

# Setup Unrelated Application Artifact to prove safety isolation
$unrelatedDir = "$env:LOCALAPPDATA\UnrelatedTestApp"
New-Item -ItemType Directory -Force -Path $unrelatedDir | Out-Null
$unrelatedFile = "$unrelatedDir\important_data.txt"
"CRITICAL_USER_DATA_DO_NOT_DELETE" | Out-File -FilePath $unrelatedFile -Encoding UTF8

Write-Host "[OK] Mock RupeeCRM environment & unrelated safety canary initialized."

# 2. Test Diagnostics Mode BEFORE Purge (Must detect artifacts and exit with code 1)
Write-Host "`n==> Testing Diagnostics mode with active mock artifacts..."
& .\tools\purge-rupeecrm-completely.ps1 -Diagnostics -NoElevation
if ($LASTEXITCODE -ne 1) {
    throw "FAILURE: Diagnostics failed to detect active mock artifacts (expected exit code 1, got $LASTEXITCODE)!"
}
if (-not (Test-Path "$mockDataDir\database.mv.db") -or -not (Test-Path "$mockCurrentDir\RupeeCRM.exe")) {
    throw "CRITICAL VIOLATION: Diagnostics mode modified or deleted mock artifacts!"
}
Write-Host "[OK] Diagnostics accurately detected existing artifacts and made zero modifications."

# 3. Test Dry-Run Mode (Must NOT delete anything)
Write-Host "`n==> Testing Dry-Run mode..."
& .\tools\purge-rupeecrm-completely.ps1 -DryRun -NoElevation
if (-not (Test-Path "$mockDataDir\database.mv.db") -or -not (Test-Path "$mockCurrentDir\RupeeCRM.exe")) {
    throw "FAILURE: Dry-run mode modified or deleted mock artifacts!"
}
Write-Host "[OK] Dry-Run mode validated (zero modifications)."

# 4. Test Destructive Purge Execution
Write-Host "`n==> Testing Destructive Purge execution..."
& .\tools\purge-rupeecrm-completely.ps1 -Force -NoElevation
if ($LASTEXITCODE -ne 0) {
    throw "FAILURE: Destructive purge exited with non-zero code ($LASTEXITCODE)!"
}

# 5. Verify All Mock RupeeCRM Artifacts Are Completely Removed
Write-Host "`n==> Verifying removal of all mock RupeeCRM artifacts..."

$pathsToCheck = @(
    $mockCurrentDir,
    "$env:LOCALAPPDATA\RupeeCRM",
    $mockLegacyDir,
    $mockTempDir
)

foreach ($p in $pathsToCheck) {
    if (Test-Path $p) {
        throw "FAILURE: Path '$p' still exists after complete purge!"
    }
}

$remainingEnvVar = [Environment]::GetEnvironmentVariable("RUPEECRM_DATA_DIR", "User")
if ($remainingEnvVar) {
    throw "FAILURE: Environment variable RUPEECRM_DATA_DIR still exists after complete purge!"
}
Write-Host "[OK] All RupeeCRM directories, databases, binaries, env vars, and caches successfully wiped."

# 6. Verify Unrelated Application Safety Canary Is 100% Intact
Write-Host "`n==> Verifying unrelated safety canary..."
if (-not (Test-Path $unrelatedFile)) {
    throw "CRITICAL SAFETY VIOLATION: Unrelated file '$unrelatedFile' was deleted!"
}
$unrelatedContent = Get-Content $unrelatedFile -Raw
if ($unrelatedContent -notmatch "CRITICAL_USER_DATA_DO_NOT_DELETE") {
    throw "CRITICAL SAFETY VIOLATION: Unrelated file '$unrelatedFile' was corrupted!"
}
Write-Host "[OK] Unrelated application data safely preserved."

# Cleanup canary
Remove-Item -Path $unrelatedDir -Recurse -Force -ErrorAction SilentlyContinue

# 7. Execute Diagnostics Quality Gate on Clean State (Must return exit code 0)
Write-Host "`n==> Running Diagnostics mode on clean system..."
& .\tools\purge-rupeecrm-completely.ps1 -Diagnostics -NoElevation
if ($LASTEXITCODE -ne 0) {
    throw "FAILURE: Diagnostics reported unclean state after purge (expected exit code 0, got $LASTEXITCODE)!"
}

Write-Host ""
Write-Host "=========================================================="
Write-Host " [TEST SUCCESS] Purge Script Verification Passed 100%!"
Write-Host "=========================================================="
