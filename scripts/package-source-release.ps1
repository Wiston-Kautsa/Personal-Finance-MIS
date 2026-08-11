param(
    [string]$OutputDirectory = "dist",
    [string]$PackageName = "PFMIS-Source"
)

$ErrorActionPreference = "Stop"

& (Join-Path $PSScriptRoot "package-release.ps1") -OutputDirectory $OutputDirectory -PackageName $PackageName
