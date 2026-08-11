# PFMIS

PFMIS is a Java 21, JavaFX, Maven and SQLite desktop application for personal finance management. It is a local desktop system with local multi-account workspace separation, not a networked concurrent multi-user server.

## Supported Modules

- Accounts, income, expenses, transfers and transaction ledger
- Automatic foreign exchange rates with offline cache and manual-rate fallback
- Budgets, goals, projects, loans and repayments
- Assets and asset records where implemented
- Savings groups and local financial commitments
- Reports, data maintenance, backup and recovery workflows
- Bundled local AI integration through llama.cpp where runtime files and models are installed
- Optional external AI providers, with user-visible privacy risk

## Security Model

PFMIS does not ship with a built-in administrator username or password. On a clean installation, the first registered account becomes the Super Administrator because no users exist yet. Additional local users are created and governed from the application.

Authentication records are separate from user finance databases. Each normal user has a private local workspace. A Super Administrator may open another active user's workspace only through governed application workflows, and that access must be audited.

Configuration secrets must not be committed or packaged. Use `.env.example` as a template only. Installed builds create a private `.env` under the writable application-data directory for machine-level options such as exchange-rate provider settings, optional mail settings and logging controls. It is ignored by Git and blocked from release archives.

Previously exposed Gmail App Passwords, SMTP/IMAP passwords and external AI API keys must be rotated in their provider consoles. Do not reuse them.

## Data Storage

The application stores system data in the operating-system application-data directory rather than the project folder.

```text
PFMIS/
|-- pfmis-auth.db
`-- users/<user-id>/pfmis.db
```

Root-level files such as `pfmis.db`, `*.lock`, logs, backups and generated reports are runtime artifacts and must not be committed or shipped.

## AI Privacy

Bundled local AI should remain the default where the llama.cpp runtime and model are installed. External AI providers may process financial or personal information outside the local machine. Users must review provider risk and redact personal data before sending financial context externally.

## Current Limitations

- Financial precision migration to integer minor units is not complete.
- The large `DatabaseHandler` refactor is not complete.
- Some AI starter-pack descriptors and local runtime packaging still need consolidation.
- Password reset still needs a token/code workflow instead of reusable temporary login passwords.
- Sync-related screens represent local data and recovery checks unless a real remote synchronization architecture is implemented.

These limitations are tracked for later phases and must not be described as fully complete functionality.

## Build

```powershell
mvn clean test
mvn clean package
```

During development:

```powershell
.\run-pfmis.bat
```

or:

```sh
./run-pfmis.sh
```

## Release Packaging

PFMIS has two different release outputs:

- Source package for development review: `PFMIS-Source-<timestamp>.zip`
- Windows end-user installer: `PFMIS-<version>-x64.exe`

The source package is not an application installer. It does not include a bundled Java runtime or production launcher.

Create a clean source archive with:

```powershell
.\scripts\package-source-release.ps1
```

Build the self-contained Windows 10/11 x64 installer on Windows with JDK 21, Maven, jpackage and WiX available:

```powershell
.\scripts\build-windows-installer.ps1
```

The installer pipeline runs tests, collects production runtime dependencies, builds a jpackage app image, validates the app image, smoke-tests the native `PFMIS.exe` launcher, then creates `dist/windows/PFMIS-<version>-x64.exe` and a `.sha256` file.

The installed Desktop and Start Menu shortcuts must point directly to the native jpackage GUI launcher, not to `run-pfmis.bat`, Maven, `cmd.exe`, PowerShell or a Java console wrapper. Production jpackage builds intentionally do not use `--win-console`.

The package scripts stage files, write release manifests, create SHA-256 checksum files and run validation against generated artifacts.

Release packages must not contain `.env`, SQLite databases, lock files, logs, backups, generated exports, local AI model files, credentials, API keys or real private email addresses.

See:

- `docs/SECURITY.md`
- `docs/ENV_CONFIGURATION.md`
- `docs/FOREIGN_EXCHANGE.md`
- `docs/RELEASE_PROCESS.md`
- `docs/PFMIS-Multi-User-Security-and-Workspace-Guide.md`
