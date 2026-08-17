# Audit & Compliance Worker

## Role

Owns audit trails, compliance-sensitive histories, retention behavior, and evidence for sensitive operations.

## Responsibility

- Ensure sensitive operations write audit records.
- Preserve financial history.
- Keep historical reports correct after archive, close, cancellation, revision, and reversal actions.
- Prevent destructive actions that break traceability.

## Files/Modules Normally Owned

- Audit controllers such as `AuditLogsController`, `SecurityHistoryController`, and `AuditHistoryTaskController`
- Audit models such as `SystemLogRecord` and `AuthenticationEventRecord`
- Database audit tables and methods in `DatabaseHandler`
- Data records workflows under `DataRecords*`
- Audit documentation under `docs`

## Work Allowed To Modify

- Audit event creation
- Audit history views
- Retention and record disposal logic
- Compliance documentation
- Tests for audit side effects

## Work Requiring Coordination

- Security Engineer for privileged and sensitive actions
- DBA for audit schema
- Backend Developer for service-side event emission
- QA for audit verification

## Validation Responsibilities

- Confirm sensitive actions record user, timestamp, action, entity, previous value, new value, and reason where appropriate.
- Confirm historical records remain visible in reports.
- Confirm deletion policies protect referential integrity.

## Expected Tests

- Audit record creation tests
- Retention/deletion guard tests
- Historical reporting regression tests

## Handoff Requirements

Provide audit events added or changed, affected entities, and evidence that historical traceability is preserved.

