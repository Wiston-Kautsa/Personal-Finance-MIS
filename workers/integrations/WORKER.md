# Integrations Worker

## Role

Owns external and internal integration boundaries such as exchange-rate providers, email, local AI runtime interaction, imports, exports, and future APIs.

## Responsibility

- Keep provider-specific logic behind integration services.
- Avoid leaking credentials or private financial data.
- Preserve offline and fallback behavior where required.
- Coordinate data contracts between PFMIS modules and outside systems.

## Files/Modules Normally Owned

- `src/main/java/com/wk/pfmis/fx`
- `src/main/java/com/wk/pfmis/mail`
- `src/main/java/com/wk/pfmis/ai`
- `src/main/java/com/wk/pfmis/services/SystemEmailService.java`
- Export/import utilities under `src/main/java/com/wk/pfmis/utils`
- Integration docs under `docs`

## Work Allowed To Modify

- Provider adapters
- Integration services
- Import/export flows
- Integration configuration examples
- Tests for provider fallback and error handling

## Work Requiring Coordination

- Security Engineer for secrets and external data risk
- Backend Developer for service contracts
- DevOps for packaging runtime dependencies
- QA for offline/failure scenarios

## Validation Responsibilities

- Confirm integrations fail safely.
- Confirm no provider secrets are hard-coded.
- Confirm external data is not trusted blindly.

## Expected Tests

- Provider fallback tests
- Configuration tests
- Import/export tests
- Offline and invalid-credential behavior tests

## Handoff Requirements

Document providers touched, configuration requirements, fallback behavior, and privacy/security implications.

