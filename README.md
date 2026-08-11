# PFMIS

PFMIS is a Java 21, JavaFX, Maven and SQLite desktop application for personal finance management. It is a local desktop system with local multi-account workspace separation, not a networked concurrent multi-user server.

## Supported Modules

- Accounts, income, expenses, transfers and transaction ledger
- Budgets, goals, projects, loans and repayments
- Assets and asset records where implemented
- Savings groups and local financial commitments
- Reports, data maintenance, backup and recovery workflows
- Bundled local AI integration through llama.cpp where runtime files and models are installed
- Optional external AI providers, with user-visible privacy risk

## Security Model

PFMIS does not ship with a built-in administrator username or password. On a clean installation, the first registered account becomes the Super Administrator because no users exist yet. Additional local users are created and governed from the application.

Authentication records are separate from user finance databases. Each normal user has a private local workspace. A Super Administrator may open another active user's workspace only through governed application workflows, and that access must be audited.

Configuration secrets must not be committed or packaged. Use `.env.example` as a template only. A private `.env` file may be created on a target machine for optional mail settings, but it is ignored by Git and blocked from release archives.

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

Create a clean release archive with:

```powershell
.\scripts\package-release.ps1
```

The package script stages files, writes a release manifest, creates a SHA-256 checksum file and runs `scripts/validate-release.ps1` against both the staged content and the final archive.

Release packages must not contain `.env`, SQLite databases, lock files, logs, backups, generated exports, local AI model files, credentials, API keys or real private email addresses.

See:

- `docs/SECURITY.md`
- `docs/RELEASE_PROCESS.md`
- `docs/PFMIS-Multi-User-Security-and-Workspace-Guide.md`
