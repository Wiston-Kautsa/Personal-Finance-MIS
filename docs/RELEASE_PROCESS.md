# PFMIS Release Process

## Build

Use JDK 21 and Maven for production releases.

```powershell
mvn clean test
mvn clean package
```

## Source Package

Create a source archive from the repository root:

```powershell
.\scripts\package-source-release.ps1
```

The script:

1. Stages release content in a temporary directory.
2. Excludes private and generated runtime files.
3. Creates `RELEASE_MANIFEST.txt` with file sizes and SHA-256 hashes.
4. Runs `scripts/validate-release.ps1` on staged content.
5. Creates the zip archive under `dist`.
6. Runs `scripts/validate-release.ps1` on the zip archive.
7. Writes a sidecar manifest and `.sha256` checksum file.

A source archive is not a Windows application installer. It intentionally excludes `target`, `dist`, app images, EXE installers, local AI models/runtime binaries, databases and generated runtime artifacts.

## Windows Installer

Build the self-contained Windows installer on Windows 10/11 x64:

```powershell
.\scripts\build-windows-installer.ps1
```

The production script requires:

- Windows x64
- JDK 21 with `java`, `javac`, `jdeps` and `jpackage`
- Maven
- WiX Toolset available to jpackage
- `src/main/packaging/PFMIS.ico`

The pipeline is:

1. Validate Windows, architecture, JDK, Maven, jpackage, WiX and icon prerequisites.
2. Run `mvn clean test`.
3. Build the production JAR.
4. Collect production runtime dependencies only.
5. Run packaged runtime diagnostics for SQLite JDBC, JNA and key FXML/CSS resources.
6. Build `dist/windows/app-image/PFMIS`.
7. Validate and smoke-test the app-image native launcher.
8. Build `dist/windows/PFMIS-<version>-x64.exe`.
9. Write `PFMIS-<version>-x64.exe.sha256` and a JSON build manifest.

PFMIS is a GUI application. The production jpackage command must not use `--win-console`. Desktop and Start Menu shortcuts must target the installed native `PFMIS.exe` launcher directly, not a BAT file, Maven, `cmd.exe`, PowerShell, `java.exe` or any console wrapper.

Runtime data must remain under `%LOCALAPPDATA%\PFMIS`, including `pfmis-auth.db`, `users\<user-id>\pfmis.db`, backups and logs. Installed application binaries must not store financial databases under Program Files or the application image directory.

The base installer does not require bundled local AI runtime/model files. If those files are not packaged, PFMIS must continue to start and should log local-AI startup details under `%LOCALAPPDATA%\PFMIS\logs\local-ai.log`.

## Validate an Existing Archive

```powershell
.\scripts\validate-release.ps1 -Path .\dist\PFMIS-Application-YYYYMMDD-HHMMSS.zip
```

Validation fails when blocked file types, blocked runtime paths, real private email addresses, credentials or known API-token patterns are found. Findings are reported by type and location only; secret values are not printed.

Validate an app image:

```powershell
.\scripts\validate-windows-package.ps1 -AppImagePath .\dist\windows\app-image\PFMIS -SmokeTestSeconds 12
```

## Must Not Be Included

- `.env`
- `*.db`, `*.sqlite`, `*.sqlite3` and SQLite journal/WAL files
- `*.lock`
- `*.log`
- backups
- exports
- generated reports
- personal reports
- local AI model files such as `*.gguf`
- nested release archives
- real SMTP/IMAP passwords
- real external AI API keys
- default administrator credentials

## Upgrade Notes

Back up user workspaces before upgrading. Do not copy project-root runtime databases into a release package. Existing user data must be migrated by versioned database migrations, not by destructive reset.

Installer upgrades and uninstall/reinstall testing must not silently delete `%LOCALAPPDATA%\PFMIS`, `pfmis-auth.db`, `users\`, `pfmis.db`, backups, settings or credentials.

## Clean Installation Notes

On first start, create the first local account through the UI. That user becomes Super Administrator only because no users exist. The application must not create a default administrator password automatically.

Final installer acceptance requires a clean Windows 10/11 x64 VM or PC with no Java, JDK, Maven, NetBeans, IntelliJ, source tree or developer environment variables installed. Copy only `PFMIS-<version>-x64.exe`, install it, launch from Desktop, Start Menu and installed `PFMIS.exe`, and confirm there is no Command Prompt, PowerShell, Windows Terminal, Maven or Java console flash.
