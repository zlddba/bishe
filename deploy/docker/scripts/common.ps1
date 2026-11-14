# Shared helpers for Windows (PowerShell) deployment scripts.
# Keep this file ASCII-only: Windows PowerShell 5.1 reads BOM-less scripts as ANSI.

function Get-PackageRoot {
    return (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

function Resolve-ComposeCommand {
    # Priority: -Engine / PATNEST_ENGINE env var / PATNEST_ENGINE in .env / auto.
    # Auto means docker first, podman as fallback, error when neither is usable.
    $requested = $env:PATNEST_ENGINE
    if (-not $requested) { $requested = Get-EnvValue "PATNEST_ENGINE" "auto" }
    $requested = $requested.ToLower().Trim()

    $dockerOk = $false
    $podmanCmd = $null

    if (Get-Command docker -ErrorAction SilentlyContinue) {
        $version = Invoke-NativeCommand -Command "docker" -Arguments @("compose", "version") -Quiet -AllowFail
        $info = Invoke-NativeCommand -Command "docker" -Arguments @("info") -Quiet -AllowFail
        $dockerOk = ($version.ExitCode -eq 0 -and $info.ExitCode -eq 0)
    }
    if (Get-Command podman -ErrorAction SilentlyContinue) {
        $version = Invoke-NativeCommand -Command "podman" -Arguments @("compose", "version") -Quiet -AllowFail
        $info = Invoke-NativeCommand -Command "podman" -Arguments @("info") -Quiet -AllowFail
        if ($version.ExitCode -eq 0 -and $info.ExitCode -eq 0) {
            $podmanCmd = @("podman", "compose")
        }
    }
    if (-not $podmanCmd -and (Get-Command podman-compose -ErrorAction SilentlyContinue)) {
        $info = Invoke-NativeCommand -Command "podman" -Arguments @("info") -Quiet -AllowFail
        if ($info.ExitCode -eq 0) { $podmanCmd = @("podman-compose") }
    }

    switch ($requested) {
        "docker" {
            if (-not $dockerOk) {
                throw "docker requested but not usable (need docker CLI, compose plugin and a running daemon)."
            }
            $script:ComposeEngine = "docker"
            return @("docker", "compose")
        }
        "podman" {
            if (-not $podmanCmd) {
                throw "podman requested but not usable (need podman compose or podman-compose plus podman info)."
            }
            $script:ComposeEngine = "podman"
            return $podmanCmd
        }
        "auto" {
            if ($dockerOk) {
                $script:ComposeEngine = "docker"
                return @("docker", "compose")
            }
            if ($podmanCmd) {
                Write-Host "[engine] docker not available, using podman" -ForegroundColor Yellow
                $script:ComposeEngine = "podman"
                return $podmanCmd
            }
            if (Get-Command docker -ErrorAction SilentlyContinue) {
                Write-Host "  docker CLI found but the daemon is not running; start Docker and retry." -ForegroundColor Yellow
            }
            throw "Neither docker nor podman is usable. Install one and retry, or pass -Engine to select one."
        }
        default {
            throw "Unknown engine '$requested' (use docker, podman or auto)."
        }
    }
}

function Resolve-ContainerEngine {
    # Follow the engine used by the selected compose command. On machines that
    # have both installed, guessing by PATH order is wrong; docker is the default.
    if ($script:ComposeEngine) { return $script:ComposeEngine }
    if (Get-Command docker -ErrorAction SilentlyContinue) { return "docker" }
    if (Get-Command podman -ErrorAction SilentlyContinue) { return "podman" }
    throw "No container engine found (docker or podman)."
}

# Stop early with an actionable hint when the container engine is down.
function Assert-EngineRunning {
    param([string]$Step = "[install]")

    $engine = Resolve-ContainerEngine
    # Plain "info" works for both docker and podman; the --format field
    # {{.ServerVersion}} only exists on docker and would misreport podman.
    $probe = Invoke-NativeCommand -Command $engine -Arguments @("info") -Quiet -AllowFail
    if ($probe.ExitCode -eq 0) { return $true }

    Write-Host ""
    Write-Host "$Step container engine is not running; script stopped." -ForegroundColor Red
    if ($engine -eq "docker") {
        Write-Host "  Docker Desktop is not started, or the engine is still starting (tray icon shows starting)." -ForegroundColor Yellow
        Write-Host "  Start Docker Desktop, wait until fully ready, then rerun this script." -ForegroundColor Yellow
        Write-Host "  If it still fails, the engine likely crashed: restart Docker Desktop and retry." -ForegroundColor Yellow
    } else {
        Write-Host "  Cannot reach $engine (socket down or insufficient permissions)." -ForegroundColor Yellow
        Write-Host "  Check: systemctl status podman, or verify podman.sock path and permissions." -ForegroundColor Yellow
    }
    Write-Host ""
    exit 1
}

# Compose bind-mounts nginx/upstream.conf and nginx/api_proxy.conf as live files.
# If a source is missing Docker creates a directory instead and the mount fails:
# "not a directory: are you trying to mount a directory onto a file (or vice-versa)?"
function Ensure-NginxMountFiles {
    $files = @{
        "nginx\api_proxy.conf" = "proxy_pass http://patnest_api;`n"
        "nginx\upstream.conf"  = "server api-blue:9201 max_fails=2 fail_timeout=5s;`n"
    }
    foreach ($rel in $files.Keys) {
        $path = Join-Path (Get-PackageRoot) $rel
        $dir = Split-Path -Parent $path
        if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }

        if (Test-Path $path -PathType Container) {
            $items = @(Get-ChildItem -Path $path -Force -ErrorAction SilentlyContinue)
            if ($items.Count -gt 0) {
                Write-Warning "$rel is a non-empty directory; check manually before continuing: $path"
                continue
            }
            Remove-Item -Path $path -Recurse -Force
            Write-Host "[fix] removed directory created by Docker at $rel" -ForegroundColor Yellow
        }

        if (-not (Test-Path $path -PathType Leaf)) {
            Set-Content -Path $path -Value $files[$rel] -NoNewline -Encoding ascii
            Write-Host "[fix] created missing mount source file $rel" -ForegroundColor Yellow
        }
    }
}

function Invoke-NativeCommand {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [string[]]$Arguments = @(),
        [switch]$AllowFail,
        [switch]$Quiet
    )
    # Windows PowerShell 5.1 turns native stderr lines into terminating errors
    # when ErrorActionPreference is Stop. Docker/Compose write normal progress
    # and nginx output to stderr, so capture both streams under Continue and
    # decide success from the real process exit code.
    $oldEap = $ErrorActionPreference
    $oldEncoding = $null
    try { $oldEncoding = [Console]::OutputEncoding } catch {}
    $ErrorActionPreference = "Continue"
    $output = @()
    try {
        # Docker outputs UTF-8; PS 5.1 decodes native output with the OEM code
        # page and garbles Chinese text. Force UTF-8 for the duration of the call.
        try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
        $output = @(& $Command @Arguments 2>&1)
        $code = $LASTEXITCODE
    } finally {
        if ($oldEncoding) {
            try { [Console]::OutputEncoding = $oldEncoding } catch {}
        }
        $ErrorActionPreference = $oldEap
    }
    if (-not $Quiet) {
        foreach ($line in $output) {
            Write-Host ([string]$line)
        }
    }
    if (-not $AllowFail -and $code -ne 0) {
        throw "command failed with exit code ${code}: $Command $($Arguments -join ' ')"
    }
    return [pscustomobject]@{ ExitCode = $code; Output = $output }
}

function Invoke-Compose {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [switch]$AllowFail
    )
    if (-not $script:ComposeCmd) { $script:ComposeCmd = Resolve-ComposeCommand }
    $cmd = $script:ComposeCmd[0]
    $prefix = @()
    if ($script:ComposeCmd.Count -gt 1) {
        $prefix = $script:ComposeCmd[1..($script:ComposeCmd.Count - 1)]
    }
    $cmdArgs = @($prefix) + @($Arguments)
    Invoke-NativeCommand -Command $cmd -Arguments $cmdArgs -AllowFail:$AllowFail | Out-Null
}

function Get-EnvValue {
    param([string]$Key, [string]$Default = "")
    if (-not (Test-Path ".env")) { return $Default }
    foreach ($line in Get-Content ".env" -Encoding UTF8) {
        if ($line -match "^\s*$([regex]::Escape($Key))\s*=\s*(.*)$") {
            $value = $Matches[1].Trim().Trim('"').Trim("'")
            if ($value) { return $value }
        }
    }
    return $Default
}

function Get-CurrentSlot {
    $conf = Join-Path (Get-PackageRoot) "nginx\upstream.conf"
    if (-not (Test-Path $conf)) { return "blue" }
    # Only match the active "server" directive. Comments in this file also
    # mention both slot names, so an unanchored search always returns blue.
    $match = Select-String -Path $conf -Pattern "^\s*server\s+api-(blue|green)\s*:" | Select-Object -First 1
    if ($match -and $match.Matches.Count -gt 0) {
        return $match.Matches[0].Groups[1].Value.ToLower()
    }
    return "blue"
}

function Set-UpstreamSlot {
    param([string]$Slot)
    if ($Slot -notin @("blue", "green")) { throw "Invalid slot: $Slot" }
    $conf = Join-Path (Get-PackageRoot) "nginx\upstream.conf"
    # nginx does not strip a UTF-8 BOM, so write ASCII without BOM.
    $content = "# Active slot: api-$Slot`nserver api-${Slot}:9201 max_fails=2 fail_timeout=5s;`n"
    Set-Content -Path $conf -Value $content -NoNewline -Encoding ascii
}

function Get-ApiScheme {
    # Backend protocol: http (nginx terminates TLS) or https (backend does).
    $scheme = (Get-EnvValue "SERVER_SCHEME" "http").ToLower()
    if ($scheme -eq "https") { return "https" }
    return "http"
}

function Set-ApiProxy {
    # nginx -> backend proxy protocol must match the backend's own protocol.
    # A plain-text handshake against a TLS-only upstream fails with 502.
    $scheme = Get-ApiScheme
    $conf = Join-Path (Get-PackageRoot) "nginx\api_proxy.conf"
    $content = "proxy_pass ${scheme}://patnest_api;`n"
    Set-Content -Path $conf -Value $content -NoNewline -Encoding ascii
    return $scheme
}

function Get-HealthUrl {
    param([string]$Slot)
    # Probe from inside the API container, so it always uses 127.0.0.1.
    $scheme = Get-ApiScheme
    if ($scheme -eq "https") {
        return "https://127.0.0.1:9201/health"
    }
    return "http://127.0.0.1:9201/health"
}

function Get-HealthWgetArgs {
    # busybox wget needs --no-check-certificate: the cert is issued for the
    # public domain, not for 127.0.0.1.
    if ((Get-ApiScheme) -eq "https") {
        return @("wget", "-qO-", "--no-check-certificate")
    }
    return @("wget", "-qO-")
}

function Wait-Healthy {
    param([string]$Slot, [int]$Retries = 30)
    $wgetArgs = Get-HealthWgetArgs
    $url = Get-HealthUrl $Slot
    for ($i = 1; $i -le $Retries; $i++) {
        $probe = Invoke-NativeCommand -Command $script:Engine -Arguments (@("exec", "patnest-api-$Slot") + $wgetArgs + @($url)) -Quiet -AllowFail
        if ($probe.ExitCode -eq 0) { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Get-Health {
    param([string]$Slot)
    $wgetArgs = Get-HealthWgetArgs
    $url = Get-HealthUrl $Slot
    $probe = Invoke-NativeCommand -Command $script:Engine -Arguments (@("exec", "patnest-api-$Slot") + $wgetArgs + @($url)) -Quiet -AllowFail
    if ($probe.ExitCode -ne 0 -or -not $probe.Output) { return $null }
    try {
        $raw = @($probe.Output | ForEach-Object { [string]$_ })
        return (($raw -join "`n") | ConvertFrom-Json)
    } catch {
        return $null
    }
}
