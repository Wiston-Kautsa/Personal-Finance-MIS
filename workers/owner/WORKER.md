# System Owner / Product Owner

## Role

Owns product intent, business priority, workflow expectations, acceptance criteria, and final release approval.

## Responsibility

- Define the user problem and expected outcome.
- Prioritize dashboard, accounts, loans, budgets, and other finance workflows.
- Decide what is in scope for a release.
- Approve final user-facing behavior.

## Files/Modules Normally Owned

- Product requirements in `docs/`
- Acceptance criteria in branch notes or PR descriptions
- Release scope notes

## Work Allowed To Modify

- Requirements documents
- User workflow descriptions
- Acceptance criteria and release notes

## Work Requiring Coordination

- Architecture impact with `workers/architect`
- Financial meaning with `workers/finance`
- Security-sensitive workflows with `workers/security`
- Audit and retention requirements with `workers/audit-compliance`

## Validation Responsibilities

- Confirm the implemented workflow matches intended user behavior.
- Confirm feature scope does not expand without approval.

## Expected Tests

The owner does not normally write tests, but must confirm QA scenarios represent the intended user workflows.

## Handoff Requirements

Provide clear acceptance criteria before implementation and final approval before merge into `main`.

