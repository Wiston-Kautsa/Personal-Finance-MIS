# PFMIS Testing

## Current Baseline

Automated tests now exist under `src/test/java`.

Current coverage focuses on:

- PBKDF2 password hashing, verification, random salts and weak-password rejection
- Release validation pass/fail behavior
- Release validation redaction of sensitive values
- Money parsing, MWK minor-unit scale, explicit rounding, arithmetic and currency mismatch rejection
- Chipeleganyu contribution status rules for automatic deduction and missed-contribution reversal protection

This is an initial regression baseline, not the complete financial-system test suite.

## Run Tests

```powershell
mvn clean test
```

## Coverage Report

JaCoCo is configured through Maven. After `mvn clean test`, open:

```text
target/site/jacoco/index.html
```

## Required Future Test Areas

- Auth database behavior using temporary database roots
- Workspace separation
- Secure one-time password reset codes
- Transaction posting and reversal
- Transfer debit/credit balancing
- Minor-unit money calculations and rounding
- Database migrations and rollback
- Asset-recognition decisions
- Bank Nkhonde and Chipeleganyu workflows
- Report totals versus ledger totals
- Backup and restore integrity

No release should claim production readiness until these areas are covered by tests.
