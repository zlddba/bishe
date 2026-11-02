param(
    [Parameter(Mandatory = $true)][string]$Version,
    [int]$HealthRetries = 30,
    [int]$DrainSeconds = 5
)

# Roll back to a locally available image tag, e.g. .\scripts\rollback.ps1 -Version 1.0.0
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "common.ps1")

Set-Location (Get-PackageRoot)
$script:ComposeCmd = Resolve-ComposeCommand
$script:Engine = Resolve-ContainerEngine
$env:APP_VERSION = $Version

$inspect = Invoke-NativeCommand -Command $script:Engine -Arguments @("image", "inspect", "patnest-api:$Version") -Quiet -AllowFail
if ($inspect.ExitCode -ne 0) { throw "Image patnest-api:$Version not found locally." }

$current = Get-CurrentSlot
$target = if ($current -eq "blue") { "green" } else { "blue" }
Write-Host "[rollback] image patnest-api:$Version, $current -> $target" -ForegroundColor Cyan

Invoke-Compose @("--profile", "app", "up", "-d", "--no-deps", "api-$target")

if (-not (Wait-Healthy -Slot $target -Retries $HealthRetries)) {
    Invoke-NativeCommand -Command $script:Engine -Arguments @("logs", "--tail", "60", "patnest-api-$target") -AllowFail | Out-Null
    Invoke-Compose @("--profile", "app", "stop", "api-$target") -AllowFail
    throw "Health check failed for the rollback version; traffic unchanged."
}

Set-UpstreamSlot -Slot $target
Invoke-NativeCommand -Command $script:Engine -Arguments @("exec", "patnest-nginx", "nginx", "-t") | Out-Null
Invoke-NativeCommand -Command $script:Engine -Arguments @("exec", "patnest-nginx", "nginx", "-s", "reload") | Out-Null

Start-Sleep -Seconds $DrainSeconds
Invoke-Compose @("--profile", "app", "stop", "api-$current") -AllowFail

Write-Host "[rollback] Done: $Version" -ForegroundColor Green
