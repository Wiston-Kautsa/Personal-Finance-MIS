# PFMIS Financial Redesign Product Requirements Baseline

Baseline date: 2026-08-17

Owner: System Owner / Product Owner

Source context:

- `workers/owner/WORKER.md`
- `docs/PFMIS_REDESIGN_INITIAL_ANALYSIS.md`
- Existing repository architecture and financial rules documented in `docs/FINANCIAL_RULES.md`

This document defines the product baseline and acceptance criteria for the PFMIS financial remediation and redesign work. It is a requirements baseline for implementation branches. It does not claim that the redesigned behavior has already been implemented.

## Product Intent

PFMIS must remain a personal finance management information system that preserves existing user records while improving the correctness, auditability, and usability of core financial workflows.

The redesign must make Dashboard, Accounts, Transactions, Budgets, Loans, Reports, and related modules reflect one consistent financial ledger. The Dashboard must present financial truth from approved services and aggregate queries; it must not become a separate source of financial calculations.

## Current Implementation Baseline

The existing application is a Java 21, JavaFX, Maven, and SQLite desktop application. It already contains substantial working modules, including accounts, transactions, budgets, borrowed loans, savings groups, assets, foreign exchange, audit logging, local AI, export/import, backup, authentication, and packaging assets.

Known implementation facts from the initial analysis:

- `DatabaseHandler.java` currently owns schema initialization, migration logic, CRUD, reporting, financial calculations, and many business rules.
- SQLite is the current persistence engine, with inline database initialization and migration behavior.
- Account balances are generally derived from opening balances plus posted valid ledger transactions, which is the correct product direction.
- Account lifecycle states already include `ACTIVE`, `FROZEN`, `ARCHIVED`, and `CLOSED`, with legacy `INACTIVE` present in some paths.
- Budgets are already planning records and budget creation does not create expense transactions.
- Budget actual spending is currently inferred mainly by category and month, which can misattribute the same expense to multiple budgets.
- The central loan engine already supports borrowed-loan registration, disbursement, schedules, payments, allocation, idempotency controls, insufficient-funds checks, and audit records.
- Money-lent behavior exists outside the central loan workflow and must be unified without duplicating the loan engine.
- Dashboard analytics currently rely on repeated SQL formulas and some recent-transaction limits. Some declared dashboard charts are not connected to the active Dashboard FXML.
- The project still contains legacy `double` and SQLite `REAL` monetary values, while new financial work should move toward `Money` and minor-unit arithmetic.
- Authentication documentation and startup behavior need alignment, especially around Super Administrator provisioning.

## Problems Being Corrected

The redesign must correct these confirmed product problems:

- Oversized persistence and business logic concentration in `DatabaseHandler`, addressed through incremental service and repository extraction.
- Direct controller-to-database coupling in touched modules, especially accounts, transactions, budgets, loans, and dashboard analytics.
- Duplicate financial meaning across account, dashboard, report, and balance SQL calculations.
- Dashboard metrics and charts that are stale, disconnected, or calculated from incomplete transaction samples.
- The misleading Dashboard label `Savings This Month` when the actual calculation is income minus expenses.
- Budget actuals inferred from category and month rather than explicit budget and budget-line transaction links.
- Budget domain shape that mixes budget headers and category allocations in one structure.
- Budget reports that do not consistently distinguish original planned amount, current planned amount, actual, variance, and remaining.
- Multiple loan concepts that split borrowed-money and lent-money behavior across competing workflows.
- Authentication and password-reset behavior that needs stronger token-based controls and consistent documentation.
- UI workflows that expose insufficiently contextual actions, unclear destructive actions, or financial calculations in controllers.
- Insufficient regression coverage for financial invariants, cross-module reconciliation, migrations, and UI-controller wiring.

## Scope

The release scope includes incremental remediation of the following modules and workflows:

- Financial core service boundaries for transaction effects, accounts, budgets, loans, and dashboard analytics.
- Account overview and lifecycle management for edit, freeze, unfreeze, archive, restore, close, and safe delete.
- Budget Domain V2 with budget headers, budget lines, revisions, explicit transaction links, and migration of existing budget data.
- Unified central loan engine with `BORROWED` and `LENT` loan directions.
- Dashboard Analytics V2 based on service-backed aggregate queries and period filtering.
- Authentication and password reset hardening using expiring one-time reset tokens or codes.
- Audit logging for sensitive financial and administrative actions.
- Cross-module financial checks for income, expenses, transfers, loans, projects, savings groups, recurring payments, assets, reports, and reconciliation.
- Documentation updates that describe implemented behavior accurately.
- Build, packaging, migration execution, and release validation.

## Out Of Scope

The following are explicitly out of scope for this redesign:

- Rebuilding PFMIS from scratch.
- Replacing SQLite with PostgreSQL.
- Resetting, dropping, or replacing user databases.
- Deleting historical financial records as part of migration.
- Removing working Savings Groups, FX, local AI, audit, backup/restore, export/import, or asset functionality.
- Rewriting the full `DatabaseHandler` in one large change.
- Adding a second financial engine for budgets, loans, balances, or reports.
- Creating fake production data.
- Hard-coding credentials, years, tokens, API keys, SMTP passwords, or other secrets.
- Directly setting account balances from edit forms.
- Performing a full monetary database migration from legacy `REAL` fields to minor units in a single unsafe step.
- Introducing enterprise approval complexity unless required by existing compatible workflows.

## Financial Invariants

All implementation branches must preserve these invariants:

- Ordinary external income increases wealth and available account balance exactly once.
- Ordinary expenses decrease wealth and available account balance exactly once.
- Transfers between owned accounts have zero total wealth effect.
- Borrowed loan disbursement increases cash or account balance and increases liability, but is not ordinary income.
- Borrowed loan repayment decreases cash and decreases liability according to principal allocation; interest, fees, and penalties must be classified without double counting the cash movement.
- Money lent decreases available cash and creates a receivable.
- Lent loan repayment increases cash and decreases receivable according to principal allocation.
- Budget creation has no cash, income, expense, asset, liability, or receivable effect.
- Budget revision has no cash, income, expense, asset, liability, or receivable effect.
- Budget actual spending is the sum of valid linked transactions, not a manually editable duplicate total.
- Multiple transactions may apply to the same budget line until the line is complete, over budget, cancelled, or otherwise terminal under defined status rules.
- Two budgets with the same category and overlapping period must not automatically share actual expenditure.
- Asset recognition must not create a second expense when the original purchase transaction already exists.
- Savings Group contributions and payouts must use their existing financial semantics and must not be counted twice in dashboards or reports.
- Scheduled or recurring future commitments are not completed financial transactions until posted.
- Frozen, archived, closed, cancelled, reversed, soft-deleted, and terminal records must be filtered consistently according to their module rules.
- New monetary calculations in touched areas must avoid new raw `double` money logic and should use `Money` or compatible minor-unit boundaries where practical.

## Required Workflows

### Accounts

The account workflow must support:

- Account overview with name, type, provider, currency, current balance, opening balance, status, last activity, and transaction count.
- Context-aware actions for edit, freeze, unfreeze, archive, restore, close, and delete when valid.
- Transaction blocking for frozen or closed accounts.
- Preservation of archived and closed accounts in historical reporting.
- Safe deletion only when an account has no financial history or dependencies and the authorized user confirms the action.
- Balance correction only through an explicit balance adjustment transaction with reason, before balance, adjustment, after balance, user, timestamp, and audit entry.

### Transactions And Financial Effects

The transaction workflow must support one authoritative financial meaning for every transaction purpose. Controllers may validate user input and call services, but financial effects must live in service/repository boundaries.

Transaction classification must distinguish:

- Ordinary income.
- Ordinary expense.
- Transfer debit and credit rows.
- Loan proceeds.
- Loan repayments and repayment components.
- Money-lent disbursement.
- Lent-loan repayment.
- Budget-linked expenses.
- Project-linked spending.
- Savings Group movements.
- Asset recognition references.

### Budgets

The budget workflow must support:

- Create Budget.
- Budget Details.
- Budget Lines.
- Review.
- Save Draft or Activate.

Budget details must include name, start date, end date, currency, description, optional project, and optional funding account.

Budget lines must include description, category, original planned amount, current planned amount, and status.

Budget reports and screens must distinguish:

- Original planned amount.
- Current or revised planned amount.
- Actual.
- Variance.
- Remaining.
- Status.

Budget revisions must record previous amount, new amount, reason, user, and date/time. Budget actuals must be derived from linked valid financial transactions.

### Budget Transaction Linking

Expense creation must optionally allow a valid active budget and budget line to be selected. Only valid active budget lines should be selectable. Posting an expense must create one financial expense event and store the budget references needed for actual-spending aggregation.

An expense linked to one budget line must not be attributed to another same-category budget line unless explicitly linked.

### Loans

The loan workflow must use the central loan architecture for both loan directions:

- `BORROWED`: I borrowed money.
- `LENT`: I lent money.

Loan creation must begin with the user decision:

- I Borrowed Money.
- I Lent Money.

Borrowed-loan creation must collect lender, principal, currency, borrowed date, interest method, interest rate, fees, frequency, instalments, first payment date, maturity, receiving account, and review information before save draft or activate/disburse.

Lent-loan creation must collect borrower, principal, currency, issue date, interest, fees, frequency, instalments, due dates, source account, and review information before save draft or issue.

Loan overview must show separate user-facing positions:

- Money I Owe.
- Money Owed to Me.
- Due This Month.
- Overdue.
- Paid This Month.

Loan payment workflows must support partial payment, full payment, early settlement, correct overpayment handling, duplicate prevention, cancelled-loan rejection, settled-loan rejection, and direction-correct accounting.

### Dashboard Analytics

Dashboard Analytics V2 must be implemented only after stabilized account, budget, and loan semantics are integrated.

The dashboard must support:

- Total Available Balance.
- Income.
- Expenses.
- Net Cash Flow or Net Surplus.
- Budget Utilization.
- Money I Owe.
- Money Owed to Me.
- Upcoming Commitments.
- Overdue Commitments where applicable.

The dashboard period selector must support:

- This Month.
- Last Month.
- Last 3 Months.
- Last 6 Months.
- This Year.
- Previous Year.
- Custom Range.

Changing the period must refresh relevant KPIs, charts, trends, and comparisons. Historical analytics must use complete aggregate queries for the selected period, not an arbitrary recent-transaction limit.

Declared dashboard charts must either be connected to visible FXML controls and populated with meaningful data or removed with the corresponding unused controller code.

### Security And Password Reset

The reset workflow must use expiring one-time reset tokens or codes where practical. It must include token hashing, expiration, one-time use, attempt limits, audit logging, and no password or token logging.

If email configuration is unavailable, the system must fail clearly without weakening the reset process. Credentials must come from secure configuration and must not be committed.

Code, README, installation instructions, `.env.example`, and security documentation must agree on Super Administrator provisioning and authentication setup.

### Projects

Budgets may optionally belong to projects. Project actual spending must be transaction-derived. A transaction that is both project-linked and budget-linked must not be counted twice.

### Savings Groups

Existing Bank Nkhonde and Chipeleganyu behavior must be preserved. Contributions, scheduled contributions, auto-deduction, schedules, history, account-status restrictions, and dashboard/report totals must continue to reconcile.

### Recurring Payments

Recurring payments must treat scheduled future commitments as commitments, not posted transactions. Execution must create one actual transaction, respect frozen or closed source accounts, support optional budget linkage, and avoid duplicate postings during retry.

### Assets

Asset recognition must reference the original purchase transaction when an asset originates from a purchase. Budget accomplishment, project completion, and asset recognition must remain separate concepts and must not create duplicate expense transactions.

### Audit And Compliance

Audit records must exist for account freeze, unfreeze, archive, restore, close, delete, balance adjustment, budget revision, budget cancellation, loan modification, loan cancellation, loan write-off, repayment reversal, password reset, and sensitive administrative actions.

Audit entries should include user, timestamp, action, entity type, entity ID, previous state, new state, and reason where applicable. Audit entries must not contain secrets.

### Local AI And Integrations

Local AI, FX, email, export/import, backup/restore, and existing API integrations must continue to work after financial refactoring. AI-generated financial insight must distinguish income, loans, budgets, transfers, and sensitive data boundaries. Integration failures must not corrupt financial transactions.

## Acceptance Criteria

### Release-Level Acceptance

The redesigned system can be accepted for merge to `main` only when:

- All required feature branches compile.
- Relevant unit and integration tests pass.
- Database migrations are non-destructive and validated against a populated legacy database.
- Account balances, transaction ledger totals, loan balances, budget actuals, reports, and dashboard totals reconcile in known scenarios.
- FXML files modified by the redesign parse successfully and match their controller fields and actions.
- No high-severity financial, security, migration, or launch regression remains open.
- Documentation describes actual implemented behavior rather than planned behavior.
- Feature branches have been integrated in dependency order into `integration/pfmis-financial-redesign`, tested there, and then merged to `main`.

### Financial Core Acceptance

- Touched controllers call services for financial behavior rather than directly calculating financial outcomes.
- A shared transaction-effect mechanism or equivalent shared service/query logic defines the financial meaning of transaction purposes.
- Transfers, loan proceeds, repayments, budget creation, budget revision, asset recognition, and savings group movements are classified without double counting.
- New money calculations in touched paths do not introduce new raw `double` calculations.

### Account Acceptance

- Account lifecycle actions are context-aware and respect authorization.
- Frozen accounts reject new financial postings.
- Archived and closed accounts remain available for historical reporting.
- Safe delete is available only for eligible unused accounts and writes an audit record.
- Balance adjustments are explicit financial transactions with audit metadata.

### Budget Acceptance

- Existing budget data is preserved through migration.
- Budget headers and budget lines are represented clearly in the new model or compatible versioned replacement.
- Creating or activating a budget creates no financial transaction.
- Budget actuals are calculated from linked valid transactions.
- Same-category overlapping budgets do not cross-attribute actual spending.
- Budget revision preserves original and current planned values and writes revision history.

### Loan Acceptance

- A loan direction field or equivalent model distinguishes `BORROWED` from `LENT`.
- Borrowed disbursement, borrowed repayment, lent disbursement, and lent repayment each produce correct financial effects.
- The central loan schedule/payment architecture is reused rather than duplicated.
- Existing central borrowed-loan behavior is preserved unless deliberately corrected by tested changes.
- Legacy lending records are migrated when deterministic and preserved for review when not deterministic.

### Dashboard Acceptance

- `Savings This Month` is renamed to `Net Cash Flow` or `Net Surplus` unless genuine savings contributions are being measured.
- Period filters update all relevant KPIs and charts.
- Dashboard historical analytics use aggregate queries for the selected period rather than `listRecentTransactions(500)`.
- Every chart declared in `DashboardController` is visible and populated, or its field and population code are removed.
- Dashboard KPIs reconcile to service-backed account, transaction, budget, and loan values.

### Security Acceptance

- Password reset uses expiring, one-time, attempt-limited tokens or codes with hashed storage.
- Reset and administrative actions write audit records without secrets.
- Missing email configuration fails clearly.
- No hard-coded credentials, tokens, SMTP passwords, or API keys are introduced.
- Authentication setup documentation matches actual code behavior.

### QA Acceptance

QA must test financial outcomes, not only screen loading. Required coverage includes:

- Account create, edit, freeze, unfreeze, archive, restore, close, valid delete, invalid delete, and historical reporting.
- Budget draft, activation, below-budget expense, exact-budget expense, over-budget expense, multiple expenses on one line, same-category overlapping budgets, revision, cancellation, and completion.
- Borrowed and lent loans, disbursement, repayment, partial payment, full payment, early settlement, overdue handling, duplicate rejection, cancelled-loan rejection, and legacy migration review.
- Dashboard totals and period selectors, including a data set with more than 500 transactions.
- Security reset-token invalid, expired, reused, and excessive-attempt cases.
- Authorization failures for restricted account and loan actions.
- Audit creation for sensitive actions.

## Required Branch And Worker Flow

Implementation must proceed in this dependency order:

1. Financial core and service boundaries.
2. Account lifecycle completion.
3. Budget normalization and explicit transaction linkage.
4. Unified `BORROWED` and `LENT` loan engine.
5. Dashboard analytics based on corrected financial models.
6. Authentication and reset hardening.
7. Cross-module regression and reconciliation.
8. Documentation and packaging.
9. Integration branch.
10. Merge to `main`.

Each worker that changes repository content must inspect upstream work, make scoped changes, validate, stage only its own files, commit with a meaningful message, and push the task branch when possible. Review-only workers must record review results instead of creating empty commits.

## Product Owner Approval Gate

The Product Owner can approve release integration only after the implementation report provides actual evidence for:

- Branches created and merged.
- Worker participation and commit hashes.
- Migrations applied and validated.
- Tests executed and results.
- Reconciliation scenario outputs.
- Security checks performed.
- Remaining technical debt.
- Remaining known defects.

Any unresolved high-severity issue involving incorrect balances, duplicate financial events, data loss, authentication bypass, irreversible migration failure, or broken application launch blocks merge to `main`.
