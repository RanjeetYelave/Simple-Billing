# ==============================================================================
# PowerShell Syntax Precheck for CI
# ==============================================================================

$ErrorActionPreference = "Stop"

$scripts = @(
    "tools/install-windows.ps1",
    "tools/uninstall-windows.ps1",
    "tools/validate-windows-lifecycle.ps1"
)

$hasErrors = $false

foreach ($script in $scripts) {
    if (-not (Test-Path $script)) {
        Write-Error "Script file not found: $script"
        $hasErrors = $true
        continue
    }

    $fullPath = (Resolve-Path $script).Path
    $errors = $null
    $tokens = $null
    [System.Management.Automation.Language.Parser]::ParseFile($fullPath, [ref]$tokens, [ref]$errors) | Out-Null

    if ($errors -and $errors.Count -gt 0) {
        Write-Host "[FAIL] PowerShell syntax errors in $script :" -ForegroundColor Red
        foreach ($err in $errors) {
            Write-Host "  Line $($err.Extent.StartLineNumber): $($err.Message)" -ForegroundColor Red
        }
        $hasErrors = $true
    } else {
        Write-Host "[OK] PowerShell syntax valid: $script" -ForegroundColor Green
    }
}

if ($hasErrors) {
    throw "PowerShell script syntax verification failed."
}
