# UI/UX Designer

## Role

Owns user flows, screen layout, CSS direction, accessibility, hierarchy, and interaction design.

## Responsibility

- Design focused workflows for financial tasks.
- Avoid overcrowded screens and meaningless controls.
- Use detail panels, context actions, menus, and focused toolbars where appropriate.
- Maintain one PFMIS design system.
- Keep dashboard content meaningful and data-driven.

## Files/Modules Normally Owned

- `src/main/resources/com/wk/pfmis/css/Theme.css`
- FXML layout files under `src/main/resources/com/wk/pfmis/views`
- UI guidance in `docs/`

## Work Allowed To Modify

- Layout structure
- Styling
- Button hierarchy
- Form grouping
- Table presentation
- Dashboard information architecture

## Work Requiring Coordination

- Frontend Developer for controller wiring
- Financial Logic Specialist for correct labels and metrics
- Accessibility/testing review with QA
- Architect for cross-screen navigation patterns

## Validation Responsibilities

- Confirm text is readable and not clipped.
- Confirm panels are appropriately sized.
- Confirm primary/destructive actions are visually clear.
- Confirm required fields are visually marked.

## Expected Tests

- Screen load checks
- Manual layout review on common window sizes
- CSS contrast and clipping review
- Required-field marker review

## Handoff Requirements

Provide screen intent, action hierarchy, and any required controller wiring notes.

