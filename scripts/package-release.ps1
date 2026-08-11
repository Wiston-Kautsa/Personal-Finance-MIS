param(
    [string]$OutputDirectory = "dist",
    [string]$PackageName = "PFMIS-Application"
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$outputRoot = Join-Path $repoRoot $OutputDirectory
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$zipPath = Join-Path $outputRoot "$PackageName-$timestamp.zip"
$manifestPath = Join-Path $outputRoot "$PackageName-$timestamp-manifest.txt"
$checksumPath = Join-Path $outputRoot "$PackageName-$timestamp.sha256"
$stageRoot = Join-Path $env:TEMP "pfmis-release-$timestamp"
$stage = Join-Path $stageRoot "PFMIS"
$validator = Join-Path $repoRoot "scripts\validate-release.ps1"

$blockedNames = @(
    ".git",
    ".idea",
    ".junie",
    "target",
    "out",
    "dist",
    "ai-starter-pack"
)

$allowedEnvironmentFiles = @(".env.example")
$blockedExtensions = @(
    ".zip",
    ".7z",
    ".tar",
    ".gz",
    ".log",
    ".lock",
    ".bak",
    ".backup",
    ".dump",
    ".db",
    ".db-shm",
    ".db-wal",
    ".db-journal",
    ".sqlite",
    ".sqlite3",
    ".sqlite-shm",
    ".sqlite-wal",
    ".sqlite-journal",
    ".gguf"
)
$blockedPathFragments = @(
    "\backups\",
    "\logs\",
    "\security-backups\",
    "\exports\",
    "\reports\",
    "\personal-reports\",
    "\temp-exports\",
    "\temporary-exports\",
    "\local-ai\models\",
    "\local-ai\runtime\"
)

function Test-IsBlockedPath {
    param([System.IO.FileSystemInfo]$Item)

    $relative = $Item.FullName.Substring($repoRoot.Length).ToLowerInvariant()
    $segments = $relative.TrimStart([char[]]@('\', '/')) -split "[\\/]"
    foreach ($segment in $segments) {
        if ($segment.StartsWith(".env") -and -not ($allowedEnvironmentFiles -contains $segment)) {
            return $true
        }
        if ($blockedNames -contains $segment) {
            return $true
        }
    }
    if (-not $Item.PSIsContainer -and $blockedExtensions -contains $Item.Extension.ToLowerInvariant()) {
        return $true
    }
    foreach ($fragment in $blockedPathFragments) {
        if ($relative.Contains($fragment)) {
            return $true
        }
    }
    return $false
}

function New-ReleaseManifest {
    param(
        [string]$Root,
        [string]$Destination
    )

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("PFMIS release manifest")
    $lines.Add("Generated: $(Get-Date -Format o)")
    $lines.Add("")
    $lines.Add("RelativePath`tSizeBytes`tSHA256")

    Get-ChildItem -LiteralPath $Root -Recurse -File -Force |
        Sort-Object FullName |
        ForEach-Object {
            $relative = $_.FullName.Substring($Root.Length).TrimStart([char[]]@('\', '/'))
            $hash = Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
            $lines.Add("$relative`t$($_.Length)`t$($hash.Hash)")
        }

    $lines | Set-Content -LiteralPath $Destination -Encoding UTF8
}

if (-not (Test-Path -LiteralPath $validator)) {
    throw "Release validator not found: $validator"
}

Remove-Item -LiteralPath $stageRoot -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $stage | Out-Null
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null

Get-ChildItem -LiteralPath $repoRoot -Recurse -Force | Where-Object {
    -not $_.PSIsContainer -and -not (Test-IsBlockedPath $_)
} | ForEach-Object {
    $relative = $_.FullName.Substring($repoRoot.Length).TrimStart([char[]]@('\', '/'))
    $destination = Join-Path $stage $relative
    New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
    Copy-Item -LiteralPath $_.FullName -Destination $destination -Force
}

$stageManifest = Join-Path $stage "RELEASE_MANIFEST.txt"
New-ReleaseManifest -Root $stage -Destination $stageManifest

& $validator -Path $stage
Compress-Archive -LiteralPath $stage -DestinationPath $zipPath -Force
& $validator -Path $zipPath

Copy-Item -LiteralPath $stageManifest -Destination $manifestPath -Force
$hash = Get-FileHash -LiteralPath $zipPath -Algorithm SHA256
"$($hash.Hash)  $(Split-Path $zipPath -Leaf)" | Set-Content -LiteralPath $checksumPath -Encoding ASCII

Remove-Item -LiteralPath $stageRoot -Recurse -Force

[PSCustomObject]@{
    Package = $zipPath
    Manifest = $manifestPath
    Checksum = $checksumPath
    SizeMB = [math]::Round((Get-Item -LiteralPath $zipPath).Length / 1MB, 2)
    SHA256 = $hash.Hash
} | Format-List
