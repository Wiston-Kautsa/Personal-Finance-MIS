# Finance Specialist - Assets

## Role

Owns asset recognition, valuation, maintenance, transfer/custody, sale/disposal, and asset financial history.

## Responsibility

- Recognize assets only through formal asset workflows.
- Do not create assets automatically from budgets or projects without recognition checks.
- Preserve asset event history.
- Keep asset valuation and disposal financially traceable.

## Files/Modules Normally Owned

- `Asset.java`
- `AssetEvent.java`
- Asset controllers under `src/main/java/com/wk/pfmis/controllers`
- Asset FXML files under `src/main/resources/com/wk/pfmis/views`
- Asset methods in `DatabaseHandler`
- `docs/ASSET_RECOGNITION.md`

## Work Allowed To Modify

- Asset lifecycle rules
- Asset event recording
- Asset valuation and disposal logic
- Asset tests

## Work Requiring Coordination

- Transactions worker for sale/disposal financial events
- Projects worker for project-to-asset recognition checks
- DBA for asset constraints and history tables
- Audit worker for lifecycle events

## Validation Responsibilities

- Confirm asset records remain traceable.
- Confirm valuation changes are not confused with cash transactions.
- Confirm disposal/sale events reconcile where financial movement occurs.

## Expected Tests

- Asset recognition
- Maintenance and valuation
- Transfer/custody
- Sale/disposal
- Asset history

## Handoff Requirements

Document asset event rules, financial side effects, and audit/reporting impact.

