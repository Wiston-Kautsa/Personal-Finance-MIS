# PFMIS Dashboard

The Dashboard provides high-level financial decision support from posted PFMIS records. It is designed to answer the current financial position, monthly cash movement, spending concentration, budget and goal progress, account balance position, savings status, loan obligations, and recent ledger activity.

## KPI Definitions

- Available Balance: active non-liability, non-system asset account balances shown in the reporting/base currency. Non-base balances require a saved FX rate before they are included.
- Monthly Income: posted income transactions for the current month.
- Monthly Expenses: posted expense transactions for the current month.
- Net Cash Flow: Monthly Income minus Monthly Expenses.

## Charts And Progress

- Cash Flow Trend: last six months of posted income, posted expenses, and net cash flow.
- Spending By Category: current-month posted expenses grouped dynamically by category. Extra categories are consolidated into `Other categories`.
- Account Balances: calculated account balances for dashboard-sized active account comparison.
- Budget / Goal Progress: current-month budget utilization and active goal completion as progress rows with actual, planned or target amounts, remaining amount, and status.
- Savings Summary: active Savings Group count, community savings balance, contribution totals, next due date, expected payout, cycle count, and loan position.

## Planning Versus Actual

- Posted: actual ledger activity that affects dashboard actuals and balances.
- Expected: planning signal only.
- Draft: not posted and not included in actual totals.
- Scheduled: future commitment shown as an attention item where relevant, not as completed financial activity.

## Refresh

Dashboard refresh reloads KPIs, cash-flow trend, category spending, account balances, budget and goal progress, savings summary, loan signals, scheduled obligation alerts, and the five latest posted transactions. Refresh runs on a background task and applies UI updates on the JavaFX Application Thread.

## Empty States

If data is unavailable, the dashboard shows explicit empty-state messages for charts and progress sections instead of sample values. No dashboard chart uses hard-coded demonstration data.
