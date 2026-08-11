param(
    [Parameter(Mandatory = $true)]
    [string]$Path
)

$ErrorActionPreference = "Stop"

$resolved = (Resolve-Path -LiteralPath $Path).Path
$temporaryRoot = $null
$scanRoot = $resolved

if ((Test-Path -LiteralPath $resolved -PathType Leaf) -and ([IO.Path]::GetExtension($resolved).ToLowerInvariant() -eq ".zip")) {
    $temporaryRoot = Join-Path $env:TEMP ("pfmis-release-validation-" + [guid]::NewGuid())
    New-Item -ItemType Directory -Force -Path $temporaryRoot | Out-Null
    Expand-Archive -LiteralPath $resolved -DestinationPath $temporaryRoot -Force
    $scanRoot = $temporaryRoot
}

$allowedEnvironmentFiles = @(".env.example")
$blockedExtensions = @(
    ".db",
    ".db-shm",
    ".db-wal",
    ".db-journal",
    ".sqlite",
    ".sqlite3",
    ".sqlite-shm",
    ".sqlite-wal",
    ".sqlite-journal",
    ".lock",
    ".log",
    ".bak",
    ".backup",
    ".dump",
    ".zip",
    ".7z",
    ".tar",
    ".gz",
    ".gguf"
)
$blockedPathSegments = @(
    ".git",
    ".idea",
    ".junie",
    "target",
    "out",
    "dist",
    "backups",
    "logs",
    "exports",
    "reports",
    "personal-reports",
    "temp-exports",
    "temporary-exports",
    "security-backups"
)
$textExtensions = @(
    ".bat",
    ".css",
    ".example",
    ".fxml",
    ".html",
    ".java",
    ".json",
    ".md",
    ".properties",
    ".ps1",
    ".sh",
    ".txt",
    ".xml",
    ".yaml",
    ".yml"
)
$configurationValueExtensions = @(
    ".env",
    ".example",
    ".properties",
    ".json",
    ".yaml",
    ".yml",
    ".xml"
)

$findings = New-Object System.Collections.Generic.List[object]

function Add-Finding {
    param(
        [string]$RelativePath,
        [string]$Type,
        [int]$LineNumber = 0
    )

    $location = if ($LineNumber -gt 0) { "${RelativePath}:$LineNumber" } else { $RelativePath }
    $findings.Add([PSCustomObject]@{
        Type = $Type
        Location = $location
    })
}

function Get-RelativePath {
    param([string]$FullName)

    return $FullName.Substring($scanRoot.Length).TrimStart([char[]]@('\', '/'))
}

function Test-IsAllowedPlaceholder {
    param([string]$Value)

    if ($null -eq $Value) {
        return $true
    }
    $clean = $Value.Trim().Trim('"').Trim("'").Trim()
    if ($clean.Length -eq 0) {
        return $true
    }
    $lower = $clean.ToLowerInvariant()
    return $lower.Contains("placeholder") `
        -or $lower.Contains("replace-with") `
        -or $lower.Contains("example") `
        -or $lower.Contains("not-set") `
        -or $lower.Contains("disabled") `
        -or $lower -eq "false" `
        -or $lower -eq "true"
}

function Test-IsAllowedEmail {
    param([string]$EmailAddress)

    $domain = ($EmailAddress -split "@", 2)[1].ToLowerInvariant()
    return $domain -in @("example.invalid", "example.com", "example.org", "example.net", "pfmis.local", "localhost")
}

function Test-LooksSensitiveLiteral {
    param([string]$Value)

    if (Test-IsAllowedPlaceholder $Value) {
        return $false
    }
    $clean = $Value.Trim().Trim('"').Trim("'").Trim()
    if ($clean.Length -lt 12) {
        return $false
    }
    if ($clean -match '^(sk-|sk_live_|AIza|ghp_|github_pat_|xox[baprs]-|ya29\.)') {
        return $true
    }
    $hasUpper = $clean -cmatch '[A-Z]'
    $hasLower = $clean -cmatch '[a-z]'
    $hasDigit = $clean -match '\d'
    $hasSymbol = $clean -match '[+/=_-]'
    return (($hasUpper -and $hasLower -and $hasDigit) -or ($hasDigit -and $hasSymbol)) -and $clean.Length -ge 20
}

function Test-TextFile {
    param([System.IO.FileInfo]$File)

    $extension = $File.Extension.ToLowerInvariant()
    return $textExtensions -contains $extension -or $File.Name.ToLowerInvariant().EndsWith(".env.example")
}

try {
    foreach ($item in Get-ChildItem -LiteralPath $scanRoot -Recurse -Force) {
        $relative = Get-RelativePath $item.FullName
        if ($relative.Length -eq 0) {
            continue
        }

        $segments = $relative -split '[\\/]'
        foreach ($segment in $segments) {
            $lowerSegment = $segment.ToLowerInvariant()
            if ($lowerSegment.StartsWith(".env") -and -not ($allowedEnvironmentFiles -contains $lowerSegment)) {
                Add-Finding $relative "environment file"
            }
            if ($blockedPathSegments -contains $lowerSegment) {
                Add-Finding $relative "runtime or private path"
            }
        }

        if ($item.PSIsContainer) {
            continue
        }

        $extension = $item.Extension.ToLowerInvariant()
        if ($blockedExtensions -contains $extension) {
            Add-Finding $relative "blocked file extension"
        }

        if (-not (Test-TextFile $item) -or $item.Length -gt 2MB) {
            continue
        }

        $lineNumber = 0
        $isConfigurationValueFile = $configurationValueExtensions -contains $item.Extension.ToLowerInvariant() `
            -or $item.Name.ToLowerInvariant().EndsWith(".env.example")
        foreach ($line in Get-Content -LiteralPath $item.FullName -ErrorAction Stop) {
            $lineNumber++

            foreach ($match in [regex]::Matches($line, '[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)) {
                if (-not (Test-IsAllowedEmail $match.Value)) {
                    Add-Finding $relative "real email address" $lineNumber
                }
            }

            if ($isConfigurationValueFile -and $line -match '^\s*([A-Za-z0-9_.-]*(PASSWORD|TOKEN|SECRET|API[_-]?KEY|CREDENTIAL)[A-Za-z0-9_.-]*)\s*[:=]\s*(.+?)\s*$') {
                $value = $Matches[3]
                if (-not (Test-IsAllowedPlaceholder $value)) {
                    Add-Finding $relative "sensitive configuration value" $lineNumber
                }
            }

            foreach ($match in [regex]::Matches($line, '(sk-[A-Za-z0-9_-]{16,}|sk_live_[A-Za-z0-9_-]{16,}|AIza[A-Za-z0-9_-]{20,}|ghp_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,}|xox[baprs]-[A-Za-z0-9-]{20,}|ya29\.[A-Za-z0-9_-]{20,})')) {
                Add-Finding $relative "known API token pattern" $lineNumber
            }

            if ($line -match '(?i)\b(password|apikey|api_key|token|secret)\b\s*=\s*"([^"]+)"') {
                if (Test-LooksSensitiveLiteral $Matches[2]) {
                    Add-Finding $relative "hard-coded sensitive literal" $lineNumber
                }
            }
        }
    }

    if ($findings.Count -gt 0) {
        $summary = ($findings | Select-Object -First 50 | Format-Table -AutoSize | Out-String).TrimEnd()
        throw "Release validation failed. Findings are redacted by type and location only:`n$summary"
    }

    Write-Output "Release validation passed for $resolved"
} finally {
    if ($temporaryRoot -and (Test-Path -LiteralPath $temporaryRoot)) {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
}
