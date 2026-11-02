param(
    [string]$Version = "",
    [int]$HealthRetries = 30,
    [int]$DrainSeconds = 8
)

# Blue-green release: build image -> migrate -> start standby -> health -> switch nginx.
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "common.ps1")

Set-Location (Get-PackageRoot)
if (-not (Test-Path ".env")) { throw "Missing .env" }
if (-not $Version) { $Version = (Get-Date).ToString("yyyy.MM.dd.HHmmss") }

$script:ComposeCmd = Resolve-ComposeCommand
$script:Engine = Resolve-ContainerEngine
$env:APP_VERSION = $Version

$current = Get-CurrentSlot
$target = if ($current -eq "blue") { "green" } else { "blue" }
Write-Host "[release] $current -> $target, version $Version" -ForegroundColor Cyan

Write-Host "[release] Building image patnest-api:$Version" -ForegroundColor Cyan
Invoke-Compose @("--profile", "app", "build", "--pull", "api-blue")

Write-Host "[release] Running database migrations" -ForegroundColor Cyan
try {
    Invoke-Compose @("--profile", "tools", "run", "--rm", "migrate")
} catch {
    throw "Database migration failed. Check DB_HOST:DB_PORT reachability and credentials. Original error: $($_.Exception.Message)"
}

Write-Host "[release] Starting api-$target" -ForegroundColor Cyan
Invoke-Compose @("--profile", "app", "up", "-d", "--no-deps", "api-$target")

if (-not (Wait-Healthy -Slot $target -Retries $HealthRetries)) {
    Invoke-NativeCommand -Command $script:Engine -Arguments @("logs", "--tail", "60", "patnest-api-$target") -AllowFail | Out-Null
    Invoke-Compose @("--profile", "app", "stop", "api-$target") -AllowFail
    throw "Health check failed for api-$target; traffic stays on $current."
}

Set-UpstreamSlot -Slot $target
Invoke-NativeCommand -Command $script:Engine -Arguments @("exec", "patnest-nginx", "nginx", "-t") | Out-Null
Invoke-NativeCommand -Command $script:Engine -Arguments @("exec", "patnest-nginx", "nginx", "-s", "reload") | Out-Null

Write-Host "[release] Traffic switched to $target, drain ${DrainSeconds}s" -ForegroundColor Cyan
Start-Sleep -Seconds $DrainSeconds
Invoke-Compose @("--profile", "app", "stop", "api-$current") -AllowFail

Write-Host "[release] Done: $Version" -ForegroundColor Green
