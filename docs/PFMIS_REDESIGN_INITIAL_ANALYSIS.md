# PFMIS Redesign Initial Analysis

Analysis date: 2026-08-17

This document records the repository analysis required before the Dashboard, Accounts, Loans, and Budgets redesign branches begin. It is not a replacement for feature-specific architecture review.

## Git State

The analysis was performed on `main`. The working tree already contained modified application files and untracked governance files, so no task branch was created during this pass. Before starting any redesign branch, cleanly commit, stash, or otherwise isolate the current working tree so unrelated edits are not carried into a feature branch.

## Build And Packaging

- Runtime stack: Java 21, JavaFX 21.0.5, Maven, SQLite.
- Main entry points: `src/main/java/com/wk/pfmis/Launcher.java`, `MainApp.java`, and `DbBootstrap.java`.
- Build file: `pom.xml`.
- Key dependencies: JavaFX controls/FXML, `sqlite-jdbc`, JNA/JNA Platform, SLF4J simple runtime, JUnit 5, JaCoCo.
- Packaging assets and scripts exist under `src/main/packaging`, `scripts`, `dist`, and the root run scripts.

## Project Structure

- JavaFX controllers: `src/main/java/com/wk/pfmis/controllers`
- FXML views: `src/main/resources/com/wk/pfmis/views`
- CSS/design system: `src/main/resources/com/wk/pfmis/css/Theme.css`
- Workspace database and most persistence logic: `src/main/java/com/wk/pfmis/db/DatabaseHandler.java`
- Authentication database: `src/main/java/com/wk/pfmis/auth/AuthDatabase.java`
- Security/session services: `src/main/java/com/wk/pfmis/security`
- Configuration: `src/main/java/com/wk/pfmis/config`
- Models: `src/main/java/com/wk/pfmis/models`
- Domain value objects: `src/main/java/com/wk/pfmis/domain`
- Services: `src/main/java/com/wk/pfmis/services`
- Utilities: `src/main/java/com/wk/pfmis/utils`
- AI and FX integrations: `src/main/java/com/wk/pfmis/ai` and `src/main/java/com/wk/pfmis/fx`
- Tests: `src/test/java/com/wk/pfmis`

## Database Shape

PFMIS currently uses SQLite with inline schema initialization and migration logic in `DatabaseHandler`. There is no visible Flyway migration directory in the current source tree.

Important workspace tables and views include:

- `accounts`
- `categories`
- `projects` and `project_activities`
- `people`
- `goals`, `goal_steps`, and `goal_contributions`
- `budgets`, `budget_revisions`, and `household_budget_members`
- `transactions`
- `valid_transactions`
- `loan_schedules`
- `loans`, `loan_installments`, `loan_payments`, and `loan_audit_log`
- `scheduled_obligations` and `recurring_transaction_plans`
- `account_reconciliations`
- community savings and Bank Nkhonde tables
- `assets` and `asset_events`
- `system_event_log`
- `deletion_requests`
- `schema_version` and `schema_migration_history`

Database changes for the redesign branches must continue to be non-destructive and compatible with the current inline migration approach unless the Architect and DBA deliberately introduce a formal migration framework.

## Workflow Traces

### Dashboard

`Dashboard.fxml` loads `DashboardController`. The controller owns navigation, dashboard refresh, chart/table population, and view loading through `FXMLLoader`.

Current data path:

```text
Dashboard.fxml
-> DashboardController.refreshDashboard()
-> DatabaseHandler.getDashboardStats()
-> DatabaseHandler report/query methods
-> accounts, transactions, valid_transactions, budgets, loans, projects
```

Existing dashboard values rely on `DashboardStats`, `listRecentTransactions`, `categorySpendingReport`, `accountBalanceReport`, and related reporting methods. Several balance formulas are repeated as SQL CASE expressions.

### Accounts

`Accounts.fxml` loads `AccountsController`.

Current data path:

```text
Accounts.fxml
-> AccountsController
-> DatabaseHandler.addAccount/updateAccount/updateAccountLifecycleStatus/deleteAccount/listAccounts
-> accounts, transactions, account_reconciliations, system_event_log
```

The current balance is derived from opening balance plus posted `valid_transactions`. It is not stored as a directly edited field. The UI already disables opening balance, account type, and currency in edit mode, but `DatabaseHandler.updateAccount` still accepts those values and should remain carefully guarded by controller/service contracts.

The lifecycle already includes `ACTIVE`, `FROZEN`, `ARCHIVED`, and `CLOSED`, with legacy `INACTIVE` still present in places. Account deletion uses the soft-delete pathway and Super Administrator guard.

### Transactions

Core transaction entry is handled through transaction, income, expense, and transfer controllers. The central posting path is `DatabaseHandler.recordTransaction`, while transfers use paired transfer rows through `recordTransferWithFee`.

Current data path:

```text
Transaction UI
-> Controller validation
-> DatabaseHandler.recordTransaction/recordTransferWithFee
-> transactions
-> valid_transactions
-> account/report/dashboard balance queries
```

Posting requires active accounts and validates transaction type, purpose, status, categories, dates, and related person/project context. Account balances are recalculated from ledger rows rather than mutated directly.

### Budgets

`Budgets.fxml` loads `BudgetsController`.

Current data path:

```text
Budgets.fxml
-> BudgetsController
-> DatabaseHandler.addBudgetPlanDraft/addBudget/updateBudget/updateBudgetGroupStatus/listBudgetProgress
-> budgets, budget_revisions, valid_transactions
```

The current implementation correctly treats budgets as planning records and does not create expense transactions during budget creation or activation. Actual spending is currently inferred by matching expense transactions by category and month. The redesign should add explicit transaction-to-budget-line linkage because category/month inference can overstate or misattribute actual budget use.

### Loans

Loan screens include `LoanOverview.fxml`, `NewLoan.fxml`, `LoanLedger.fxml`, `LoanRepayment.fxml`, and `LoanRepaymentSchedule.fxml`.

Current borrowed-loan path:

```text
NewLoan.fxml
-> NewLoanController.readCommand()
-> DatabaseHandler.registerBorrowedLoan()
-> loans, loan_installments, transactions, scheduled_obligations, loan_audit_log
```

Borrowed loan registration posts a `LOAN_PROCEEDS` transaction, creates central loan records, generates installments, writes audit entries, and creates scheduled obligations.

Repayment path:

```text
LoanRepayment.fxml
-> LoanRepaymentController
-> DatabaseHandler.recordCentralLoanPayment()
-> loan_payments, loan_installments, loans, transactions, scheduled_obligations, loan_audit_log
```

Repayment posting allocates payment across principal, interest, fees, and penalties, creates the financial transaction rows, guards idempotency, recalculates outstanding balances, and refreshes statuses.

The central loan workflow currently focuses on borrowed loans. Money-lent handling exists in older transaction purposes and lending reports, but it is not yet equivalent to the central borrowed-loan workflow.

## Existing Validation And UI Patterns

- Required field markers exist in FXML through red `*` labels and global helper support such as `RequiredFieldSupport`.
- Some modules use inline validation labels, especially the new loan form.
- Some modules still use generic `UiAlerts` popups for validation.
- `DashboardController.loadView` applies `RequiredFieldSupport` and `ReadableTextSupport` when loading views.
- The UI design system is centralized mainly through `Theme.css`, but dense module screens use different local patterns.

## Security And Audit

- User authentication and roles are stored in a separate auth database through `AuthDatabase`.
- `UserSession` tracks the signed-in user and the active workspace user.
- `PrivilegedActionService` supports time-limited password verification for sensitive actions.
- `DatabaseHandler.recordSystemLog` writes to `system_event_log`.
- Loans additionally write to `loan_audit_log`.
- Soft deletion, restoration, permanent purge, record disposal, and workspace reset are guarded by Super Administrator checks.
- Sensitive redesign work should reuse these mechanisms instead of adding a parallel audit system.

## Financial Integrity Observations

- Account balances are calculated from opening balances and posted transactions, which is the correct direction.
- Balance SQL is duplicated across account listing, dashboard stats, and account reports. Redesign branches should centralize or share this calculation before expanding analytics.
- The codebase still uses Java `double` and SQLite `REAL` for many monetary values despite `docs/FINANCIAL_RULES.md` recommending `Money` and integer minor units for new financial code.
- Budget actuals come from transactions, but the current join is category/month based, not budget-line based.
- Loan repayment creates several transaction rows for principal, interest, fees, and penalties. Reporting must treat these components carefully to avoid double counting cash movement and expense classification.
- Borrowed loan proceeds increase the receiving account but should not be classified as ordinary income.
- Account transfers are represented by linked outgoing/incoming rows and must not be counted as income.
- Soft-deleted and terminal/reversed records must be excluded consistently through `valid_transactions` and module-specific filters.

## Redesign Risks

- `DatabaseHandler` is very large and mixes schema, SQL, migration, reporting, and business logic. Feature work should introduce focused services or extracted helpers incrementally.
- Dashboard analytics may double count if it combines account-balance CASE logic, loan reports, transfer rows, and transaction totals without a shared definition.
- Account lifecycle work must not make `ARCHIVED` or `CLOSED` accounts disappear from historical reports.
- Budget work must not overwrite original plan values after actual spending exists.
- Loan redesign must distinguish borrowed and lent money without mapping both to the current borrowed-loan tables blindly.
- JavaFX controllers currently contain some workflow logic. New financial rules should move toward service/database boundaries and not expand controller-owned business rules.
- Existing tests are useful but do not yet cover the complete account, budget, loan, and dashboard workflow matrix required by the redesign.

## Feature Branch Kickoff Notes

Before starting each feature branch:

1. Return to a clean `main`.
2. Pull the latest remote changes.
3. Create one short-lived task branch.
4. Record participating workers in the branch kickoff note or PR description.
5. Trace the affected UI-to-database workflow before editing.
6. Add or update tests for the financial side effects changed by the branch.

Recommended branch order:

1. `feature/account-lifecycle-management`
2. `feature/budget-workflow-redesign`
3. `feature/loan-workflow-redesign`
4. `feature/dashboard-analytics`

This order lets dashboard analytics reuse stabilized account, budget, and loan semantics.
