# PFMIS Financial Service Boundaries

Status: implementation baseline for `refactor/financial-core-services`

Date: 2026-08-17

Workers: Technical Lead / Architect, Financial Logic Specialist, Transactions, Reconciliation, Backend, DBA, QA

## Boundary Direction

PFMIS keeps the existing JavaFX and SQLite application. The financial-core work is an incremental extraction, not a rewrite of `DatabaseHandler`.

Dependency direction for touched financial modules:

```text
Controller
    -> Service
        -> Repository
            -> DatabaseHandler / SQLite
```

Controllers may handle UI state, form validation display, and navigation. They must not become the authoritative source for balances, budget actuals, loan outstanding balances, or dashboard formulas.

## First Extraction Scope

The first shared boundary is transaction cash effect semantics:

- `FinancialTransactionEffect` defines account-balance impact for transaction type and purpose combinations.
- `DatabaseHandler` may reuse SQL fragments generated from that shared effect while broader repositories are introduced.
- Account listing, close checks, reconciliation balance-on-date, workspace balance, dashboard balance, available cash, community savings balance, and account balance reports should reuse the same account-balance effect expression.

This avoids a large rewrite while removing one practical source of financial drift.

## Repository And Service Targets

Subsequent feature branches should use or introduce these boundaries as their touched code requires:

- `AccountService` for lifecycle validation, safe delete, balance adjustment, and audit requests.
- `TransactionService` for posting, reversals, transfers, and transaction-effect classification.
- `BudgetService` and `BudgetRepository` for Budget V2 headers, lines, revisions, and linked actuals.
- `LoanService` and `LoanRepository` for borrowed and lent loan direction handling.
- `DashboardAnalyticsService` for period-aware aggregate KPIs and chart series.

These services may initially delegate to existing `DatabaseHandler` methods. Extraction is complete only when the controller no longer owns domain rules and the service/repository owns the financial contract.

## Financial Rules For The Shared Effect

The shared account-balance effect must preserve these invariants:

- Income and asset sale proceeds increase account balance.
- Expense rows decrease account balance.
- Transfer-out decreases one owned account and transfer-in increases the other, netting to zero total owned-account effect.
- Borrowed loan proceeds increase cash but are not ordinary income.
- Borrowed loan principal repayments decrease cash.
- Loan interest, fee, and penalty expense rows decrease cash through ordinary expense transaction type semantics.
- Money lent decreases cash and creates receivable meaning outside the cash-balance expression.
- Lent repayment increases cash and reduces receivable meaning outside the cash-balance expression.
- Budget creation and revision do not affect cash.
- Opening-balance source rows do not duplicate the account opening-balance field.

## Coordination Notes

- DBA must keep schema/index changes non-destructive and compatible with existing inline migrations.
- Budget and loan branches must not introduce separate duplicate balance formulas.
- Dashboard V2 must consume aggregate services built on these semantics, not `listRecentTransactions(500)`.
- QA must keep regression tests around transaction effect, ledger-to-balance reconciliation, and cross-module totals.

