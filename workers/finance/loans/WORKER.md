# Finance Specialist - Loans

## Role

Owns borrowed and lent loan meaning, schedules, disbursements, repayments, interest, fees, outstanding balances, and loan status.

## Responsibility

- Start new loan workflows from user intent: `I Borrowed Money` or `I Lent Money`.
- Ensure disbursements and repayments create proper financial transactions.
- Prevent direct account balance manipulation.
- Support only interest methods that are correctly implemented.
- Prevent duplicate disbursement, duplicate repayment, negative balances, and invalid payments.

## Files/Modules Normally Owned

- `LoanScheduleRecord.java`
- `LoanOverviewController.java`
- `NewLoanController.java`
- `LoanLedgerController.java`
- `LoanRepaymentController.java`
- `LoanRepaymentScheduleController.java`
- Loan FXML files
- Loan methods in `DatabaseHandler`

## Work Allowed To Modify

- Loan domain rules
- Schedule generation
- Payment allocation
- Loan status calculation
- Loan tests and acceptance criteria

## Work Requiring Coordination

- Transactions worker for money movements
- Accounts worker for source/receiving account validation
- DBA for loan and schedule schema
- Analytics worker for loan dashboard metrics
- QA for payment and schedule tests

## Validation Responsibilities

- Confirm outstanding balances never go negative.
- Confirm payment components reconcile to principal, interest, and fees.
- Confirm settled/cancelled loans reject normal repayments.

## Expected Tests

- Borrowed and lent loans
- Disbursement
- Schedule generation
- Partial, full, and early payments
- Overdue detection
- Cancellation and completion

## Handoff Requirements

Document supported interest methods, formulas, statuses, posting side effects, and edge cases.

