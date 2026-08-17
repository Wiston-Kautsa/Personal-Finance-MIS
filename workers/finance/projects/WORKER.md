# Finance Specialist - Projects

## Role

Owns project financial meaning, project budgets, activities, milestones, income/expense links, and project reporting.

## Responsibility

- Keep project activity separate from actual financial transactions unless a transaction is posted.
- Link project finances to transactions where applicable.
- Preserve project history and lifecycle events.
- Ensure project reports reconcile with linked financial records.

## Files/Modules Normally Owned

- `Project.java`
- `ProjectActivity.java`
- `ProjectMilestone.java`
- `ProjectsController.java`
- `ProjectFinancesController.java`
- `ProjectOverviewController.java`
- `ProjectActivitiesController.java`
- Project FXML files
- Project methods in `DatabaseHandler`

## Work Allowed To Modify

- Project financial rules
- Project transaction links
- Project status/lifecycle behavior
- Project report tests

## Work Requiring Coordination

- Budgets worker for optional project budgets
- Transactions worker for project-linked financial activity
- Assets worker when completed projects create assets through formal recognition
- Analytics worker for project metrics

## Validation Responsibilities

- Confirm planned project costs are not counted as actual expenses.
- Confirm project financial totals reconcile to linked records.
- Confirm history remains available after lifecycle transitions.

## Expected Tests

- Project creation and lifecycle
- Linked transactions
- Project financial totals
- Project reporting regression tests

## Handoff Requirements

Document project finance rules, links to budgets/transactions/assets, and reporting behavior.

