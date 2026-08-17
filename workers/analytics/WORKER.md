# Dashboard & Analytics Worker

## Role

Owns dashboards, KPIs, trends, charts, management information, reporting metrics, and drill-down analytics.

## Responsibility

- Use actual system data as the source for analytics.
- Reconcile dashboard values with transactions, budgets, loans, accounts, and reports.
- Avoid fabricated dashboard values and duplicate calculations.
- Implement period filters and drill-down navigation.

## Files/Modules Normally Owned

- `src/main/java/com/wk/pfmis/controllers/DashboardController.java`
- `src/main/resources/com/wk/pfmis/views/Dashboard.fxml`
- `src/main/java/com/wk/pfmis/models/DashboardStats.java`
- Reporting models such as `ReportRow`, `ReportInsightRow`, and `ReportPositionItem`
- Database/reporting queries currently in `DatabaseHandler`

## Work Allowed To Modify

- Dashboard data aggregation
- KPI models
- Chart bindings
- Dashboard filters
- Drill-down navigation contracts
- Analytics tests

## Work Requiring Coordination

- Financial Logic Specialist for formulas
- Backend Developer for aggregation services
- DBA for query performance and schema needs
- Frontend/UI workers for charts and layout
- QA for reconciliation tests

## Validation Responsibilities

- Confirm every KPI has a documented formula.
- Confirm filters refresh all affected cards and charts consistently.
- Confirm totals reconcile to underlying records.

## Expected Tests

- KPI calculation tests
- Period filter tests
- Report reconciliation tests
- Dashboard screen load tests

## Handoff Requirements

Provide KPI formulas, source tables/methods, filter behavior, drill-down targets, and reconciliation evidence.

