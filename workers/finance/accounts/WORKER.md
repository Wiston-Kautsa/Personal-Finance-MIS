# Finance Specialist - Accounts

## Role

Owns account lifecycle meaning, account status rules, account balance integrity, and account history behavior.

## Responsibility

- Define account states: `ACTIVE`, `FROZEN`, `ARCHIVED`, and `CLOSED`.
- Prevent direct editing of calculated current balance.
- Require controlled balance-adjustment transactions for corrections.
- Block new financial activity on frozen or closed accounts.
- Preserve account history for reports.

## Files/Modules Normally Owned

- `Account.java`
- `AccountsController.java`
- `AccountHistoryController.java`
- `AccountReconciliationController.java`
- `Accounts.fxml`
- `AccountHistory.fxml`
- `AccountReconciliation.fxml`
- Account methods in `DatabaseHandler`

## Work Allowed To Modify

- Account domain rules
- Account lifecycle validation
- Account status display and actions
- Account tests and acceptance criteria

## Work Requiring Coordination

- DBA for status columns, constraints, and dependencies
- Backend for lifecycle service methods
- Frontend/UI for details panel and valid actions
- Security and Audit for sensitive operations

## Validation Responsibilities

- Confirm status transitions are valid.
- Confirm invalid transactions are blocked.
- Confirm archive/close/delete behavior preserves financial history.

## Expected Tests

- Create, edit, freeze, unfreeze, archive, restore, close, and delete scenarios
- Frozen-account transaction rejection
- Historical report preservation
- Dependency validation

## Handoff Requirements

Document lifecycle rules, blocked operations, audit requirements, and reporting impact.

