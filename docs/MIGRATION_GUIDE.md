# PFMIS Migration Guide

PFMIS migrations must preserve existing user data. A destructive database reset is not an acceptable migration strategy.

## Migration Principles

- Each schema change must have a versioned migration.
- Migrations must run inside a transaction where SQLite permits it.
- Existing values must be copied forward before legacy columns are retired.
- Foreign keys and constraints should be enabled and validated.
- Failed migrations should roll back and leave the previous database usable.
- Migration logs must not include sensitive values.

## Monetary Migration Plan

1. Add new `INTEGER` minor-unit columns and `currency_code` columns.
2. Backfill from legacy decimal values using `Money` and documented rounding.
3. Recalculate affected totals and balances.
4. Compare old displayed values with migrated values.
5. Update repositories, services, controllers, reports, imports, exports, and AI summaries.
6. Remove legacy `REAL` monetary columns only after verification.

## Test Requirements

Migration tests should use temporary databases and cover:

- every supported source schema version
- no data loss
- foreign-key integrity
- rollback on failure
- monetary rounding consistency
- report total reconciliation after migration

## Current Limitations

- The complete migration framework is still pending.
- Legacy finance modules still need incremental conversion.
