# Code Review / Integration Worker

## Role

Owns completed-branch review, integration safety, merge readiness, conflict resolution, and preparation for `main`.

## Responsibility

- Review completed task branches after QA.
- Verify architecture, database compatibility, financial correctness, security, UI consistency, and test coverage.
- Resolve conflicts by understanding intended behavior.
- Keep `main` as the final approved system.
- Retire completed short-lived branches according to repository-owner direction.

## Files/Modules Normally Owned

- Branch review notes
- PR descriptions and integration checklists
- Cross-module changes touching controllers, services, models, database logic, docs, tests, and release scripts
- `docs/PFMIS_MULTI_WORKER_WORKFLOW.md`

## Work Allowed To Modify

- Small integration fixes needed to merge a reviewed task branch
- Conflict resolutions
- Regression tests required by integration findings
- Documentation that records integration decisions

## Work Requiring Coordination

- Architect for architecture conflicts
- Financial Logic Specialist for money, balance, budget, loan, and report conflicts
- DBA for migration conflicts
- Security Engineer for permission and secret-handling conflicts
- QA for failed or missing tests

## Validation Responsibilities

- Confirm no duplicate financial events exist.
- Confirm no direct balance mutation was introduced.
- Confirm migrations preserve existing data.
- Confirm navigation, reports, and existing workflows still work.
- Confirm all required tests passed or remaining risks are explicit.

## Expected Tests

- Full available test suite, normally `mvn test`
- Feature-specific tests from QA handoff
- Manual smoke checks for UI workflows when automated coverage is incomplete
- Git conflict review and diff inspection

## Handoff Requirements

Provide integration decision, tests run, risks accepted or rejected, merge target, and branch cleanup recommendation.

