# Finance Specialist - Recurring Payments

## Role

Owns recurring income, recurring expenses, scheduled transfers, planned obligations, and due-date behavior.

## Responsibility

- Keep scheduled commitments separate from actual posted transactions.
- Block automatic deductions from frozen or closed accounts.
- Generate or post transactions only through controlled rules.
- Surface upcoming commitments for dashboard analytics.

## Files/Modules Normally Owned

- `RecurringTransactionPlan.java`
- `ScheduledObligation.java`
- `PlannedRecurringExpensesController.java`
- `RecurringIncomeController.java`
- `ScheduledTransfersController.java`
- Corresponding FXML files
- Recurring/scheduled methods in `DatabaseHandler`

## Work Allowed To Modify

- Recurring schedule rules
- Upcoming commitment calculations
- Posting safeguards
- Recurring payment tests

## Work Requiring Coordination

- Accounts worker for account status blocking
- Transactions worker for actual posting
- Analytics worker for upcoming commitments
- DBA for schedule schema

## Validation Responsibilities

- Confirm scheduled items do not alter balances until posted.
- Confirm frozen/closed account schedules are blocked.
- Confirm due dates and recurrence calculations are deterministic.

## Expected Tests

- Recurring plan creation
- Due/upcoming calculations
- Blocked account posting rejection
- Posted transaction side effects

## Handoff Requirements

Document recurrence rules, posting rules, skipped/blocked behavior, and dashboard effects.

