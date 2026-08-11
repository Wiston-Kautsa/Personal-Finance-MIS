# Chipeleganyu Missed Contributions

PFMIS now stores Chipeleganyu monthly installment rows separately from account transactions.

## Statuses

Each Chipeleganyu monthly contribution can use:

- `UPCOMING`
- `DUE`
- `PAID`
- `PARTIALLY_PAID`
- `MISSED`
- `OVERDUE`
- `FAILED_AUTOMATIC_DEDUCTION`
- `WAIVED`
- `CANCELLED`

## Missed Contribution Rule

`MISSED` means the user confirmed that no contribution was made for that month.

When a contribution is marked missed:

- no account transaction is created
- no account balance is reduced
- amount paid remains zero
- expected amount remains recorded
- reason, notes, confirmation date and audit log are recorded
- the row remains visible in the schedule and summary

## Reasons

Supported missed reasons:

- `INSUFFICIENT_FUNDS`
- `PAYMENT_NOT_MADE`
- `GROUP_ALLOWED_SKIP`
- `USER_ABSENT`
- `PAYMENT_DEFERRED`
- `OTHER`

## Paid Installments

If a contribution already has posted money movement, it cannot be directly marked missed. The posted transaction must be reversed first so the schedule and ledger do not disagree.

## Later Settlement

A missed or partially paid contribution can be settled later. Settlement creates a real account transaction through the existing contribution-posting path, then updates the installment to `PAID` or `PARTIALLY_PAID`.

## Automatic Deduction

The data layer now exposes status rules for automatic deduction:

- `MISSED`, `WAIVED`, `CANCELLED` and `PAID` block automatic deduction.
- `FAILED_AUTOMATIC_DEDUCTION` records that no money moved.
- A failed automatic deduction can later be changed to `MISSED`.

The full background automatic-deduction scheduler is still pending.
