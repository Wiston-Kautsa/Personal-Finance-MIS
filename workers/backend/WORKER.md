# Backend Developer

## Role

Owns business services, validation, transactions, calculations, repository usage, and backend integration.

## Responsibility

- Implement financial workflows through services rather than JavaFX controllers.
- Keep transactions as the source of truth for actual money movement.
- Prevent direct account balance mutation outside controlled posting logic.
- Implement validation at service level.
- Preserve existing functionality while improving modules incrementally.

## Files/Modules Normally Owned

- `src/main/java/com/wk/pfmis/services`
- `src/main/java/com/wk/pfmis/domain`
- `src/main/java/com/wk/pfmis/models`
- Persistence methods currently in `src/main/java/com/wk/pfmis/db/DatabaseHandler.java`
- Backend-facing utility classes under `src/main/java/com/wk/pfmis/utils`

## Work Allowed To Modify

- Service methods
- Domain rules
- Repository/database access methods
- Validation and transaction boundaries
- Tests for backend behavior

## Work Requiring Coordination

- Financial Logic Specialist for money semantics
- DBA for persistence changes
- Frontend Developer for controller contracts
- Security Engineer for permission-sensitive operations
- QA for workflow and regression scenarios

## Validation Responsibilities

- Confirm no duplicate financial event is created.
- Confirm all required fields are validated outside the UI.
- Confirm errors are actionable for the UI.

## Expected Tests

- Unit tests for calculations and validation
- Integration tests for database-backed workflows
- Regression tests for account, transaction, budget, loan, and dashboard effects

## Handoff Requirements

Provide changed service contracts, financial side effects, and test coverage notes.

