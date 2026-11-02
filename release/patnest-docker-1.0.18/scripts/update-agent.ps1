param(
    [int]$PollSeconds = 3,
    [switch]$Once
)

# Host-side update agent for Windows.
# The API only writes jobs into data\updates\queue; this process performs the
# privileged file replacement and calls the existing release scripts.
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "common.ps1")

$Root = Get-PackageRoot
Set-Location $Root

$Updates = Join-Path $Root "data\updates"
$Queue = Join-Path $Updates "queue"
$Processing = Join-Path $Updates "processing"
$Failed = Join-Path $Updates "failed"
$StatusDir = Join-Path $Updates "status"
$Logs = Join-Path $Updates "logs"
$Backups = Join-Path $Updates "backups"
$PidFile = Join-Path $Updates "agent.pid"
$Heartbeat = Join-Path $Updates "agent.heartbeat"

foreach ($dir in @($Updates, $Queue, $Processing, $Failed, $StatusDir, $Logs, $Backups)) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}

$mutex = New-Object System.Threading.Mutex($false, "Local\PatNestUpdateAgent")
if (-not $mutex.WaitOne(0)) { exit 0 }

$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Write-AgentLog {
    param([string]$JobID, [string]$Message)
    $line = "[{0}] {1}" -f (Get-Date).ToString("yyyy-MM-dd HH:mm:ss"), $Message
    [System.IO.File]::AppendAllText(
        (Join-Path $Logs "$JobID.log"),
        $line + "`r`n",
        $Utf8NoBom
    )
}

function Touch-Heartbeat {
    [System.IO.File]::WriteAllText($Heartbeat, (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ"), $Utf8NoBom)
}

function Read-JobFile {
    param([string]$Path)
    $data = @{}
    foreach ($line in [System.IO.File]::ReadAllLines($Path)) {
        $i = $line.IndexOf("=")
        if ($i -gt 0) {
            $data[$line.Substring(0, $i).Trim()] = $line.Substring($i + 1).Trim()
        }
    }
    return $data
}

function Set-JobStatus {
    param(
        [hashtable]$Job,
        [string]$State,
        [string]$Step,
        [string]$Message,
        [string]$StartedAt = "",
        [string]$FinishedAt = ""
    )
    if (-not $StartedAt) { $StartedAt = $Job["started_at"] }
    if (-not $FinishedAt) { $FinishedAt = $Job["finished_at"] }
    $values = [ordered]@{
        id          = $Job["id"]
        state       = $State
        step        = $Step
        message     = ($Message -replace "[\r\n]+", " ")
        version     = $Job["version"]
        targets     = $Job["targets"]
        source      = $Job["source"]
        created_at  = $Job["created_at"]
        started_at  = $StartedAt
        finished_at = $FinishedAt
    }
    $lines = foreach ($key in $values.Keys) { "$key=$($values[$key])" }
    $file = Join-Path $StatusDir "$($Job['id']).status"
    $tmp = "$file.tmp"
    [System.IO.File]::WriteAllText($tmp, (($lines -join "`n") + "`n"), $Utf8NoBom)
    Move-Item -LiteralPath $tmp -Destination $file -Force
}

function Backup-Current {
    param([hashtable]$Job)
    $dest = Join-Path $Backups $Job["id"]
    New-Item -ItemType Directory -Force -Path (Join-Path $dest "artifacts"), (Join-Path $dest "db\migrations"), (Join-Path $dest "web") | Out-Null
    foreach ($name in @("patnest-api", "patnest-migrate")) {
        $src = Join-Path $Root "artifacts\$name"
        if (Test-Path $src) { Copy-Item -LiteralPath $src -Destination (Join-Path $dest "artifacts\$name") -Force }
    }
    if (Test-Path (Join-Path $Root "web\dist")) {
        Copy-Item -LiteralPath (Join-Path $Root "web\dist") -Destination (Join-Path $dest "web\dist") -Recurse -Force
    }
    Get-ChildItem -Path (Join-Path $Root "db\migrations") -Filter *.sql -ErrorAction SilentlyContinue |
        Copy-Item -Destination (Join-Path $dest "db\migrations") -Force
    return $dest
}

function Restore-Backup {
    param([hashtable]$Job, [string]$BackupDir)
    if (-not (Test-Path $BackupDir)) { return }
    foreach ($name in @("patnest-api", "patnest-migrate")) {
        $src = Join-Path $BackupDir "artifacts\$name"
        if (Test-Path $src) { Copy-Item -LiteralPath $src -Destination (Join-Path $Root "artifacts\$name") -Force }
    }
    $webBackup = Join-Path $BackupDir "web\dist"
    if (Test-Path $webBackup) {
        $webCurrent = Join-Path $Root "web\dist"
        if (Test-Path $webCurrent) { Remove-Item -LiteralPath $webCurrent -Recurse -Force }
        Copy-Item -LiteralPath $webBackup -Destination $webCurrent -Recurse -Force
    }
    Get-ChildItem -Path (Join-Path $BackupDir "db\migrations") -Filter *.sql -ErrorAction SilentlyContinue |
        Copy-Item -Destination (Join-Path $Root "db\migrations") -Force
}

function Update-EnvVersion {
    param([string]$Version)
    $envPath = Join-Path $Root ".env"
    if (-not (Test-Path $envPath)) { return }
    $lines = [System.IO.File]::ReadAllLines($envPath)
    $found = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match "^APP_VERSION=") {
            $lines[$i] = "APP_VERSION=$Version"
            $found = $true
        }
    }
    if (-not $found) { $lines += "APP_VERSION=$Version" }
    [System.IO.File]::WriteAllLines($envPath, $lines, $Utf8NoBom)
}

function Invoke-ReleaseScript {
    param([string]$JobID, [string]$Script, [string]$Version)
    Write-AgentLog $JobID "run $Script -Version $Version"
    Touch-Heartbeat
    $oldEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $output = @()
    try {
        $output = @(& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $Root "scripts\$Script") -Version $Version 2>&1)
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $oldEap
    }
    foreach ($line in @($output)) {
        Write-AgentLog $JobID ([string]$line)
    }
    Write-AgentLog $JobID "$Script exit=$code"
    if ($code -ne 0) { throw "$Script failed with exit code $code" }
}

function Invoke-UpdateJob {
    param([string]$JobPath)
    $id = [System.IO.Path]::GetFileNameWithoutExtension($JobPath)
    if ($id -notmatch "^[0-9]{8}-[0-9]{6}-[0-9a-f]{8}$") {
        Write-Host "[update-agent] invalid job filename: $id" -ForegroundColor Yellow
        return
    }
    $claimed = Join-Path $Processing "$id.job"
    try {
        Move-Item -LiteralPath $JobPath -Destination $claimed -Force
    } catch {
        return
    }

    $job = Read-JobFile $claimed
    $job["id"] = $id
    $targets = @($job["targets"].Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    $version = $job["version"]
    $staging = $job["staging"]
    $started = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    $job["started_at"] = $started
    $job["finished_at"] = ""

    $backupDir = ""
    try {
        if ($version -notmatch "^[0-9A-Za-z][0-9A-Za-z._-]{0,63}$") { throw "Invalid version" }
        $stagingRoot = [System.IO.Path]::GetFullPath((Join-Path $Updates "staging"))
        if ([System.IO.Path]::IsPathRooted($staging)) { throw "Staging path must be relative" }
        $stagingFull = [System.IO.Path]::GetFullPath((Join-Path $Updates $staging))
        if (-not $stagingFull.StartsWith($stagingRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Staging path escapes update root"
        }
        if (-not (Test-Path $stagingFull)) { throw "Staging directory not found" }

        Set-JobStatus $job "running" "backup" "Backup current release" $started
        Write-AgentLog $id "job $version targets=$($targets -join ',')"
        $backupDir = Backup-Current $job

        Set-JobStatus $job "running" "copy" "Copy update files" $started
        if ($targets -contains "api") {
            Copy-Item -LiteralPath (Join-Path $stagingFull "artifacts\patnest-api") -Destination (Join-Path $Root "artifacts\patnest-api") -Force
            Copy-Item -LiteralPath (Join-Path $stagingFull "artifacts\patnest-migrate") -Destination (Join-Path $Root "artifacts\patnest-migrate") -Force
        }
        if ($targets -contains "db" -or $targets -contains "api") {
            $migrations = Join-Path $stagingFull "db\migrations"
            if (Test-Path $migrations) {
                Get-ChildItem -Path $migrations -Filter *.sql | Copy-Item -Destination (Join-Path $Root "db\migrations") -Force
            }
        }
        if ($targets -contains "web") {
            $webSrc = Join-Path $stagingFull "web\dist"
            $webDst = Join-Path $Root "web\dist"
            if (Test-Path $webDst) { Remove-Item -LiteralPath $webDst -Recurse -Force }
            New-Item -ItemType Directory -Force -Path $webDst | Out-Null
            Copy-Item -Path (Join-Path $webSrc "*") -Destination $webDst -Recurse -Force
        }

        if ($targets -contains "api") {
            Set-JobStatus $job "running" "release-api" "Build backend and release (blue-green)" $started
            Invoke-ReleaseScript $id "release-api.ps1" $version
        }
        if ($targets -contains "web") {
            Set-JobStatus $job "running" "release-web" "Rebuild admin panel" $started
            Invoke-ReleaseScript $id "release-web.ps1" $version
        }

        Update-EnvVersion $version
        $finished = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
        Set-JobStatus $job "success" "done" "Update finished" $started $finished
        Write-AgentLog $id "success"
        Remove-Item -LiteralPath $claimed -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $stagingFull -Recurse -Force -ErrorAction SilentlyContinue
    } catch {
        $message = $_.Exception.Message
        Write-AgentLog $id "FAILED: $message"
        if ($backupDir) {
            try { Restore-Backup $job $backupDir } catch { Write-AgentLog $id "restore failed: $($_.Exception.Message)" }
        }
        $finished = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
        Set-JobStatus $job "failed" "failed" $message $started $finished
        Move-Item -LiteralPath $claimed -Destination (Join-Path $Failed "$id.job") -Force -ErrorAction SilentlyContinue
    }
}

function Recover-Interrupted {
    Get-ChildItem -Path $Processing -Filter *.job -ErrorAction SilentlyContinue | ForEach-Object {
        $id = $_.BaseName
        if ($id -notmatch "^[0-9]{8}-[0-9]{6}-[0-9a-f]{8}$") { return }
        $job = Read-JobFile $_.FullName
        $job["id"] = $id
        $state = ""
        $statusFile = Join-Path $StatusDir "$id.status"
        if (Test-Path $statusFile) { $state = (Read-JobFile $statusFile)["state"] }
        if ($state -eq "success") {
            Move-Item -LiteralPath $_.FullName -Destination (Join-Path $Failed "$id.job") -Force -ErrorAction SilentlyContinue
            return
        }
        $started = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
        $job["started_at"] = $started
        Write-AgentLog $id "agent restarted while job was running"
        $backupDir = Join-Path $Backups $id
        if (Test-Path $backupDir) {
            try { Restore-Backup $job $backupDir } catch { Write-AgentLog $id "restore failed: $($_.Exception.Message)" }
        }
        Set-JobStatus $job "failed" "interrupted" "Update agent restarted; task interrupted and previous files restored" $started $started
        Move-Item -LiteralPath $_.FullName -Destination (Join-Path $Failed "$id.job") -Force -ErrorAction SilentlyContinue
    }
}

if (Test-Path $PidFile) {
    $oldPid = (Get-Content $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
    if ($oldPid) {
        $proc = Get-Process -Id $oldPid -ErrorAction SilentlyContinue
        if ($proc) { $mutex.ReleaseMutex(); exit 0 }
    }
}

[System.IO.File]::WriteAllText($PidFile, "$PID", $Utf8NoBom)
Touch-Heartbeat
Write-Host "[update-agent] watching $Queue (pid=$PID)" -ForegroundColor Cyan

try {
    Recover-Interrupted
    if ($Once) {
        Get-ChildItem -Path $Queue -Filter *.job -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime |
            ForEach-Object { Invoke-UpdateJob $_.FullName }
        return
    }
    while ($true) {
        Touch-Heartbeat
        Get-ChildItem -Path $Queue -Filter *.job -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime |
            ForEach-Object { Invoke-UpdateJob $_.FullName }
        Start-Sleep -Seconds $PollSeconds
    }
} finally {
    Remove-Item -LiteralPath $PidFile -Force -ErrorAction SilentlyContinue
    $mutex.ReleaseMutex()
}
