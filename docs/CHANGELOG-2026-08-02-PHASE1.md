# Phase 1 and 2 Security, Packaging and Baseline Testing Changelog

Date: 2026-08-02

## Changed

- Removed the local `.env` secret file from the project workspace.
- Replaced `.env.example` with placeholders only.
- Removed unused default Super Administrator environment variables from the example configuration.
- Expanded `.gitignore` coverage for private runtime data, generated reports, exports, backups, lock files, logs, spreadsheets, archives and local AI model files.
- Added `scripts/validate-release.ps1` to reject sensitive release content.
- Updated `scripts/package-release.ps1` to create a release manifest, write a SHA-256 checksum file and run validation before and after zipping.
- Updated README and package documentation with local multi-account wording, release rules, AI privacy notes and credential rotation guidance.
- Added JUnit 5, Surefire and JaCoCo test tooling.
- Added baseline password-security tests.
- Added release-validation regression tests.
- Added `docs/TESTING.md`.
- Added the immutable `Money` domain value object.
- Defined MWK storage as two-decimal integer minor units.
- Added money parsing, rounding, arithmetic and currency-mismatch tests.
- Added `docs/FINANCIAL_RULES.md`.
- Added architecture, database schema, asset recognition, AI integration, backup and migration guide documents with explicit current-limitations notes.
- Added Chipeleganyu missed-contribution schedule storage, UI actions, status rules and tests.

## Vulnerabilities Removed

- Real local `.env` values are no longer present in the workspace.
- Release packaging now has an automated validation gate for sensitive files and common secret patterns.
- Default administrator credentials are not represented in distributable configuration.

## Remaining Limitations

- Monetary precision still needs the planned integer minor-unit database migration across legacy modules.
- Password reset still needs a secure one-time token/code flow.
- External AI credential storage still needs operating-system credential-manager integration.
- Full repository/service database architecture is not yet complete.
- Bank Nkhonde and Chipeleganyu requirements still need domain-specific implementation using minor-unit money values.
- Chipeleganyu automatic deduction has status gates, but the full background scheduler remains pending.
- The automated test suite is a baseline only and does not yet cover transactions, migrations, reports, loans, assets or savings groups.
