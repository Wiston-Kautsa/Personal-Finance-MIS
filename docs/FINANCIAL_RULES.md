# PFMIS Financial Rules

PFMIS is a personal finance application. Monetary calculations must be deterministic, auditable, and safe for financial records.

## Monetary Value Model

New financial code should use `com.wk.pfmis.domain.Money`.

The value object stores:

- `amountMinor` as a signed `long`
- `currencyCode` as an uppercase ISO currency code

The preferred database representation is an `INTEGER` column containing minor currency units, plus a `currency_code` column.

## MWK Scale

PFMIS treats MWK as a two-decimal currency for system storage:

- MWK 1.00 = 100 minor units
- MWK 5,000.25 = 500,025 minor units

This preserves compatibility with ISO currency metadata and avoids binary floating-point errors. UI screens may display whole-kwacha values when that is appropriate, but stored values remain integer minor units.

## Rounding

The default rounding rule is `HALF_UP`.

Every calculation that converts from a decimal major amount to minor units must provide or inherit an explicit rounding mode. Domain-specific rules may override this only when documented, tested, and applied consistently across forms, reports, imports, exports, and AI summaries.

## Prohibited Financial Practices

New code must not:

- Use `double` or SQLite `REAL` for monetary persistence.
- Convert `Money` to `double` for calculations.
- Update account balances without a corresponding posted financial event.
- Treat expected payouts as available account balances.
- Create assets from budgets or completed projects without formal recognition checks.

## Migration Position

The current application still contains legacy monetary fields stored as Java `double` and SQLite `REAL` in several modules. These must be migrated incrementally using versioned, non-destructive database migrations.

Migration requirements:

- Preserve existing user records.
- Add integer minor-unit columns beside legacy columns where needed.
- Backfill minor-unit values using the documented rounding rule.
- Validate totals before and after migration.
- Keep rollback paths for failed migrations.
- Do not reset or replace user databases.

## Reconciliation

Reports and balances should reconcile to posted ledger records. A balance rebuild function should calculate account balances from posted ledger entries and report any difference between stored and calculated balances.

The required reconciliation tests include:

- income increases balances exactly once
- expenses reduce balances exactly once
- transfers produce matching debit and credit records
- draft transactions do not affect balances
- duplicate posting is blocked
- report totals equal ledger totals
