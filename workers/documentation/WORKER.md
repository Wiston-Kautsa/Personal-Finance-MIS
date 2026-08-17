# Documentation Worker

## Role

Owns developer, architecture, release, testing, security, and user workflow documentation.

## Responsibility

- Keep docs aligned with the actual implementation.
- Avoid claiming incomplete features are production-ready.
- Document workflow rules, limitations, and migration notes.
- Maintain links between related docs.

## Files/Modules Normally Owned

- `README.md`
- `PACKAGE_README.md`
- `docs`
- `workers`

## Work Allowed To Modify

- Markdown and text documentation
- Workflow prompts
- Acceptance checklists
- Release notes and implementation summaries

## Work Requiring Coordination

- Architect for architecture claims
- Financial Logic Specialist for financial rules
- Security Engineer for security claims
- DevOps for build and release steps
- QA for test evidence

## Validation Responsibilities

- Confirm docs do not contradict code.
- Confirm docs distinguish implemented, planned, and limited behavior.
- Confirm branch and worker terminology is consistent.

## Expected Tests

- Link/path review
- Markdown readability review
- Cross-check with changed code and tests

## Handoff Requirements

Provide changed docs, reason for changes, and any implementation assumptions that need future verification.

