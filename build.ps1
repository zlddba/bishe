[CmdletBinding()]
param(
    [string]$Version = "",
    [ValidateSet("amd64", "arm64")]
    [string]$Arch = "amd64",
    [switch]$SkipWeb,
    [switch]$SkipGo,
    [switch]$NoZip
)

# One-click builder: frontend dist + Go binaries + container package zip.
$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$WebDir = Join-Path $RootDir "webadmin\patnestServerAdminWeb"
$SrcDir = Join-Path $RootDir "server\patnestServer"
$TplDir = Join-Path $RootDir "deploy\docker"
$ReleaseDir = Join-Path $RootDir "release"

function Write-Step([string]$Message) {
    Write-Host "[build] $Message" -ForegroundColor Cyan
}

function Fail([string]$Message) {
    Write-Host "[build] $Message" -ForegroundColor Red
    exit 1
}

function Get-Sha256([string]$Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

if (-not $Version) {
    $pkgJson = Get-Content (Join-Path $WebDir "package.json") -Raw | ConvertFrom-Json
    $Version = $pkgJson.version
}
if (-not $Version) { $Version = "1.0.0" }

$Commit = "unknown"
try {
    $gitOut = & git -C $RootDir rev-parse --short HEAD 2>$null
    if ($LASTEXITCODE -eq 0 -and $gitOut) { $Commit = "$gitOut".Trim() }
} catch {}

$BuildTime = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")

$PkgName = "patnest-docker-$Version"
if ($Arch -ne "amd64") { $PkgName = "$PkgName-$Arch" }
$PkgDir = Join-Path $ReleaseDir $PkgName

if (-not (Get-Command go -ErrorAction SilentlyContinue)) { Fail "go not found in PATH" }
$Npm = $null
if (Get-Command npm.cmd -ErrorAction SilentlyContinue) { $Npm = "npm.cmd" }
elseif (Get-Command npm -ErrorAction SilentlyContinue) { $Npm = "npm" }

Write-Step "version=$Version commit=$Commit arch=$Arch"

$StashDir = ""
if ($SkipGo -and (Test-Path (Join-Path $PkgDir "artifacts\patnest-api"))) {
    $StashDir = Join-Path $env:TEMP "patnest-artifacts-$Version-$Arch"
    if (Test-Path $StashDir) { Remove-Item $StashDir -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $StashDir | Out-Null
    Copy-Item (Join-Path $PkgDir "artifacts\*") $StashDir -Force
}

if (Test-Path $PkgDir) {
    $resolved = (Resolve-Path $PkgDir).Path
    if (-not $resolved.StartsWith((Resolve-Path $ReleaseDir).Path)) { Fail "refuse to clean unexpected path: $PkgDir" }
    Remove-Item $PkgDir -Recurse -Force
}

$dirs = @(
    $PkgDir,
    (Join-Path $PkgDir "artifacts"),
    (Join-Path $PkgDir "web"),
    (Join-Path $PkgDir "db\migrations"),
    (Join-Path $PkgDir "docker\nginx"),
    (Join-Path $PkgDir "nginx"),
    (Join-Path $PkgDir "scripts")
)
foreach ($d in $dirs) { New-Item -ItemType Directory -Force -Path $d | Out-Null }

if (-not $SkipWeb) {
    Write-Step "Building admin panel (vite) ..."
    Push-Location $WebDir
    try {
        $viteJs = Join-Path $WebDir "node_modules\vite\bin\vite.js"
        if (Test-Path $viteJs) {
            $env:GIT_COMMIT = $Commit
            $env:APP_VERSION = $Version
            & node $viteJs build
            if ($LASTEXITCODE -ne 0) { throw "vite build failed" }
        } elseif ($Npm -and -not (Test-Path "node_modules")) {
            & $Npm ci --no-audit --no-fund
            if ($LASTEXITCODE -ne 0) { throw "npm ci failed" }
            $env:GIT_COMMIT = $Commit
            $env:APP_VERSION = $Version
            & $Npm run build
            if ($LASTEXITCODE -ne 0) { throw "npm run build failed" }
        } elseif ($Npm) {
            $env:GIT_COMMIT = $Commit
            $env:APP_VERSION = $Version
            & $Npm run build
            if ($LASTEXITCODE -ne 0) { throw "npm run build failed" }
        } else {
            throw "neither local vite nor npm found; run npm ci in $WebDir first"
        }
    } finally {
        Pop-Location
    }
} else {
    Write-Step "Skip web build, reuse existing dist"
}

$distDir = Join-Path $WebDir "dist"
if (-not (Test-Path (Join-Path $distDir "index.html"))) { Fail "missing web dist: $distDir" }

if (-not $SkipGo) {
    Write-Step "Building Go binaries for linux/$Arch ..."
    $oldGoos = $env:GOOS; $oldGoarch = $env:GOARCH; $oldCgo = $env:CGO_ENABLED
    $env:GOOS = "linux"; $env:GOARCH = $Arch; $env:CGO_ENABLED = "0"
    try {
        $ldflags = "-s -w -X patnestServer/internal/version.Version=$Version -X patnestServer/internal/version.Commit=$Commit -X patnestServer/internal/version.BuildTime=$BuildTime"
        Push-Location $SrcDir
        try {
            & go build -trimpath -ldflags $ldflags -o (Join-Path $PkgDir "artifacts\patnest-api") ./cmd/api
            if ($LASTEXITCODE -ne 0) { throw "go build api failed" }
            & go build -trimpath -ldflags $ldflags -o (Join-Path $PkgDir "artifacts\patnest-migrate") ./cmd/migrate
            if ($LASTEXITCODE -ne 0) { throw "go build migrate failed" }
        } finally {
            Pop-Location
        }
    } finally {
        $env:GOOS = $oldGoos; $env:GOARCH = $oldGoarch; $env:CGO_ENABLED = $oldCgo
    }
} else {
    Write-Step "Skip Go build, reuse existing artifacts"
    if ($StashDir -and (Test-Path (Join-Path $StashDir "patnest-api"))) {
        Copy-Item (Join-Path $StashDir "*") (Join-Path $PkgDir "artifacts") -Force
    } else {
        $prev = Get-ChildItem $ReleaseDir -Directory -Filter "patnest-docker-*" -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | Select-Object -First 1
        if (-not $prev -or -not (Test-Path (Join-Path $prev.FullName "artifacts\patnest-api"))) {
            Fail "no previous artifacts to reuse"
        }
        Copy-Item (Join-Path $prev.FullName "artifacts\*") (Join-Path $PkgDir "artifacts") -Force
    }
}

Write-Step "Assembling container package ..."
Copy-Item (Join-Path $TplDir "compose.yaml") $PkgDir -Force
Copy-Item (Join-Path $TplDir ".env.example") $PkgDir -Force
Copy-Item (Join-Path $TplDir ".dockerignore") $PkgDir -Force
Copy-Item (Join-Path $TplDir "README.md") $PkgDir -Force
Copy-Item (Join-Path $TplDir "Dockerfile.api") (Join-Path $PkgDir "docker") -Force
Copy-Item (Join-Path $TplDir "Dockerfile.web") (Join-Path $PkgDir "docker") -Force
Copy-Item (Join-Path $TplDir "Dockerfile.agent") (Join-Path $PkgDir "docker") -Force
# 入口脚本必须落在包根：Dockerfile.agent 的 COPY 路径相对构建上下文（部署包根目录）。
Copy-Item (Join-Path $TplDir "agent-entrypoint.sh") $PkgDir -Force
Copy-Item (Join-Path $TplDir "nginx\default.conf") (Join-Path $PkgDir "docker\nginx") -Force
# locations.conf 是 9200/443 共用规则，Dockerfile.web 会 COPY 它
Copy-Item (Join-Path $TplDir "nginx\locations.conf") (Join-Path $PkgDir "docker\nginx") -Force
# 证书热加载守护，会被放进 /docker-entrypoint.d/
Copy-Item (Join-Path $TplDir "nginx\tls-watch.sh") (Join-Path $PkgDir "docker\nginx") -Force
Copy-Item (Join-Path $TplDir "nginx\upstream.conf") (Join-Path $PkgDir "nginx") -Force
# 证书目录是运行时挂载源（compose 的 ./nginx/tls），必须带 https.conf 占位，
# 否则新版 default.conf 的 include 找不到文件，nginx 起不来
$tlsDir = Join-Path $PkgDir "nginx\tls"
New-Item -ItemType Directory -Force -Path $tlsDir | Out-Null
Copy-Item (Join-Path $TplDir "nginx\tls\https.conf") $tlsDir -Force
Copy-Item (Join-Path $TplDir "scripts\*.sh") (Join-Path $PkgDir "scripts") -Force
Copy-Item (Join-Path $TplDir "scripts\*.ps1") (Join-Path $PkgDir "scripts") -Force
Copy-Item $distDir (Join-Path $PkgDir "web\dist") -Recurse -Force
Copy-Item (Join-Path $SrcDir "serverdb\init.sql") (Join-Path $PkgDir "db") -Force
Copy-Item (Join-Path $SrcDir "serverdb\migrations\*.sql") (Join-Path $PkgDir "db\migrations") -Force

Write-Step "Writing update manifest ..."
$manifestFiles = [ordered]@{}
foreach ($rel in @("artifacts\patnest-api", "artifacts\patnest-migrate", "web\dist\index.html", "web\dist\version.json")) {
    $full = Join-Path $PkgDir $rel
    if (Test-Path $full) {
        $manifestFiles[($rel -replace '\\', '/')] = [ordered]@{
            size = (Get-Item -LiteralPath $full).Length
            sha256 = Get-Sha256 $full
        }
    }
}
$manifest = [ordered]@{
    schema = 1
    version = $Version
    commit = $Commit
    build_time = $BuildTime
    arch = "linux/$Arch"
    targets = @("api", "web", "db")
    files = $manifestFiles
}
$manifestJson = $manifest | ConvertTo-Json -Depth 6
[System.IO.File]::WriteAllText(
    (Join-Path $PkgDir "update-manifest.json"),
    $manifestJson,
    (New-Object System.Text.UTF8Encoding($false))
)

Write-Step "Package directory: $PkgDir"

if (-not $NoZip) {
    $zipPath = Join-Path $ReleaseDir "$PkgName.zip"
    if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
    Compress-Archive -Path $PkgDir -DestinationPath $zipPath -CompressionLevel Optimal
    $sizeMb = [math]::Round((Get-Item $zipPath).Length / 1MB, 2)
    Write-Step "Archive: $zipPath ($sizeMb MB)"
}

Write-Host ""
Write-Host "Done." -ForegroundColor Green
Write-Host "  version : $Version"
Write-Host "  commit  : $Commit"
Write-Host "  arch    : linux/$Arch"
Write-Host "  package : $PkgDir"
Write-Host ""
Write-Host "Server side:"
Write-Host "  unzip $PkgName.zip ; cd $PkgName"
Write-Host "  cp .env.example .env"
Write-Host "  bash scripts/install.sh"
Write-Host ""
Write-Host "Windows server side (PowerShell):"
Write-Host "  Expand-Archive $PkgName.zip -DestinationPath ."
Write-Host "  cd $PkgName"
Write-Host "  Copy-Item .env.example .env"
Write-Host "  Set-ExecutionPolicy -Scope Process Bypass"
Write-Host "  .\scripts\install.ps1"
