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

Write-Host "[install] Starting redis / nginx / api-blue" -ForegroundColor Cyan
Invoke-Compose @("--profile", "app", "up", "-d", "redis", "nginx", "api-blue")

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

# ---- 更新代理（容器化）--------------------------------------------------
# 代理跑在容器里：挂 docker.sock + 把部署包挂到 /pkg，容器内的 entrypoint 会反查
# daemon 侧真实路径，再以它作为 compose 项目目录，所以 Windows 不再需要同路径挂载。
# 不在宿主机上启动任何常驻进程，也不再写开机启动快捷方式。
$agentMode = Get-EnvValue "AGENT_MODE" "container"

function Set-EnvValue {
    param([string]$Key, [string]$Value)
    $lines = @()
    if (Test-Path ".env") { $lines = @(Get-Content ".env" -Encoding UTF8) }
    $found = $false
    $out = foreach ($line in $lines) {
        if ($line -match "^\s*$([regex]::Escape($Key))\s*=") {
            $found = $true
            "$Key=$Value"
        } else {
            $line
        }
    }
    if (-not $found) { $out = @($out) + "$Key=$Value" }
    Set-Content -Path ".env" -Value $out -Encoding UTF8
}

function Stop-HostAgent {
    # 清理上一版遗留在宿主机上的 PowerShell 代理，避免两个消费者抢同一个 queue。
    $pidFile = Join-Path $updates "agent.pid"
    if (Test-Path $pidFile) {
        $agentPid = Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($agentPid) { Stop-Process -Id $agentPid -ErrorAction SilentlyContinue }
        Remove-Item $pidFile -ErrorAction SilentlyContinue
    }
    $startup = [Environment]::GetFolderPath("Startup")
    if ($startup) {
        Remove-Item (Join-Path $startup "PatNestUpdateAgent.lnk") -ErrorAction SilentlyContinue
    }
}

switch ($agentMode) {
    "none" {
        Invoke-Compose @("--profile", "agent", "stop", "update-agent") -AllowFail
        Stop-HostAgent
        Write-Host "[install] AGENT_MODE=none: update agent not started." -ForegroundColor Yellow
    }
    "host" {
        Invoke-Compose @("--profile", "agent", "stop", "update-agent") -AllowFail
        $agent = Join-Path $root "scripts\update-agent.ps1"
        if (Test-Path $agent) {
            Start-Process -FilePath "powershell.exe" `
                -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$agent`"") `
                -WindowStyle Hidden
            Write-Host "[install] Update agent started as host process." -ForegroundColor Yellow
        }
    }
    default {
        # Windows 路径写进 .env 时统一用正斜杠，compose 才能正确拆出容器内目标路径。
        $hostPath = ($root -replace '\\', '/')
        Set-EnvValue "PACKAGE_HOST_PATH" $hostPath
        Stop-HostAgent
        Invoke-Compose @("--profile", "agent", "stop", "update-agent") -AllowFail

        Write-Host "[install] Building update agent image" -ForegroundColor Cyan
        Invoke-Compose @("--profile", "agent", "build", "--pull", "update-agent")

        Write-Host "[install] Starting update agent container" -ForegroundColor Cyan
        Invoke-Compose @("--profile", "agent", "up", "-d", "update-agent")

        Start-Sleep -Seconds 2
        $psOut = Invoke-NativeCommand -Command $script:Engine -Arguments @(
            "ps", "--filter", "name=patnest-update-agent", "--format", "{{.Names}}"
        ) -Quiet -AllowFail
        $running = (@($psOut.Output | ForEach-Object { [string]$_ }) -join "`n")
        if ($running -notmatch "patnest-update-agent") {
            Invoke-NativeCommand -Command $script:Engine -Arguments @(
                "logs", "--tail", "40", "patnest-update-agent"
            ) -AllowFail | Out-Null
            throw "Update agent container did not stay up. See logs above."
        }
        Write-Host "[install] Update agent container is running." -ForegroundColor Cyan
    }
}

Write-Host "[install] Done. Open http://localhost:$publicPort/" -ForegroundColor Green
