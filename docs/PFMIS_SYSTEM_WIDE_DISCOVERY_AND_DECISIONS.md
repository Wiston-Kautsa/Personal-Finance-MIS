# PFMIS System-Wide Discovery And Decisions

Date: 2026-08-17

This record documents the first implementation phase for the system-wide maintenance, UI workspace, and database requirements. It is intentionally conservative: the running codebase must be changed through coordinated slices, not by claiming PostgreSQL or maintenance-service completion while active runtime code still uses SQLite.

## Runtime Architecture Inventory

| Area | Current Runtime Finding | Evidence |
| --- | --- | --- |
| Build system | Maven Java 21 project | `pom.xml` |
| Desktop entry | `com.wk.pfmis.Launcher` delegates to `MainApp.main` for packaged builds | `Launcher.java`, `scripts/build-windows-installer.ps1` |
| JavaFX startup | `MainApp.start` loads sign-in, then opens `Dashboard.fxml` after login | `MainApp.java` |
| Active authenticated shell | `Dashboard.fxml` with `DashboardController` | `MainApp.openWorkspace`, `Dashboard.fxml` |
| Runtime navigation | `DashboardController.loadView` loads `/com/wk/pfmis/views/*.fxml` into `contentPane` | `DashboardController.java` |
| Active CSS | `/com/wk/pfmis/css/Theme.css` | `MainApp.loadScene` |
| Shared layout helper | `WorkspaceLayoutSupport.apply` is already present and applied to routed Data Records tabs | `WorkspaceLayoutSupport.java`, `DataRecordsSectionController.java` |
| Table helper | `TableActions.configureScrollableTable` centralizes table column readability and vertical growth | `TableActions.java` |
| Single-instance runtime | `SingleInstanceManager` is present and acquired before JavaFX launch | `MainApp.main`, `startup/SingleInstanceManager.java` |
| Packaging | `jpackage` app image plus WiX-backed EXE installer | `scripts/build-windows-installer.ps1` |

## Database And Persistence Finding

Current runtime persistence is SQLite, not PostgreSQL.

| Area | Finding |
| --- | --- |
| JDBC dependency | `sqlite-jdbc` is the only database driver in `pom.xml`. |
| PostgreSQL dependency | No PostgreSQL JDBC driver or HikariCP dependency is present. |
| Auth database | `AuthDatabase` opens `jdbc:sqlite:` and creates `users`, `authentication_log`, and `auth_settings` in Java. |
| Workspace database | `DatabaseHandler` opens `jdbc:sqlite:` and creates/migrates business tables in Java. |
| Schema migration | Migrations are implemented inside Java code with `CREATE TABLE IF NOT EXISTS`, `PRAGMA`, `schema_version`, and `schema_migration_history`; Flyway is not present. |
| Backup/restore | Backup and restore workflows are SQLite file-copy and validation oriented. |
| PostgreSQL references | PostgreSQL appears in policy/help text only, not as an active runtime datasource. |

Conclusion: PostgreSQL cannot be marked complete in this branch. A real migration requires a coordinated data-access slice: add PostgreSQL driver/Hikari/Flyway or equivalent, define migrations, convert `AuthDatabase` and `DatabaseHandler`, migrate data, and remove SQLite as an operational source of truth.

## Active Screen Matrix

| Screen / Module | Active FXML | Controller | Service/Repository Boundary | Main Content |
| --- | --- | --- | --- | --- |
| Application shell | `Dashboard.fxml` | `DashboardController` | Direct `DatabaseHandler` plus routed controllers | Dashboard, sidebar, header, routed `contentPane` |
| Accounts | `Accounts.fxml` | `AccountsController` | Direct `DatabaseHandler` | Accounts table, details, account lifecycle |
| Account ledger | `AccountHistory.fxml` | `AccountHistoryController` | Direct `DatabaseHandler` | Account table and transaction history |
| Reconciliation | `AccountReconciliation.fxml` | `AccountReconciliationController` | Direct `DatabaseHandler` | Reconciliation working tables |
| Income | `Income.fxml`, `IncomeRecords.fxml`, `ExpectedIncome.fxml`, `RecurringIncome.fxml` | Income controllers | Direct `DatabaseHandler` / shared helpers | Forms, income records, expected/recurring records |
| Expenses | `Expenses.fxml`, `ExpenseOverview.fxml`, `PlannedRecurringExpenses.fxml` | Expense controllers | Direct `DatabaseHandler` / shared helpers | Expense forms, records, planned obligations |
| Transactions | `Transactions.fxml`, `TransferMoney.fxml`, `ScheduledTransfers.fxml`, `CorrectionsReversals.fxml` | Transaction controllers | `TransactionService`/`TransactionRepository` exists, but many modules still use `DatabaseHandler` | Ledger, transfer, schedules, corrections |
| Budgets | `Budgets.fxml` | `BudgetsController` | Direct `DatabaseHandler` | Budget plans, performance, household members, details |
| Projects | Project FXML set | Project controllers | Direct `DatabaseHandler` / shared helpers | Activities, finances, milestones, history |
| Loans | Loan FXML set | Loan controllers | Direct `DatabaseHandler` / shared helpers | Loan records, repayments, schedule |
| Savings Groups | `CommunitySavings.fxml` | `CommunitySavingsController` | Direct `DatabaseHandler` | Bank Nkhonde, Chipeleganyu, contributions, payouts |
| Assets | Asset FXML set | Asset controllers | Direct `DatabaseHandler` / shared helpers | Asset register, recognition, lifecycle events |
| Reports | `Reports.fxml`, `ReportInputs.fxml` | Report controllers | Direct `DatabaseHandler` | Report groups, report inputs/results |
| Smart Analysis | `AiCenter.fxml` | `AiCenterController` | `PfmisIntelligenceService` plus `DatabaseHandler` data context | Analysis result and evidence |
| Administration | `Administration.fxml` | `AdministrationController` | Direct `DatabaseHandler` | Health, backup, audit, maintenance summary |
| Data Maintenance | `DataRecordsMaintenance.fxml`, `RecordDisposal.fxml`, `DataMaintenanceWorkflow.fxml` | `DataRecordsSectionController`, `RecordDisposalController`, `DataMaintenanceWorkflowController` | Direct `DatabaseHandler`; no standalone maintenance service yet | Disposal table, clear/purge/reset/delete workflows |
| Audit Logs | `AuditLogs.fxml` | `AuditLogsController` | Direct `DatabaseHandler` | System and AI log tables |
| Users/Security | `UserManagement.fxml`, `SecurityHistory.fxml`, setup tabs | User/security controllers | `AuthDatabase`, `PrivilegedActionService`, `UserSession` | Users, workspace access, security history |

## Functional Retention Decision Record

| Entity / Business Meaning | Allowed Actions | Restrictions |
| --- | --- | --- |
| Accounts: places money is kept | Open, edit, reconcile, freeze, close, archive, soft delete, restore | Non-zero authoritative balance blocks deletion. Default/system/internal Savings Group accounts are protected. Historical transactions protect against purge, not soft delete. |
| Posted financial transactions: accounting history | Void/reverse by compensating record where supported; archive only as view/status if business-approved | Do not physically delete posted financial history through normal workflows. Do not rewrite history to force balances. |
| Transfers | Reverse/void as an atomic financial workflow | Both sides must remain balanced and relationally linked. |
| Budgets and budget items | Archive, restore, revise, soft delete if no active dependency | Historical spending and revisions must remain inspectable. |
| Projects and activities | Archive, restore, close, soft delete if allowed | Linked financial transactions and assets must remain preserved. |
| Loans and repayments | Close, archive, restore, reverse/void erroneous payment through controlled workflow | Borrowed and lent directions must remain distinct. Repayment history must not be destroyed casually. |
| Savings Groups, Bank Nkhonde, Chipeleganyu | Archive/close groups, restore where safe, record missed/paid states | Internal group ledgers and contribution history are protected financial records. |
| Assets and asset events | Archive, dispose/sell, transfer custody, restore where safe | Acquisition/disposal links to financial records remain preserved. |
| Configuration records: categories, currencies, payment methods, settings | Archive/disable, restore, permanent delete only when unused | Base currency and referenced configuration are protected. |
| Temporary/demo/import draft data | Reset, purge, permanent delete when clearly classified | Must not match real operational financial records by accident. |
| Audit/security records | View, export, governed retention cleanup only | Not available for normal maintenance deletion. |
| Backups/restore metadata | View, validate, governed cleanup | Backup files must not be overwritten or silently discarded. |

## Maintenance Architecture Decisions

| Decision | Rule |
| --- | --- |
| Central destructive operations | Data Maintenance remains the central administrative place for destructive bulk operations. Operational screens may expose safe lifecycle actions, but must not become uncontrolled delete centers. |
| Account deletion | Normal account deletion means soft delete, preserving identity and history. Permanent purge is separate and highly restricted. |
| Dependency inspection | Any destructive action must present dependency counts and eligibility before execution. |
| Authorization | UI hiding is not sufficient. Backend/service/database boundaries must enforce destructive-action permissions. Current code primarily checks `UserSession.isSuperAdmin`; a centralized RBAC permission model is still required. |
| Audit | Archive, restore, soft delete, permanent delete, reset and purge must create audit records. Audit records are protected from normal purge. |
| Transactionality | Multi-record destructive operations must run in one transaction, with rollback on failure. |
| User-readable failures | Expected integrity blocks must be translated into business messages; raw SQL exceptions belong in logs. |

## UI Workspace Decisions

| Decision | Rule |
| --- | --- |
| Main workspace | The main data/table/result/history gets available width and height. |
| Header/actions | Headers, filters and action bars remain compact. |
| Tables | Primary tables must grow vertically and preserve stable empty-state geometry. |
| Analysis/results | Smart Analysis, maintenance results, report previews and diagnostic outputs must be scrollable major workspaces. |
| Forms | Normal inputs remain compact; long notes/reasons/descriptions wrap and scroll. |
| Shared implementation | Use `WorkspaceLayoutSupport` and `TableActions`; do not add page-specific layout hacks unless a page has a unique structural issue. |

## Required Next Implementation Slices

1. Create a real PostgreSQL platform slice: dependencies, datasource, migrations, migration utility and explicit removal of SQLite runtime fallback.
2. Extract `DatabaseHandler` maintenance/destructive methods behind dedicated maintenance/application services.
3. Introduce centralized maintenance permissions and enforce them below the controller layer.
4. Move schema lifecycle out of controller/runtime table creation into versioned migrations.
5. Expand test coverage from source/FXML checks to service-level destructive workflows and runtime UI smoke checks.
6. Continue applying `WorkspaceLayoutSupport` through the active `DashboardController.loadView` path for all routed content, not only Data Records tabs.

