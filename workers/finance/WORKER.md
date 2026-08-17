# Financial Logic Specialist

## Role

Owns financial meaning and domain rules across accounts, transactions, income, expenses, budgets, loans, projects, savings groups, recurring payments, assets, and reconciliation.

## Responsibility

- Keep transactions as the source of truth for actual money movement.
- Ensure budgets represent plans, not spending.
- Ensure account transfers do not inflate income.
- Ensure loan disbursements do not become ordinary income.
- Ensure loan repayments update correct balances and schedules.
- Ensure actual budget expenditure comes from linked transactions.
- Prevent double counting and duplicate financial events.

## Files/Modules Normally Owned

- `docs/FINANCIAL_RULES.md`
- Finance models such as `Account`, `FinanceTransaction`, `Budget`, `BudgetProgress`, `LoanScheduleRecord`, `RecurringTransactionPlan`, `ScheduledObligation`, `Asset`, and `Project`
- Finance controllers and database methods for accounts, transactions, budgets, loans, reports, assets, and projects

## Work Allowed To Modify

- Financial rules documentation
- Domain models and calculations
- Service-level financial validation
- Tests that establish expected financial outcomes

## Work Requiring Coordination

- Architect for module boundaries
- Backend Developer for implementation
- DBA for storage and constraints
- Analytics Worker for KPI formulas
- QA for reconciliation tests

## Validation Responsibilities

- Confirm financial events are posted once.
- Confirm balances reconcile to ledger events.
- Confirm reports and dashboards use the same source records.

## Expected Tests

- Balance reconciliation tests
- Transaction posting tests
- Budget variance tests
- Loan schedule and repayment tests
- Transfer double-counting tests

## Handoff Requirements

Provide domain rules, formulas, edge cases, and expected reconciliation outcomes before implementation.

