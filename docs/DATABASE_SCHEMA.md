# PFMIS Database Schema

PFMIS uses SQLite for local desktop data. The schema is currently created and migrated mostly through application Java code.

## Current Position

The project still contains legacy monetary columns represented as Java `double` and SQLite `REAL`. These are not safe for financial calculations and must be migrated to integer minor-unit columns.

## Monetary Storage Rule

New and migrated monetary columns should use:

- `<amount_name>_minor INTEGER NOT NULL`
- `currency_code TEXT NOT NULL`

MWK is stored using scale 2, where MWK 1.00 equals 100 minor units.

## Constraint Rules

New schema changes should include explicit constraints where valid:

- `NOT NULL` for required business fields
- `CHECK` constraints for valid status values
- `CHECK (amount_minor >= 0)` only where negative amounts are not valid
- `CHECK (quantity > 0)` for asset quantities
- `UNIQUE` constraints for stable identifiers such as asset codes
- `FOREIGN KEY` constraints for linked users, accounts, transactions, projects, loans, and assets

Do not rely on negative values to encode debit and credit meaning. Use explicit event type, account, debit, credit, and reversal semantics.

## Index Rules

Indexes should be added for common filters and joins:

- user IDs
- workspace IDs
- account IDs
- transaction dates
- category IDs
- project IDs
- loan IDs
- asset codes
- serial numbers
- status fields
- audit timestamps

## Migration Rules

Schema changes must be versioned, non-destructive, and tested with temporary databases. Existing user data must be copied forward and reconciled after migration.

## Current Limitations

- A complete versioned migration chain is still pending.
- Monetary `REAL` replacement is not complete.
- Full foreign-key and check-constraint coverage is not complete.
