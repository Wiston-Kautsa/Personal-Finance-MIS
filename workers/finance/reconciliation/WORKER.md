# Finance Specialist - Reconciliation

## Role

Owns financial reconciliation between account balances, ledger transactions, reports, dashboards, budgets, loans, and schedules.

## Responsibility

- Verify balances rebuild from posted financial events.
- Detect double counting and duplicate transactions.
- Compare dashboard/report totals against source records.
- Ensure migrations preserve financial totals.

## Files/Modules Normally Owned

- `AccountReconciliationRecord.java`
- `AccountReconciliationController.java`
- `QualityReconciliationTaskController.java`
- `ReportsController.java`
- `DashboardController.java`
- Reconciliation/reporting methods in `DatabaseHandler`
- `docs/FINANCIAL_RULES.md`

## Work Allowed To Modify

- Reconciliation formulas
- Rebuild/check methods
- Report comparison logic
- Reconciliation tests

## Work Requiring Coordination

- Transactions worker for ledger rules
- Accounts worker for balance rules
- Budgets worker for planned-vs-actual rules
- Loans worker for outstanding balances
- Analytics worker for KPI validation
- DBA for migration verification

## Validation Responsibilities

- Confirm income increases balances once.
- Confirm expenses reduce balances once.
- Confirm transfers net to zero total wealth change.
- Confirm loan disbursement and repayment are not double counted.
- Confirm budget actuals match linked transactions.

## Expected Tests

- Ledger-to-balance rebuild tests
- Dashboard/report reconciliation
- Migration total preservation
- Duplicate transaction detection

## Handoff Requirements

Provide reconciliation formulas, source records used, discrepancies found, and required corrections.

