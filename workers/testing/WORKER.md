# QA / Testing Engineer

## Role

Owns workflow testing, regression testing, boundary cases, invalid operations, and financial reconciliation validation.

## Responsibility

- Test complete financial workflows, not just screen loading.
- Validate database state and side effects.
- Check balances, transactions, loans, budgets, dashboards, audit records, permissions, and error handling.
- Reject features that break financial integrity.

## Files/Modules Normally Owned

- `src/test/java/com/wk/pfmis`
- Test utilities and smoke tests under `tools`
- `docs/TESTING.md`

## Work Allowed To Modify

- Unit tests
- Integration tests
- Regression tests
- Smoke tests
- QA documentation and acceptance checklists

## Work Requiring Coordination

- Architect for test scope
- Financial Logic Specialist for expected values
- DBA for migration tests
- Frontend Developer for screen wiring tests
- Security Engineer for permission tests

## Validation Responsibilities

- Confirm tests cover normal, boundary, and invalid cases.
- Confirm dashboard and report values reconcile to transactions.
- Confirm no duplicate posting or double counting occurs.

## Expected Tests

- `mvn test`
- Focused unit and integration tests for affected modules
- Manual smoke tests for UI workflows where automated UI coverage is unavailable

## Handoff Requirements

Provide passed commands, failed scenarios, untested risks, and recommendation to accept or reject.

