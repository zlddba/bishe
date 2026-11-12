param(
    [string]$Version = ""
)

# Rebuild nginx image with the new web/dist and recreate the nginx container.
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "common.ps1")

Set-Location (Get-PackageRoot)
if (-not (Test-Path "web\dist\index.html")) { throw "Missing web\dist\index.html" }
if (-not $Version) { $Version = Get-EnvValue "APP_VERSION" "1.0.0" }

$script:ComposeCmd = Resolve-ComposeCommand
$script:Engine = Resolve-ContainerEngine
$env:APP_VERSION = $Version

Assert-EngineRunning "[web]"

Ensure-NginxMountFiles

Write-Host "[web] Building image patnest-web:$Version" -ForegroundColor Cyan
Invoke-Compose @("--profile", "app", "build", "--pull", "nginx")

Write-Host "[web] Recreating nginx container" -ForegroundColor Cyan
Invoke-Compose @("--profile", "app", "up", "-d", "--no-deps", "--force-recreate", "nginx")

Invoke-NativeCommand -Command $script:Engine -Arguments @("exec", "patnest-nginx", "nginx", "-t") | Out-Null

$buildId = "unknown"
$versionFile = "web\dist\version.json"
if (Test-Path $versionFile) {
    $buildId = (Get-Content $versionFile -Raw | ConvertFrom-Json).build_id
}
Write-Host "[web] Done, build_id=$buildId" -ForegroundColor Green
