param(
    [string]$Version = "",
    [int]$HealthRetries = 60,
    [switch]$NoAgentAutostart
)

# Windows equivalent of scripts/install.sh
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "common.ps1")

$root = Get-PackageRoot
Set-Location $root

if (-not (Test-Path ".env")) {
    throw "Missing .env. Run: Copy-Item .env.example .env, then fill DB / MinIO / JWT / Redis."
}
if (-not (Test-Path "artifacts\patnest-api")) { throw "Missing artifacts\patnest-api" }
if (-not (Test-Path "web\dist\index.html")) { throw "Missing web\dist" }

$dbPassword = Get-EnvValue "DB_PASSWORD"
# U+8BF7 is the first char of the Chinese placeholder text in .env.example.
if (-not $dbPassword -or $dbPassword.StartsWith([string][char]0x8BF7)) {
    throw "DB_PASSWORD in .env is empty or still the placeholder. Fill in the PostgreSQL password first."
}
$jwtSecret = Get-EnvValue "JWT_SECRET"
if (-not $jwtSecret -or $jwtSecret.StartsWith([string][char]0x8BF7) -or $jwtSecret.Length -lt 32) {
    Write-Warning "JWT_SECRET is empty, a placeholder, or shorter than 32 chars. Set a strong random value in .env."
}

if (-not $Version) { $Version = Get-EnvValue "APP_VERSION" "1.0.0" }
$publicPort = Get-EnvValue "PUBLIC_PORT" "9200"
$script:ComposeCmd = Resolve-ComposeCommand
$script:Engine = Resolve-ContainerEngine
$env:APP_VERSION = $Version

New-Item -ItemType Directory -Force -Path "data\redis" | Out-Null

Write-Host "[install] Building images patnest-api:$Version / patnest-web:$Version" -ForegroundColor Cyan
Invoke-Compose @("--profile", "app", "build", "--pull", "api-blue", "nginx")

Write-Host "[install] Preparing update directory" -ForegroundColor Cyan
Invoke-Compose @("--profile", "tools", "run", "--rm", "updates-init")

Write-Host "[install] Running database migrations" -ForegroundColor Cyan
Invoke-Compose @("--profile", "tools", "run", "--rm", "migrate")

Write-Host "[install] Starting redis / srs / nginx / api-blue" -ForegroundColor Cyan
Invoke-Compose @("--profile", "app", "up", "-d", "redis", "srs", "nginx", "api-blue")

Write-Host "[install] Waiting for api-blue health check ..." -ForegroundColor Cyan
if (-not (Wait-Healthy -Slot "blue" -Retries $HealthRetries)) {
    Invoke-NativeCommand -Command $script:Engine -Arguments @("logs", "--tail", "80", "patnest-api-blue") -AllowFail | Out-Null
    throw "api-blue health check failed. Check .env DB settings and: $($script:Engine) logs patnest-api-blue"
}

$health = Get-Health -Slot "blue"
if ($health) {
    Write-Host ("[install] health: db={0} minio={1} redis={2}" -f $health.db, $health.minio, $health.redis)
    if (-not $health.minio) {
        Write-Warning "MinIO is not reachable. Uploads will fail until MINIO_* settings are fixed."
    }
    if (-not $health.redis) {
        Write-Warning "Redis is not reachable. Login rate limiting is disabled."
    }
}

Invoke-Compose @("--profile", "app", "ps")

$updates = Join-Path $root "data\updates"
foreach ($dir in @("tmp", "inbox", "manual", "queue", "processing", "failed", "status", "staging", "logs", "backups")) {
    New-Item -ItemType Directory -Force -Path (Join-Path $updates $dir) | Out-Null
}

$agent = Join-Path $root "scripts\update-agent.ps1"
if (Test-Path $agent) {
    $pidFile = Join-Path $updates "agent.pid"
    $running = $false
    if (Test-Path $pidFile) {
        $agentPid = Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($agentPid -and (Get-Process -Id $agentPid -ErrorAction SilentlyContinue)) { $running = $true }
    }
    if (-not $running) {
        Start-Process -FilePath "powershell.exe" `
            -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$agent`"") `
            -WindowStyle Hidden
        Write-Host "[install] Update agent started in background." -ForegroundColor Cyan
    }
    if (-not $NoAgentAutostart) {
        $startup = [Environment]::GetFolderPath("Startup")
        if ($startup) {
            $shortcut = Join-Path $startup "PatNestUpdateAgent.lnk"
            $shell = New-Object -ComObject WScript.Shell
            $link = $shell.CreateShortcut($shortcut)
            $link.TargetPath = "powershell.exe"
            $link.Arguments = "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$agent`""
            $link.WorkingDirectory = $root
            $link.Save()
            Write-Host "[install] Update agent autostart: $shortcut" -ForegroundColor Cyan
        }
    }
}

Write-Host "[install] Done. Open http://localhost:$publicPort/" -ForegroundColor Green
