# PFMIS Production Readiness Corrections - 2026-07-23

## Current Classification

PFMIS is an advanced prototype suitable for controlled pilot use. It is not yet a production-grade financial MIS because several financial-governance, security, audit, packaging and reporting controls remain incomplete.

## Corrections Applied In This Pass

### Authentication

- Removed built-in Super Administrator identity and password constants.
- Removed startup creation, reactivation, promotion and password reset of a configured Super Administrator.
- Removed login-field prefill for administrator email and password.
- Removed first-run registration prefill for personal administrator details.
- Kept the correct first-run rule: when no users exist, the first registered account becomes `SUPER_ADMIN`.

### Roles

- Added explicit `ADMIN` role beside `SUPER_ADMIN` and `USER`.
- Added auth-database migration support for the `ADMIN` role check constraint.
- Removed email-based automatic promotion to `SUPER_ADMIN`.
- Added Administrator/Super Administrator checks for supplementary-input validation, rejection review and freeze requests.

### Data And Records

- Converted governance workflow steps, required-field cards and process buttons into active controls.
- Active controls now open workflow detail, run available checks, create requests, export evidence, create backups or write audit events.
- High-risk import, freeze, purge, reset, delete and sync execution still remains behind role, backup, confirmation and audit requirements.
- Expanded workflow cards to include step purpose, evidence expectations and control meaning.
- Expanded Supplementary Report Inputs with a seven-step workflow and required field wireframe.
- Replaced alert-only People ledger and statement placeholders with active linked-transaction ledger viewing and text statement export.

### Packaging

- Added `.env.example` as the configuration template.
- Added `scripts/package-release.ps1` to create release archives while blocking `.env` variants, SQLite/database files, backups, logs, report/export output, old ZIPs and local model files.
- Updated `.gitignore`, `README.md` and `PACKAGE_README.md` with release-content rules.

## Still Required Before Production

1. Replace monetary `double` and SQLite `REAL` storage with exact minor-unit or `BigDecimal` storage through a controlled migration.
2. Implement record lifecycle fields, status history, correction requests, cancellation, reversal and operational freeze.
3. Build service-layer authorisation so UI hiding is not the only control.
4. Implement dependency analysis and Super Administrator disposal workflows.
5. Expand financial audit from simple system events to immutable before/after financial audit records.
6. Add ordered schema migrations and migration tests.
7. Split `DatabaseHandler` into repositories and services.
8. Introduce immutable `WorkspaceContext` for background operations.
9. Implement real file import batches, validation, duplicate detection and rollback.
10. Implement report snapshots and genuine PDF/XLSX exports.
11. Add encrypted backup/database deployment policy.
12. Add automated financial, security, migration and destructive-action tests.

## Release Rule

Do not distribute a package unless this command succeeds and the produced archive is tested:

```powershell
.\scripts\package-release.ps1
```

Every release should also be checksummed and archive-tested before sharing.
