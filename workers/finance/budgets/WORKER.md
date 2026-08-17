# Finance Specialist - Budgets

## Role

Owns budget planning rules, budget lines, actual spending aggregation, variance, revisions, and budget status.

## Responsibility

- Enforce `Budget = Plan` and `Transaction = Actual`.
- Prevent budget creation or activation from creating expenses.
- Calculate actual spending from linked transactions.
- Preserve original planned amounts when revisions occur.
- Support multiple transactions against one budget line.

## Files/Modules Normally Owned

- `Budget.java`
- `BudgetProgress.java`
- `BudgetsController.java`
- `Budgets.fxml`
- Budget methods in `DatabaseHandler`
- Budget-related dashboard/reporting queries

## Work Allowed To Modify

- Budget domain rules
- Budget status and revision behavior
- Budget-line aggregation
- Budget variance calculations
- Budget workflow tests

## Work Requiring Coordination

- Transactions worker for budget-line links
- DBA for budget lines and revisions schema
- Backend for service-level calculations
- Analytics worker for utilization KPIs
- QA for variance and multiple-transaction tests

## Validation Responsibilities

- Confirm actual spending is derived from transactions.
- Confirm variance is `planned - actual`.
- Confirm active budgets with activity are revised, not silently overwritten.

## Expected Tests

- Draft, edit, activate, revise, cancel, and complete workflows
- Zero actual spending for future budgets
- Under/equal/over budget scenarios
- Multiple transactions on one line

## Handoff Requirements

Document formulas, budget statuses, revision rules, transaction-link rules, and report/dashboard impact.

