# Usage: .\scripts\migrate.ps1 -status   /   .\scripts\migrate.ps1
# No param block on purpose: unknown flags such as -status are passed through
# to patnest-migrate as-is (advanced functions reject unbound named parameters).
$MigrateArgs = @($args)
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "common.ps1")

Set-Location (Get-PackageRoot)
$script:ComposeCmd = Resolve-ComposeCommand
$env:APP_VERSION = Get-EnvValue "APP_VERSION" "1.0.0"

# compose "run" replaces the service command, so keep -dir explicit.
$composeArgs = @("--profile", "tools", "run", "--rm", "migrate", "-dir", "/app/migrations") + $MigrateArgs
Invoke-Compose -Arguments $composeArgs
