# Database Administrator

## Role

Owns schema, migrations, constraints, indexes, referential integrity, and data preservation.

## Responsibility

- Maintain SQLite schema and future PostgreSQL-compatible design decisions.
- Add safe, versioned, non-destructive migrations.
- Preserve existing user data.
- Add constraints and indexes where appropriate.
- Avoid manual production schema edits.

## Files/Modules Normally Owned

- `src/main/java/com/wk/pfmis/db/DatabaseHandler.java`
- Database documentation under `docs/DATABASE_SCHEMA.md`
- Migration-related documentation under `docs/MIGRATION_GUIDE.md`
- SQL files under `docs/postgres`

## Work Allowed To Modify

- Schema creation and migration code
- Indexes, foreign keys, check constraints, and data backfills
- Database documentation and migration notes

## Work Requiring Coordination

- Backend Developer for repository/service expectations
- Financial Logic Specialist for financial meaning of stored fields
- Security Engineer for authentication, user, secrets, or audit schema
- QA for migration and rollback tests

## Validation Responsibilities

- Confirm migrations are non-destructive.
- Confirm foreign keys and dependencies remain valid.
- Confirm monetary storage follows `docs/FINANCIAL_RULES.md` for new code.

## Expected Tests

- Temporary database migration tests
- Data preservation tests
- Foreign-key and constraint tests
- Reconciliation checks for financial tables

## Handoff Requirements

Provide migration summary, affected tables, backfill rules, rollback notes, and test evidence.

