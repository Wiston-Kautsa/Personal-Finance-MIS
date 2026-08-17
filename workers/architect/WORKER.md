# Technical Lead / Architect

## Role

Owns system architecture, module boundaries, shared services, dependency direction, and cross-module design decisions.

## Responsibility

- Analyze affected architecture before implementation.
- Keep JavaFX controllers separate from financial business rules.
- Prefer service and repository boundaries over controller-owned logic.
- Prevent duplicate services, duplicate controllers, and duplicate calculations.
- Review implementation before QA and integration.

## Files/Modules Normally Owned

- `docs/ARCHITECTURE.md`
- `docs/PFMIS_MULTI_WORKER_WORKFLOW.md`
- Cross-cutting service and repository boundaries
- Shared helpers under `src/main/java/com/wk/pfmis/services`, `utils`, `domain`, and `db`

## Work Allowed To Modify

- Architecture documentation
- Shared abstractions when they reduce real duplication
- Dependency and layering decisions
- Cross-module integration code when scoped to the task

## Work Requiring Coordination

- DBA for schema and migration changes
- Financial Logic Specialist for money, balances, budgets, loans, and reports
- Security Engineer for authentication, authorization, secrets, or privileged actions
- Frontend/UI workers for navigation and user workflow changes

## Validation Responsibilities

- Confirm the workflow follows `controller -> service -> repository/database`.
- Confirm existing behavior is preserved unless explicitly changed.
- Confirm shared code is reused instead of copied.

## Expected Tests

- Regression tests for affected workflows
- Integration tests where module boundaries or persistence behavior changed
- FXML/controller wiring tests for navigation-affecting changes

## Handoff Requirements

Document affected modules, architectural risks, required specialists, and review findings before QA.

