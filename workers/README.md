# PFMIS Worker Definitions

Workers are permanent responsibility definitions. They are not Git branches.

Use workers to decide who owns technical responsibility. Use short-lived Git branches to isolate the current task.

```text
Worker = Responsibility
Branch = Current Task
Main = Approved System
```

## Worker Index

- [System Owner / Product Owner](owner/WORKER.md)
- [Technical Lead / Architect](architect/WORKER.md)
- [Database Administrator](database-admin/WORKER.md)
- [Backend Developer](backend/WORKER.md)
- [Frontend / JavaFX Developer](frontend/WORKER.md)
- [UI/UX Designer](ui-ux/WORKER.md)
- [Security Engineer](security/WORKER.md)
- [QA / Testing Engineer](testing/WORKER.md)
- [Code Review / Integration Worker](integration-review/WORKER.md)
- [DevOps / Release Worker](devops/WORKER.md)
- [Documentation Worker](documentation/WORKER.md)
- [Integrations Worker](integrations/WORKER.md)
- [Dashboard & Analytics Worker](analytics/WORKER.md)
- [AI Engine Worker](ai-engine/WORKER.md)
- [Audit & Compliance Worker](audit-compliance/WORKER.md)
- [Financial Logic Specialist](finance/WORKER.md)

Finance specialist sub-workers:

- [Accounts](finance/accounts/WORKER.md)
- [Transactions](finance/transactions/WORKER.md)
- [Income & Expenses](finance/income-expenses/WORKER.md)
- [Budgets](finance/budgets/WORKER.md)
- [Loans](finance/loans/WORKER.md)
- [Projects](finance/projects/WORKER.md)
- [Savings Groups](finance/savings-groups/WORKER.md)
- [Recurring Payments](finance/recurring-payments/WORKER.md)
- [Assets](finance/assets/WORKER.md)
- [Reconciliation](finance/reconciliation/WORKER.md)

## Usage

For a task branch, list the participating workers in the branch kickoff note or PR description. Example:

```text
Branch: feature/loan-workflow-redesign
Workers: Architect, Loans Financial Specialist, DBA, Backend, Frontend, UI/UX, Analytics, QA, Integration
```

Each worker must read its `WORKER.md`, inspect the existing implementation, inspect upstream worker commits, make only scoped changes, run relevant validation, commit any files it changed, push the task branch, and document handoff notes.

See `docs/PFMIS_MULTI_WORKER_WORKFLOW.md` for the Git workflow and branch rules.

## Required Worker File Sections

Each `WORKER.md` must keep these sections:

- `## Role`
- `## Responsibility`
- `## Files/Modules Normally Owned`
- `## Work Allowed To Modify`
- `## Work Requiring Coordination`
- `## Validation Responsibilities`
- `## Expected Tests`
- `## Handoff Requirements`
