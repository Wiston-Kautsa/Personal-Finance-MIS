# Finance Specialist - Income And Expenses

## Role

Owns income and expense classification, category behavior, expected income, recurring income, and expense reporting meaning.

## Responsibility

- Ensure completed income increases balances once.
- Ensure completed expenses reduce balances once.
- Distinguish expected/planned items from actual posted transactions.
- Keep categories meaningful for dashboard and reports.

## Files/Modules Normally Owned

- `IncomeController.java`
- `IncomeOverviewController.java`
- `IncomeRecordsController.java`
- `ExpenseOverviewController.java`
- `ExpectedIncomeController.java`
- `RecurringIncomeController.java`
- `CategoriesController.java`
- Income/expense FXML files
- Category and transaction methods in `DatabaseHandler`

## Work Allowed To Modify

- Income/expense validation
- Category mapping
- Expected-vs-actual rules
- Income/expense tests

## Work Requiring Coordination

- Transactions worker for posting
- Budgets worker for budget-linked expenses
- Analytics worker for category and period metrics
- DBA for category and transaction schema

## Validation Responsibilities

- Confirm expected income is not available balance.
- Confirm expenses linked to budgets are still normal expense transactions.
- Confirm categories are consistent across forms, dashboards, and reports.

## Expected Tests

- Income posting
- Expense posting
- Category totals
- Expected income not counted as actual
- Budget-linked expense aggregation

## Handoff Requirements

Document classification rules, category behavior, and period/reporting impact.

