param(
    [Parameter(Mandatory = $true)]
    [string]$AppImagePath,

    [int]$SmokeTestSeconds = 0,

    [string]$AppName = "PFMIS"
)

$ErrorActionPreference = "Stop"

function Stop-WithMessage {
    param([string]$Message)
    throw "PFMIS Windows package validation failed: $Message"
}

function Assert-File {
    param([string]$Path, [string]$Description)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Stop-WithMessage "$Description was not found: $Path"
    }
    if ((Get-Item -LiteralPath $Path).Length -le 0) {
        Stop-WithMessage "$Description is empty: $Path"
    }
}

function Assert-Directory {
    param([string]$Path, [string]$Description)
    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        Stop-WithMessage "$Description was not found: $Path"
    }
}

function Invoke-HiddenProcess {
    param(
        [string]$FilePath,
        [string]$Arguments,
        [int]$TimeoutSeconds = 30
    )
    $processInfo = New-Object System.Diagnostics.ProcessStartInfo
    $processInfo.FileName = $FilePath
    $processInfo.Arguments = $Arguments
    $processInfo.UseShellExecute = $false
    $processInfo.RedirectStandardOutput = $true
    $processInfo.RedirectStandardError = $true
    $processInfo.CreateNoWindow = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $processInfo
    [void]$process.Start()
    if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
        $process.Kill()
        Stop-WithMessage "process did not exit within $TimeoutSeconds seconds: $FilePath $Arguments"
    }
    [PSCustomObject]@{
        ExitCode = $process.ExitCode
        Output = (($process.StandardOutput.ReadToEnd(), $process.StandardError.ReadToEnd()) -join [Environment]::NewLine).Trim()
    }
}

$resolvedImage = (Resolve-Path -LiteralPath $AppImagePath).Path
$launcher = Join-Path $resolvedImage "$AppName.exe"
$appDirectory = Join-Path $resolvedImage "app"
$runtimeDirectory = Join-Path $resolvedImage "runtime"

Assert-File -Path $launcher -Description "native launcher"
Assert-Directory -Path $appDirectory -Description "jpackage app directory"
Assert-Directory -Path $runtimeDirectory -Description "bundled Java runtime directory"
Assert-File -Path (Join-Path $runtimeDirectory "bin\jli.dll") -Description "bundled Java runtime launcher library"
Assert-File -Path (Join-Path $runtimeDirectory "lib\modules") -Description "bundled Java runtime module image"

$mainJar = Get-ChildItem -LiteralPath $appDirectory -Filter "personal-finance-mis-*.jar" -File |
    Sort-Object FullName |
    Select-Object -First 1
if ($null -eq $mainJar) {
    Stop-WithMessage "PFMIS application JAR was not found in $appDirectory"
}

$requiredLibraries = @(
    "javafx-controls",
    "javafx-fxml",
    "javafx-base",
    "javafx-graphics",
    "sqlite-jdbc",
    "jna-",
    "jna-platform",
    "slf4j-simple",
    "slf4j-api"
)

$jarNames = Get-ChildItem -LiteralPath $appDirectory -Filter "*.jar" -File | ForEach-Object { $_.Name }
foreach ($library in $requiredLibraries) {
    if (-not ($jarNames | Where-Object { $_ -like "*$library*" })) {
        Stop-WithMessage "required runtime library '$library' was not packaged"
    }
}

$blockedTestLibraries = @("junit", "surefire", "opentest4j", "apiguardian", "junit-platform")
foreach ($library in $blockedTestLibraries) {
    if ($jarNames | Where-Object { $_ -like "*$library*" }) {
        Stop-WithMessage "test library '$library' was packaged into the production app image"
    }
}

$jarTool = (Get-Command jar -ErrorAction SilentlyContinue).Source
if ($jarTool) {
    $jarEntries = & $jarTool tf $mainJar.FullName
    foreach ($resource in @(
        "com/wk/pfmis/views/Login.fxml",
        "com/wk/pfmis/views/Dashboard.fxml",
        "com/wk/pfmis/css/Theme.css"
    )) {
        if (-not ($jarEntries -contains $resource)) {
            Stop-WithMessage "required application resource '$resource' was not found inside $($mainJar.Name)"
        }
    }
}

$runtimeCheck = Invoke-HiddenProcess -FilePath $launcher -Arguments "--pfmis-runtime-check" -TimeoutSeconds 30
if ($runtimeCheck.ExitCode -ne 0) {
    $details = $runtimeCheck.Output
    Stop-WithMessage "packaged runtime dependency check failed. $details"
}

if ($SmokeTestSeconds -gt 0) {
    $smokeDataDirectory = Join-Path $env:TEMP ("pfmis-app-image-smoke-" + [guid]::NewGuid())
    $previousJavaOptions = $env:_JAVA_OPTIONS
    try {
        $env:_JAVA_OPTIONS = "-Dpfmis.data.dir=$smokeDataDirectory"
        $process = Start-Process -FilePath $launcher -PassThru
        Start-Sleep -Seconds $SmokeTestSeconds
        if ($process.HasExited) {
            Stop-WithMessage "app-image launcher exited during the $SmokeTestSeconds second smoke test with code $($process.ExitCode)"
        }
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    } finally {
        if ($null -eq $previousJavaOptions) {
            Remove-Item Env:\_JAVA_OPTIONS -ErrorAction SilentlyContinue
        } else {
            $env:_JAVA_OPTIONS = $previousJavaOptions
        }
        Remove-Item -LiteralPath $smokeDataDirectory -Recurse -Force -ErrorAction SilentlyContinue
    }
}

[PSCustomObject]@{
    AppImage = $resolvedImage
    Launcher = $launcher
    Runtime = $runtimeDirectory
    MainJar = $mainJar.FullName
    RuntimeJarCount = $jarNames.Count
    SmokeTestSeconds = $SmokeTestSeconds
} | Format-List
