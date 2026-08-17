# PFMIS Multi-Worker Development And Git Workflow

PFMIS is developed through persistent specialist worker definitions and short-lived task branches.

```text
Worker = Responsibility
Branch = Current Task
Main = Approved System
```

Workers do not represent permanent Git branches. A worker answers who owns a technical responsibility. A branch answers what specific piece of work is being implemented now.

The final authoritative system remains on `main`.

## Governance Flow

Every significant feature follows this control structure:

```text
System Owner / Product Owner
        |
        v
Technical Lead / Architect
        |
        v
Specialist Worker(s)
        |
        v
QA / Testing Engineer
        |
        v
Code Review / Integration Worker
        |
        v
main
```

Implementation completion is not production readiness. A feature reaches `main` only after architecture review, implementation, testing, integration review, and merge approval.

## Worker Execution Rule

Workers are not simulated labels. For each task branch, every relevant worker must inspect the current branch, inspect existing changes, perform the real work owned by that responsibility, validate it, and commit any repository changes it made before handing off to the next worker.

Required handoff flow:

```text
Requirement
        |
        v
Technical Lead / Architect
        |
        v
Relevant Specialist Worker(s)
        |
        v
Implementation
        |
        v
Each modifying worker commits its own work
        |
        v
QA / Testing
        |
        v
Code Review / Integration Worker
        |
        v
main
```

A worker that reviews a feature and finds no repository change is needed must record that outcome in the branch notes or review documentation instead of creating an empty or artificial commit.

Before starting work, each worker must inspect:

```powershell
git status
git log --oneline -15
git diff
```

Workers must inspect upstream worker output before implementing dependent changes. Do not replace another worker's valid work to impose a preferred implementation. Resolve conflicts by understanding the business rule and coordinating with the Architect and Integration Worker.

## Current Repository Context

PFMIS is an existing Java 21, JavaFX, Maven, and SQLite desktop application. Do not rebuild it from scratch.

Important current structure:

- JavaFX controllers: `src/main/java/com/wk/pfmis/controllers`
- FXML views: `src/main/resources/com/wk/pfmis/views`
- CSS: `src/main/resources/com/wk/pfmis/css`
- Domain objects: `src/main/java/com/wk/pfmis/domain`
- Models: `src/main/java/com/wk/pfmis/models`
- Security: `src/main/java/com/wk/pfmis/security`
- Authentication bootstrap/storage: `src/main/java/com/wk/pfmis/auth`
- Configuration: `src/main/java/com/wk/pfmis/config`
- Database and current schema/migration logic: `src/main/java/com/wk/pfmis/db/DatabaseHandler.java`
- Services: `src/main/java/com/wk/pfmis/services`
- Tests: `src/test/java/com/wk/pfmis`
- Architecture notes: `docs/ARCHITECTURE.md`
- Financial rules: `docs/FINANCIAL_RULES.md`
- Database rules: `docs/DATABASE_SCHEMA.md`
- Redesign baseline analysis: `docs/PFMIS_REDESIGN_INITIAL_ANALYSIS.md`

Controllers should validate UI input, call services, and update views. Business services should own financial rules and transaction boundaries. Database code should preserve existing data through safe migrations.

The current baseline analysis must be read before any of the Dashboard, Account Lifecycle, Loan Workflow, or Budget Workflow task branches begin.

## Worker Definitions

Permanent worker definitions live under `workers/`.

Each worker definition documents:

- Role
- Responsibility
- Files/modules normally owned
- Work the worker may modify
- Work requiring coordination
- Validation responsibilities
- Expected tests
- Handoff requirements

Workers must not blindly edit unrelated modules.

## Branch Strategy

Use task branches, not worker branches.

Do not create permanent branches such as:

```text
database
frontend
backend
finance-engine
analytics-dashboard
security
```

Create branches for specific work:

```text
feature/dashboard-analytics
feature/account-lifecycle-management
feature/loan-workflow-redesign
feature/budget-workflow-redesign
fix/account-delete-permissions
fix/transaction-double-counting
security/env-credential-cleanup
docs/multi-worker-governance
```

Each branch should be created from the latest `main`, solve one clearly defined area, pass review and tests, merge into `main`, and then be closed.

## Existing Branch Handling

Existing branches must be reclassified by task outcome, not by worker identity.

At the time this workflow was added, the repository had:

- `main`: authoritative production line.
- `agent/pfmis-system-local-ai`: an older agent/task branch that is already an ancestor of `main`.

For existing branches:

1. Fetch remote state.
2. Check whether the branch is already included in `main`.
3. If the branch is already included, mark it completed and close/delete it after repository-owner approval.
4. If the branch has unmerged work, identify the specific task or tasks it contains.
5. If the branch name is worker-based or too broad, create one or more task branches from latest `main`.
6. Move only the relevant scoped changes into the new task branch.
7. Review, test, and merge through the normal workflow.
8. Retire the old broad branch after the useful work is integrated or deliberately rejected.

Useful checks:

```powershell
git fetch origin
git branch --all --verbose --no-abbrev
git merge-base --is-ancestor <branch> main
```

If a branch is already merged, `git merge-base --is-ancestor <branch> main` exits with code `0`.

Do not delete local or remote branches automatically unless the repository owner explicitly requests that cleanup.

## Standard Task Workflow

For each task:

```powershell
git checkout main
git pull
git checkout -b <task-branch>
```

Then:

1. Architect reviews affected architecture and implementation boundary.
2. Financial Logic Specialist defines financial rules where money, balances, budgets, loans, reports, or transactions are involved.
3. DBA defines schema/migration impact where persistence changes are needed.
4. Backend implements services, validation, repositories, and transaction boundaries.
5. Frontend/UI workers implement FXML, controllers, layout, navigation, and state handling.
6. Security and Audit workers review sensitive operations.
7. QA tests normal, boundary, invalid, and regression workflows.
8. Integration worker reviews compatibility and prepares merge into `main`.

Do not automatically merge incomplete work.

## Worker Commit And Push Rules

Each worker that modifies the repository stages only the files belonging to that completed responsibility and creates a meaningful commit before handoff.

Examples:

```text
arch(loan): define redesigned loan workflow boundaries
feat(finance): implement loan repayment calculation rules
feat(database): add loan repayment schedule migration
feat(backend): implement loan disbursement and repayment services
feat(ui): implement redesigned loan registration wizard
style(ui): improve loan overview and repayment schedule
feat(analytics): add loan position dashboard metrics
security(loans): enforce loan modification permissions
test(loans): add repayment and settlement regression tests
docs(loans): document redesigned loan workflow
fix(integration): reconcile budget transaction workflow
```

After each worker commit, push the task branch:

```powershell
git push -u origin <task-branch>
```

After the upstream branch exists:

```powershell
git push
```

Do not leave completed worker commits only on the local machine. Do not create empty commits just to show a worker participated.

## Initial Analysis Required Before Code Changes

Before editing application code, trace the affected workflow through:

```text
UI
|
v
Controller / ViewModel
|
v
Service
|
v
Repository / DatabaseHandler
|
v
SQLite database
```

Identify existing controllers, FXML, CSS, models, services, database tables, migrations, reports, tests, audit logging, authorization checks, and financial calculations before adding new code.

Do not duplicate existing functionality. Fix or extend the existing implementation when practical.

## Primary Redesign Task Branches

### Dashboard

Branch: `feature/dashboard-analytics`

Primary workers:

- Dashboard & Analytics Worker
- Financial Logic Specialist
- UI/UX Designer
- Backend Developer
- Frontend / JavaFX Developer
- QA / Testing Engineer

The dashboard should answer useful financial questions with real system data:

- Total active financial balance
- Income for selected period
- Expenses for selected period
- Net cash flow
- Budget utilization
- Money owed
- Money owed to the user
- Upcoming commitments
- Spending categories
- Account balance distribution
- Loan position

Use filters such as this month, last month, last 3 months, last 6 months, this year, previous year, and custom range. Do not hard-code years.

Dashboard drill-downs should navigate to filtered transactions, budgets, loans, or account details as appropriate.

### Account Lifecycle

Branch: `feature/account-lifecycle-management`

Primary workers:

- Financial Logic Specialist
- UI/UX Designer
- Backend Developer
- Frontend / JavaFX Developer
- Database Administrator
- Security Engineer
- Audit & Compliance Worker
- QA / Testing Engineer

Use this lifecycle:

```text
ACTIVE
FROZEN
ARCHIVED
CLOSED
```

`DELETED` is allowed only where soft deletion already fits the architecture.

Account balances must not be edited directly. Corrections require controlled balance-adjustment transactions with amount, direction, reason, date, user, and audit reference.

Frozen and closed accounts must reject invalid financial activity. Archived accounts should be removed from active views but remain available for history and reports.

### Loan Workflow

Branch: `feature/loan-workflow-redesign`

Primary workers:

- Loans Financial Specialist
- UI/UX Designer
- Backend Developer
- Frontend / JavaFX Developer
- Database Administrator
- Analytics Worker
- QA / Testing Engineer

The first user decision is:

```text
I Borrowed Money
I Lent Money
```

Loan money movements must create proper financial transactions. Do not directly manipulate account balances.

Implement or preserve:

- Loan overview
- Borrowed loans
- Money lent
- Principal, interest, fees, and terms
- Disbursement/source/receiving accounts
- Repayment schedule
- Partial payment
- Full payment
- Early settlement
- Overdue detection
- Outstanding balance
- Loan status
- Payment history

Prevent duplicate disbursements, duplicate repayments, negative balances, payments against settled/cancelled loans, and mislabeled interest calculations.

### Budget Workflow

Branch: `feature/budget-workflow-redesign`

Primary workers:

- Budget Financial Specialist
- UI/UX Designer
- Backend Developer
- Frontend / JavaFX Developer
- Database Administrator
- Analytics Worker
- QA / Testing Engineer

Core rule:

```text
Budget = Plan
Transaction = Actual
```

Creating or activating a budget must not create expense transactions.

Actual spending comes from transactions linked to a budget and budget line. Multiple transactions may apply to one budget line. Budget variance is calculated as:

```text
Variance = Planned - Actual
```

When active budgets with financial activity are changed, use a budget revision record instead of silently overwriting the original plan.

## Cross-Module Financial Integrity

Financial events must not be double counted.

Rules:

- Loan disbursement into an account increases that account balance but is not ordinary income.
- Account transfers move value between accounts and do not increase wealth.
- Budget creation is planning and does not create spending.
- Budget execution is a real transaction linked to the budget.
- Loan repayments update transaction history, the loan schedule, and outstanding balances from one controlled financial event.

## Audit Requirements

Audit sensitive operations including:

- Account freeze, unfreeze, archive, restore, close, and permanent delete
- Balance adjustments
- Budget revisions
- Loan modifications, cancellations, write-offs, and payment reversals
- Data reset where supported
- Permission-sensitive administrative actions

Record user, timestamp, action, entity, entity ID, previous value, new value, and reason where appropriate.

## UI/UX Standards

All modules must follow one PFMIS design system.

Maintain consistent typography, spacing, CSS, tables, form layouts, section titles, navigation, primary actions, destructive actions, readable text, and panel sizes.

Do not overcrowd screens. Do not place every possible operation as a top-level button. Prefer detail panels, context actions, menus, and focused toolbars.

Required fields must show a red `*`, validate at UI and service level, highlight invalid fields, show inline text, and focus the first invalid field.

## QA Expectations

QA tests complete financial workflows, not just screen loading.

Validate database state, account balances, transaction records, loan balances, budget totals, dashboard figures, audit records, permissions, and error handling.

Reject a feature if it causes duplicate transactions, double counting, incorrect loan calculations, incorrect budget variance, frozen-account transactions, missing historical reports, broken foreign keys, or unsafe deletion.

## Integration Review

Before merging into `main`, the Integration Worker checks:

- Architecture remains coherent.
- Migrations are valid and data-preserving.
- Calculations reconcile with source records.
- No duplicate financial events exist.
- No direct balance mutation was introduced.
- Security and audit rules are intact.
- UI remains consistent.
- Navigation and reports still work.
- Tests pass.

## Commit Quality

Use meaningful commits:

```text
feat(accounts): implement account lifecycle management
feat(loans): redesign loan registration workflow
feat(budget): separate planned and actual expenditure
feat(dashboard): add cash-flow analytics
fix(finance): prevent transfer double counting
fix(accounts): block transactions on frozen accounts
test(loans): add partial repayment scenarios
docs(architecture): document account lifecycle model
```

Avoid vague commits such as `update`, `changes`, `fix stuff`, or `working`.

## Final Delivery Rule

No unfinished branch should become the de facto production system.

No feature should reach `main` without:

```text
Architecture Review
        |
        v
Implementation
        |
        v
Testing
        |
        v
Integration Review
        |
        v
main
```
