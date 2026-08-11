param(
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$MavenPath = "",
    [string]$AppName = "PFMIS",
    [string]$Vendor = "PFMIS",
    [string]$Description = "Personal Finance Management Information System",
    [int]$SmokeTestSeconds = 12,
    [switch]$AppImageOnly,
    [switch]$AllowNewerJdk,
    [switch]$IncludeLocalAi
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$targetRoot = Join-Path $repoRoot "target\installer"
$dependencyDirectory = Join-Path $targetRoot "dependencies"
$inputDirectory = Join-Path $targetRoot "input"
$distRoot = Join-Path $repoRoot "dist\windows"
$appImageRoot = Join-Path $distRoot "app-image"
$appImagePath = Join-Path $appImageRoot $AppName
$logsDirectory = Join-Path $distRoot "logs"
$iconPath = Join-Path $repoRoot "src\main\packaging\PFMIS.ico"
$validator = Join-Path $repoRoot "scripts\validate-windows-package.ps1"

function Stop-Build {
    param([string]$Message)
    throw "PFMIS installer build cannot continue: $Message"
}

function Assert-UnderRepo {
    param([string]$Path)
    $fullPath = [IO.Path]::GetFullPath($Path)
    if (-not $fullPath.StartsWith($repoRoot, [StringComparison]::OrdinalIgnoreCase)) {
        Stop-Build "refusing to delete or overwrite a path outside the repository: $fullPath"
    }
    return $fullPath
}

function Resolve-Tool {
    param([string]$ToolName, [string[]]$Fallbacks = @())
    $command = Get-Command $ToolName -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command) {
        return $command.Source
    }
    foreach ($fallback in $Fallbacks) {
        if ($fallback -and (Test-Path -LiteralPath $fallback -PathType Leaf)) {
            return (Resolve-Path -LiteralPath $fallback).Path
        }
    }
    return $null
}

function Resolve-Maven {
    if ($MavenPath -and (Test-Path -LiteralPath $MavenPath -PathType Leaf)) {
        return (Resolve-Path -LiteralPath $MavenPath).Path
    }
    $maven = Resolve-Tool -ToolName "mvn.cmd"
    if ($maven) {
        return $maven
    }
    $maven = Resolve-Tool -ToolName "mvn"
    if ($maven) {
        return $maven
    }
    $candidates = @()
    $wrapperRoot = Join-Path $env:USERPROFILE ".m2\wrapper\dists"
    if (Test-Path -LiteralPath $wrapperRoot -PathType Container) {
        $candidates += Get-ChildItem -LiteralPath $wrapperRoot -Recurse -Filter "mvn.cmd" -ErrorAction SilentlyContinue
    }
    return $candidates | Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}

function Join-NativeArguments {
    param([string[]]$Arguments)
    $quoted = foreach ($argument in $Arguments) {
        if ($null -eq $argument) {
            '""'
        } elseif ($argument -match '[\s"]') {
            '"' + ($argument -replace '"', '\"') + '"'
        } else {
            $argument
        }
    }
    return ($quoted -join " ")
}

function Invoke-CapturedNativeCommand {
    param(
        [string]$FilePath,
        [string[]]$Arguments = @()
    )
    $processInfo = New-Object System.Diagnostics.ProcessStartInfo
    $nativeArguments = Join-NativeArguments -Arguments $Arguments
    if ($FilePath -match '\.(cmd|bat)$') {
        $commandLine = "`"$FilePath`""
        if ($nativeArguments) {
            $commandLine += " $nativeArguments"
        }
        $processInfo.FileName = if ($env:ComSpec) { $env:ComSpec } else { "cmd.exe" }
        $processInfo.Arguments = "/d /s /c `"$commandLine`""
    } else {
        $processInfo.FileName = $FilePath
        $processInfo.Arguments = $nativeArguments
    }
    $processInfo.UseShellExecute = $false
    $processInfo.RedirectStandardOutput = $true
    $processInfo.RedirectStandardError = $true
    $processInfo.CreateNoWindow = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $processInfo
    [void]$process.Start()
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()

    [PSCustomObject]@{
        ExitCode = $process.ExitCode
        Stdout = $stdout
        Stderr = $stderr
        CombinedOutput = (($stdout, $stderr) -join [Environment]::NewLine).Trim()
    }
}

function Resolve-JdkTool {
    param([string]$ToolName)
    if ($JdkHome) {
        $candidate = Join-Path $JdkHome "bin\$ToolName"
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    return Resolve-Tool -ToolName $ToolName
}

function Get-JavaMajorVersion {
    param([string]$JavaExe)
    $result = Invoke-CapturedNativeCommand -FilePath $JavaExe -Arguments @("-version")
    $versionOutput = $result.CombinedOutput
    if ($versionOutput -match '"(?<version>\d+)(?:\.(?<minor>\d+))?') {
        return [int]$Matches.version
    }
    Stop-Build "could not determine Java version from: $versionOutput"
}

function Get-MavenProjectVersion {
    param([string]$Maven)
    $result = Invoke-CapturedNativeCommand -FilePath $Maven -Arguments @(
        "help:evaluate",
        "-Dexpression=project.version",
        "-q",
        "-DforceStdout"
    )
    if ($result.ExitCode -ne 0) {
        Stop-Build "could not read project version. $($result.CombinedOutput)"
    }
    return (($result.CombinedOutput).Trim() -split "\s+")[-1]
}

function Invoke-LoggedCommand {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$LogFile,
        [switch]$AllowFailure
    )
    "COMMAND: $FilePath $($Arguments -join ' ')" | Tee-Object -FilePath $LogFile -Append | Out-Null
    $result = Invoke-CapturedNativeCommand -FilePath $FilePath -Arguments $Arguments
    if ($result.CombinedOutput) {
        $result.CombinedOutput | Tee-Object -FilePath $LogFile -Append
    }
    if ($result.ExitCode -ne 0 -and -not $AllowFailure) {
        Stop-Build "command failed with exit code $($result.ExitCode). See $LogFile"
    }
    return $result
}

function Assert-IcoFile {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Stop-Build "Windows icon was not found: $Path"
    }
    $bytes = [IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $Path).Path)
    if ($bytes.Length -lt 6 -or $bytes[0] -ne 0 -or $bytes[1] -ne 0 -or $bytes[2] -ne 1 -or $bytes[3] -ne 0) {
        Stop-Build "Windows icon is not a valid ICO file: $Path"
    }
    $count = [BitConverter]::ToUInt16($bytes, 4)
    if ($count -lt 2) {
        Stop-Build "Windows icon must contain multiple image sizes: $Path"
    }
}

function Assert-ProductionDependencies {
    param([System.IO.FileInfo[]]$Jars)
    $names = $Jars | ForEach-Object { $_.Name }
    foreach ($required in @("javafx-controls", "javafx-fxml", "sqlite-jdbc", "jna-", "jna-platform", "slf4j-simple", "slf4j-api")) {
        if (-not ($names | Where-Object { $_ -like "*$required*" })) {
            Stop-Build "runtime dependency '$required' was not collected"
        }
    }
    foreach ($blocked in @("junit", "surefire", "opentest4j", "apiguardian", "junit-platform")) {
        if ($names | Where-Object { $_ -like "*$blocked*" }) {
            Stop-Build "test dependency '$blocked' was collected into installer input"
        }
    }
}

function Get-WixDescription {
    $wix = Resolve-Tool -ToolName "wix.exe" -Fallbacks @("C:\Program Files\WiX Toolset v7.0\bin\wix.exe")
    if ($wix) {
        $version = (Invoke-CapturedNativeCommand -FilePath $wix -Arguments @("--version")).CombinedOutput
        return "wix.exe $version ($wix)"
    }
    $candle = Resolve-Tool -ToolName "candle.exe"
    $light = Resolve-Tool -ToolName "light.exe"
    if ($candle -and $light) {
        return "WiX v3 tools ($candle, $light)"
    }
    return $null
}

if (-not $IsWindows -and [Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
    Stop-Build "Windows installer builds must run on Windows."
}
if (-not [Environment]::Is64BitOperatingSystem) {
    Stop-Build "Windows x64 is required."
}

$java = Resolve-JdkTool -ToolName "java.exe"
$javac = Resolve-JdkTool -ToolName "javac.exe"
$jpackage = Resolve-JdkTool -ToolName "jpackage.exe"
$jdeps = Resolve-JdkTool -ToolName "jdeps.exe"
$maven = Resolve-Maven
$wixDescription = Get-WixDescription

if (-not $java) { Stop-Build "java.exe was not found." }
if (-not $javac) { Stop-Build "javac.exe was not found." }
if (-not $jpackage) { Stop-Build "jpackage.exe was not found." }
if (-not $maven) { Stop-Build "Maven was not found. Install Maven or pass -MavenPath." }
if (-not $wixDescription) { Stop-Build "WiX Toolset required by jpackage was not found." }
Assert-IcoFile -Path $iconPath
if (-not (Test-Path -LiteralPath $validator -PathType Leaf)) {
    Stop-Build "Windows package validator was not found: $validator"
}

$javaMajor = Get-JavaMajorVersion -JavaExe $java
if ($javaMajor -ne 21 -and -not $AllowNewerJdk) {
    Stop-Build "JDK 21 is required for production installer builds. Active java is major version $javaMajor at $java. Pass -AllowNewerJdk only for local diagnostics."
}
if ($javaMajor -lt 21) {
    Stop-Build "Java major version $javaMajor is too old; JDK 21 is required."
}

$jdkHomeResolved = Split-Path (Split-Path $java -Parent) -Parent
$env:JAVA_HOME = $jdkHomeResolved
$env:PATH = (Join-Path $jdkHomeResolved "bin") + ";" + $env:PATH

New-Item -ItemType Directory -Force -Path $logsDirectory | Out-Null
$buildLog = Join-Path $logsDirectory "build-windows-installer.log"
Remove-Item -LiteralPath $buildLog -Force -ErrorAction SilentlyContinue

$version = Get-MavenProjectVersion -Maven $maven
$targetArch = "x64"
$finalInstaller = Join-Path $distRoot "$AppName-$version-$targetArch.exe"
$checksumPath = "$finalInstaller.sha256"
$manifestPath = Join-Path $distRoot "$AppName-$version-$targetArch-manifest.json"

"PFMIS Windows installer build" | Tee-Object -FilePath $buildLog
"OS architecture: $env:PROCESSOR_ARCHITECTURE" | Tee-Object -FilePath $buildLog -Append
"JDK: $jdkHomeResolved" | Tee-Object -FilePath $buildLog -Append
"Java major version: $javaMajor" | Tee-Object -FilePath $buildLog -Append
"Maven: $maven" | Tee-Object -FilePath $buildLog -Append
"WiX: $wixDescription" | Tee-Object -FilePath $buildLog -Append
"Target architecture: $targetArch" | Tee-Object -FilePath $buildLog -Append
"Launcher mode: Windows GUI native launcher." | Tee-Object -FilePath $buildLog -Append

foreach ($path in @($targetRoot, $appImagePath)) {
    $safePath = Assert-UnderRepo -Path $path
    Remove-Item -LiteralPath $safePath -Recurse -Force -ErrorAction SilentlyContinue
}
foreach ($directory in @($dependencyDirectory, $inputDirectory, $appImageRoot, $distRoot)) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
}

Invoke-LoggedCommand -FilePath $maven -Arguments @("clean", "test") -LogFile $buildLog
Invoke-LoggedCommand -FilePath $maven -Arguments @(
    "-DskipTests",
    "package",
    "dependency:copy-dependencies",
    "-DincludeScope=runtime",
    "-DexcludeGroupIds=org.junit.jupiter,org.junit.platform,org.opentest4j,org.apiguardian",
    "-DoutputDirectory=$dependencyDirectory"
) -LogFile $buildLog

foreach ($directory in @($dependencyDirectory, $inputDirectory)) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
}

$mainJar = Get-ChildItem -LiteralPath (Join-Path $repoRoot "target") -Filter "personal-finance-mis-$version.jar" -File |
    Select-Object -First 1
if ($null -eq $mainJar) {
    Stop-Build "production application JAR was not built for version $version"
}

Copy-Item -LiteralPath $mainJar.FullName -Destination $inputDirectory -Force
Get-ChildItem -LiteralPath $dependencyDirectory -Filter "*.jar" -File | Copy-Item -Destination $inputDirectory -Force

if ($IncludeLocalAi) {
    foreach ($relative in @("local-ai\runtime", "local-ai\models", "local-ai\agents")) {
        $source = Join-Path $repoRoot $relative
        if (Test-Path -LiteralPath $source) {
            $destination = Join-Path $inputDirectory $relative
            New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
            Copy-Item -LiteralPath $source -Destination $destination -Recurse -Force
        }
    }
}

$inputJars = Get-ChildItem -LiteralPath $inputDirectory -Filter "*.jar" -File
Assert-ProductionDependencies -Jars $inputJars

if ($jdeps) {
    $jdepsLog = Join-Path $logsDirectory "jdeps-runtime-modules.log"
    $dependencyModulePath = (($inputJars | Where-Object { $_.Name -ne $mainJar.Name } | ForEach-Object { $_.FullName }) -join [IO.Path]::PathSeparator)
    Invoke-LoggedCommand -FilePath $jdeps -Arguments @(
        "--multi-release", "21",
        "--ignore-missing-deps",
        "--module-path", $dependencyModulePath,
        $mainJar.FullName
    ) -LogFile $jdepsLog -AllowFailure
}

$runtimeCheckLog = Join-Path $logsDirectory "packaged-runtime-check.log"
Invoke-LoggedCommand -FilePath $java -Arguments @(
    "-cp",
    (Join-Path $inputDirectory "*"),
    "com.wk.pfmis.diagnostics.PackagingRuntimeCheck"
) -LogFile $runtimeCheckLog

$appImageArgs = @(
    "--type", "app-image",
    "--dest", $appImageRoot,
    "--name", $AppName,
    "--app-version", $version,
    "--vendor", $Vendor,
    "--description", $Description,
    "--input", $inputDirectory,
    "--main-jar", $mainJar.Name,
    "--main-class", "com.wk.pfmis.Launcher",
    "--icon", $iconPath,
    "--add-modules", "java.base,java.desktop,java.net.http,java.prefs,java.sql,java.xml,java.scripting,jdk.crypto.ec"
)
Invoke-LoggedCommand -FilePath $jpackage -Arguments $appImageArgs -LogFile $buildLog

$validationLog = Join-Path $logsDirectory "validate-app-image.log"
Invoke-LoggedCommand -FilePath "powershell.exe" -Arguments @(
    "-ExecutionPolicy", "Bypass",
    "-File", $validator,
    "-AppImagePath", $appImagePath,
    "-SmokeTestSeconds", "$SmokeTestSeconds",
    "-AppName", $AppName
) -LogFile $validationLog

if ($AppImageOnly) {
    "App-image validation passed. Skipping EXE installer because -AppImageOnly was specified." | Tee-Object -FilePath $buildLog -Append
    return
}

Remove-Item -LiteralPath $finalInstaller -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $checksumPath -Force -ErrorAction SilentlyContinue

$exeArgs = @(
    "--type", "exe",
    "--dest", $distRoot,
    "--name", $AppName,
    "--app-version", $version,
    "--vendor", $Vendor,
    "--description", $Description,
    "--icon", $iconPath,
    "--app-image", $appImagePath,
    "--win-menu",
    "--win-shortcut",
    "--win-dir-chooser",
    "--win-per-user-install"
)
Invoke-LoggedCommand -FilePath $jpackage -Arguments $exeArgs -LogFile $buildLog

$createdInstaller = Get-ChildItem -LiteralPath $distRoot -Filter "*.exe" -File |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($null -eq $createdInstaller) {
    Stop-Build "jpackage did not produce an EXE installer in $distRoot"
}
if ($createdInstaller.FullName -ne $finalInstaller) {
    Move-Item -LiteralPath $createdInstaller.FullName -Destination $finalInstaller -Force
}
if ((Get-Item -LiteralPath $finalInstaller).Length -le 0) {
    Stop-Build "installer was created but is empty: $finalInstaller"
}

$hash = Get-FileHash -LiteralPath $finalInstaller -Algorithm SHA256
"$($hash.Hash)  $(Split-Path $finalInstaller -Leaf)" | Set-Content -LiteralPath $checksumPath -Encoding ASCII

$manifest = [ordered]@{
    application = $AppName
    version = $version
    target = "Windows 10/11 x64"
    architecture = $targetArch
    builtAt = (Get-Date -Format o)
    gitCommit = (& git -C $repoRoot rev-parse HEAD 2>$null)
    jdkHome = $jdkHomeResolved
    javaMajor = $javaMajor
    maven = $maven
    wix = $wixDescription
    appImage = $appImagePath
    installer = $finalInstaller
    sha256 = $hash.Hash
    launcher = "Native jpackage Windows GUI launcher"
    perUserInstall = $true
    runtimeDependencies = @($inputJars | Sort-Object Name | ForEach-Object { $_.Name })
    localAiIncluded = [bool]$IncludeLocalAi
}
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $manifestPath -Encoding UTF8

[PSCustomObject]@{
    Installer = $finalInstaller
    Checksum = $checksumPath
    Manifest = $manifestPath
    AppImage = $appImagePath
    SHA256 = $hash.Hash
} | Format-List
