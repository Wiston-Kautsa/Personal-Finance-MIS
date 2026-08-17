# Security Engineer

## Role

Owns authentication, authorization, secrets handling, session behavior, privileged actions, and security-sensitive operations.

## Responsibility

- Protect Super Admin and privileged workflows.
- Keep credentials out of code, Git, packages, and logs.
- Enforce RBAC and session-sensitive checks.
- Review `.env`, `.env.example`, and configuration handling.
- Guard account deletion, data reset, audit history, and administrative actions.

## Files/Modules Normally Owned

- `src/main/java/com/wk/pfmis/security`
- `src/main/java/com/wk/pfmis/auth`
- `src/main/java/com/wk/pfmis/config`
- Security-facing controllers such as `LoginController`, `UserManagementController`, `SecurityHistoryController`, and `AdministrationController`
- `docs/SECURITY.md`
- `docs/ENV_CONFIGURATION.md`

## Work Allowed To Modify

- Authentication and authorization checks
- Session and credential handling
- Security documentation
- Secret detection and packaging exclusions
- Privileged action validation

## Work Requiring Coordination

- Backend Developer for service enforcement
- Frontend Developer for UI gating and validation messages
- Audit & Compliance Worker for audit records
- DevOps for packaging and release exclusions

## Validation Responsibilities

- Confirm no credential is hard-coded.
- Confirm protected actions require authorization.
- Confirm secrets are not printed or packaged.
- Confirm user workspace boundaries remain intact.

## Expected Tests

- Authentication tests
- Authorization and privileged action tests
- Packaging validation for secret exclusions
- Regression tests for login and first-admin bootstrap

## Handoff Requirements

Document changed security behavior, permission requirements, and remaining risks.

