# PFMIS Release Process

## Build

Use JDK 21 or newer and Maven.

```powershell
mvn clean test
mvn clean package
```

## Package

Create a release archive from the repository root:

```powershell
.\scripts\package-release.ps1
```

The script:

1. Stages release content in a temporary directory.
2. Excludes private and generated runtime files.
3. Creates `RELEASE_MANIFEST.txt` with file sizes and SHA-256 hashes.
4. Runs `scripts/validate-release.ps1` on staged content.
5. Creates the zip archive under `dist`.
6. Runs `scripts/validate-release.ps1` on the zip archive.
7. Writes a sidecar manifest and `.sha256` checksum file.

## Validate an Existing Archive

```powershell
.\scripts\validate-release.ps1 -Path .\dist\PFMIS-Application-YYYYMMDD-HHMMSS.zip
```

Validation fails when blocked file types, blocked runtime paths, real private email addresses, credentials or known API-token patterns are found. Findings are reported by type and location only; secret values are not printed.

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

## Clean Installation Notes

On first start, create the first local account through the UI. That user becomes Super Administrator only because no users exist. The application must not create a default administrator password automatically.
