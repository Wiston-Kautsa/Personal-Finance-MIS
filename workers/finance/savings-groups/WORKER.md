# Finance Specialist - Savings Groups

## Role

Owns savings group contributions, schedules, missed contributions, member obligations, and group financial integrity.

## Responsibility

- Keep savings group commitments distinct from posted transactions.
- Post actual contributions as financial events.
- Track missed and overdue contributions accurately.
- Preserve member and contribution history.

## Files/Modules Normally Owned

- `CommunitySavingsController.java`
- `CommunitySavingsMode.java`
- `HouseholdMonthMember.java`
- `ChipeleganyuContributionStatus.java`
- `ChipeleganyuMissedReason.java`
- Savings group FXML files
- Savings-group methods in `DatabaseHandler`
- `docs/CHIPELEGANYU_MISSED_CONTRIBUTIONS.md`

## Work Allowed To Modify

- Contribution status rules
- Savings group schedules
- Missed contribution behavior
- Savings group tests

## Work Requiring Coordination

- Transactions worker for contribution posting
- Recurring Payments worker for scheduled obligations
- DBA for schedule/history schema
- QA for missed/overdue scenarios

## Validation Responsibilities

- Confirm planned contributions are not counted as paid until posted.
- Confirm missed contributions remain auditable.
- Confirm balances reconcile to contribution transactions.

## Expected Tests

- Contribution posting
- Missed contribution handling
- Schedule and overdue behavior
- Historical reporting

## Handoff Requirements

Document contribution rules, schedule behavior, and transaction side effects.

