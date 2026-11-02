# Shared helpers for Windows (PowerShell) deployment scripts.
# Keep this file ASCII-only: Windows PowerShell 5.1 reads BOM-less scripts as ANSI.

function Get-PackageRoot {
    return (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

function Resolve-ComposeCommand {
    if (Get-Command podman-compose -ErrorAction SilentlyContinue) {
        $script:ComposeEngine = "podman"
        return @("podman-compose")
    }
    if (Get-Command podman -ErrorAction SilentlyContinue) {
        $probe = Invoke-NativeCommand -Command "podman" -Arguments @("compose", "version") -Quiet -AllowFail
        if ($probe.ExitCode -eq 0) {
            $script:ComposeEngine = "podman"
            return @("podman", "compose")
        }
    }
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        $probe = Invoke-NativeCommand -Command "docker" -Arguments @("compose", "version") -Quiet -AllowFail
        if ($probe.ExitCode -eq 0) {
            $script:ComposeEngine = "docker"
            return @("docker", "compose")
        }
    }
    throw "No compose command found (need podman-compose, podman compose or docker compose)."
}

function Resolve-ContainerEngine {
    # Follow the engine used by the selected compose command. On machines that
    # have both podman and docker installed, guessing by PATH order is wrong.
    if ($script:ComposeEngine) { return $script:ComposeEngine }
    if (Get-Command podman -ErrorAction SilentlyContinue) { return "podman" }
    if (Get-Command docker -ErrorAction SilentlyContinue) { return "docker" }
    throw "No container engine found (podman or docker)."
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

function Wait-Healthy {
    param([string]$Slot, [int]$Retries = 30)
    for ($i = 1; $i -le $Retries; $i++) {
        $probe = Invoke-NativeCommand -Command $script:Engine -Arguments @(
            "exec", "patnest-api-$Slot", "wget", "-qO-", "http://127.0.0.1:9201/health"
        ) -Quiet -AllowFail
        if ($probe.ExitCode -eq 0) { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Get-Health {
    param([string]$Slot)
    $probe = Invoke-NativeCommand -Command $script:Engine -Arguments @(
        "exec", "patnest-api-$Slot", "wget", "-qO-", "http://127.0.0.1:9201/health"
    ) -Quiet -AllowFail
    if ($probe.ExitCode -ne 0 -or -not $probe.Output) { return $null }
    try {
        $raw = @($probe.Output | ForEach-Object { [string]$_ })
        return (($raw -join "`n") | ConvertFrom-Json)
    } catch {
        return $null
    }
}
