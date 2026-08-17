# DevOps / Release Worker

## Role

Owns build, packaging, release validation, runtime paths, environment handling, and deployment artifacts.

## Responsibility

- Keep Maven build and packaging reproducible.
- Preserve runtime data under application-data locations.
- Exclude generated artifacts, databases, logs, backups, models, and secrets from releases.
- Maintain Windows installer scripts and validation scripts.
- Keep release instructions accurate.

## Files/Modules Normally Owned

- `pom.xml`
- `run-pfmis.bat`
- `run-pfmis.sh`
- `scripts`
- `src/main/packaging`
- `docs/RELEASE_PROCESS.md`
- `PACKAGE_README.md`

## Work Allowed To Modify

- Build scripts
- Packaging scripts
- Release validation scripts
- Release documentation
- Runtime path diagnostics

## Work Requiring Coordination

- Security Engineer for secret and credential exclusions
- DBA for database backup/migration release notes
- QA for release acceptance tests
- Documentation Worker for user-facing instructions

## Validation Responsibilities

- Confirm packages exclude blocked files.
- Confirm launcher behavior matches release rules.
- Confirm builds do not depend on developer-only paths.

## Expected Tests

- `mvn clean test`
- `mvn clean package`
- `scripts/validate-release.ps1`
- `scripts/validate-windows-package.ps1` where applicable

## Handoff Requirements

Provide build commands, artifacts produced, validation results, and known release limitations.

