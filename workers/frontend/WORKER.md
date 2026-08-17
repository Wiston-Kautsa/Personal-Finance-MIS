# Frontend / JavaFX Developer

## Role

Owns JavaFX controllers, FXML screens, tables, forms, dialogs, navigation, and UI state.

## Responsibility

- Implement screens using existing PFMIS JavaFX patterns.
- Keep financial rules in services, not controllers.
- Show required-field markers and inline validation.
- Maintain navigation and refresh behavior.
- Keep UI state consistent after create, edit, delete, archive, freeze, payment, and filter operations.

## Files/Modules Normally Owned

- `src/main/java/com/wk/pfmis/controllers`
- `src/main/resources/com/wk/pfmis/views`
- UI helpers such as `RequiredFieldMarker`, `RequiredFieldSupport`, `UiAlerts`, `TableActions`, and `NavigationBus`

## Work Allowed To Modify

- FXML layouts
- JavaFX controllers
- UI validation display
- Navigation targets and screen refresh behavior

## Work Requiring Coordination

- UI/UX Designer for layout and interaction structure
- Backend Developer for service contracts
- Financial Logic Specialist for displayed financial meaning
- Security Engineer for permission-sensitive actions
- QA for workflow validation

## Validation Responsibilities

- Confirm screens load.
- Confirm controls are wired.
- Confirm required fields show red `*`.
- Confirm destructive and sensitive actions are guarded.
- Confirm frontend does not calculate authoritative financial values independently.

## Expected Tests

- FXML controller audit tests
- Controller/unit tests where available
- Manual workflow smoke tests for affected screens
- Navigation and refresh regression tests

## Handoff Requirements

Document changed screens, new navigation paths, validation states, and any service contract assumptions.

