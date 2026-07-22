# PFMIS Gap Fix Progress - 2026-07-22

## Completed In This Pass

1. Cancelled transactions are now excluded from dashboard/report totals that previously summed cancelled records.
   - Monthly income and expense totals.
   - Dashboard loan position.
   - Category spending reports.
   - Income source reports.
   - Category/account reports.
   - Project spending reports.
   - Lending/borrowing by person reports.

2. A `valid_transactions` SQLite view was added for centralized non-cancelled transaction reporting.

3. Database location is now stable per operating system and separated by authenticated user.
   - Central user registry: `<PFMIS app-data>/pfmis-auth.db`
   - Financial database: `<PFMIS app-data>/users/<user-id>/pfmis.db`
   - Existing local `pfmis.db` is copied into the first Super Administrator's workspace during initial setup.

4. The application lock file now uses the same stable app-data directory.

5. Additive schema management was added.
   - `schema_version`
   - `backup_history`
   - `budgets`
   - transaction/project/budget indexes

6. Project spending was corrected to use non-cancelled transactions.

7. Project activity actual amounts no longer substitute planned cost when actual usage is zero.
   - Linked expense transactions are used when present.
   - Otherwise the stored `amount_used` value is used.

8. Backup / Restore is now a real module.
   - SQLite snapshot backup via `VACUUM INTO`
   - SHA-256 checksum sidecar file
   - backup integrity validation
   - automatic pre-restore backup
   - backup history table
   - JavaFX file/folder picker screen

9. Built-in local AI lifecycle was hardened.
   - Per-session random local API token.
   - `llama-server` starts with `--api-key`.
   - PFMIS sends `Authorization: Bearer <token>`.
   - Cross-platform executable name resolution.
   - `BundledLocalAiManager.shutdown()` is called from `MainApp.stop()`.

10. Budgets module was added.
    - Budget model.
    - Budget progress model.
    - Database-backed CRUD.
    - Spending-vs-limit calculations from non-cancelled expenses.
    - JavaFX budget registration and tracking screen.
    - Dashboard navigation under Planning.

## Verified

- Full Java source compilation passed with JDK 25 and JavaFX 21.0.5 jars.
- New screen smoke test loaded:
  - `Budgets.fxml`
  - `BackupRestore.fxml`
  - `Dashboard.fxml`
- Existing full FXML smoke test reached its success marker.

## Still Open From The Gap Analysis

- BigDecimal or integer-minor-unit money migration.
- Full multi-currency conversion system.
- Goal allocation/contribution table to prevent double-counting savings across goals.
- Database-at-rest encryption and automatic inactivity locking. User authentication, sign-in, private workspaces, and Super Administrator access control are now implemented.
- Soft deletion and audit log.
- Dedicated payment-method database/controller.
- Dedicated currency database/controller.
- Report PDF/Excel/print output.
- Person-specific loan statements.
- Loan restructuring into loan, disbursement, repayment, schedule, and charge tables.
- AI tool/function registry and structured JSON response contract.
- External-AI privacy consent, prompt preview, and request audit log.
- Runtime/model checksum verification before executing local AI binaries.
- jpackage installer.
