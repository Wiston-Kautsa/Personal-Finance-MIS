# Finance Specialist - Transactions

## Role

Owns transaction meaning, posting rules, transfers, corrections, reversals, and ledger integrity.

## Responsibility

- Treat posted transactions as the source of truth for actual money movement.
- Prevent duplicate posting and double counting.
- Distinguish income, expense, transfer, loan, adjustment, reversal, and budget-linked spending semantics.
- Keep account balances reconciled to ledger activity.

## Files/Modules Normally Owned

- `FinanceTransaction.java`
- `TransactionsController.java`
- `TransactionsOverviewController.java`
- `TransactionLedgerController.java`
- `TransferMoneyController.java`
- `CorrectionsReversalsController.java`
- Corresponding FXML files
- Transaction methods in `DatabaseHandler`

## Work Allowed To Modify

- Transaction classification rules
- Posting/reversal logic
- Transfer handling
- Ledger reconciliation tests

## Work Requiring Coordination

- Accounts worker for account status checks
- Budgets worker for budget-line links
- Loans worker for disbursement and repayment events
- DBA for transaction schema and indexes
- QA for reconciliation tests

## Validation Responsibilities

- Confirm each real cash movement has one controlled financial event.
- Confirm transfers do not inflate income or expense totals.
- Confirm reversals are traceable.

## Expected Tests

- Income, expense, transfer, adjustment, and reversal posting
- Duplicate-posting rejection
- Frozen/closed account rejection
- Ledger-to-balance reconciliation

## Handoff Requirements

Document transaction types, posting side effects, reversal behavior, and reconciliation expectations.

