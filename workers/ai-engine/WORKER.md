# AI Engine Worker

## Role

Owns bundled local AI behavior, external AI configuration, prompt routing, privacy controls, and AI runtime diagnostics.

## Responsibility

- Prefer local AI where runtime and model are available.
- Treat external AI providers as privacy-sensitive.
- Keep AI prompts grounded in existing PFMIS data and permissions.
- Avoid sending secrets or unnecessary personal financial data externally.
- Maintain AI startup diagnostics and fallback behavior.

## Files/Modules Normally Owned

- `src/main/java/com/wk/pfmis/ai`
- `src/main/java/com/wk/pfmis/controllers/AiCenterController.java`
- `local-ai`
- `docs/AI_INTEGRATION.md`
- `docs/ASSET_RECOGNITION.md`

## Work Allowed To Modify

- AI settings and runtime checks
- Local AI integration
- External AI provider routing
- AI prompt templates where present
- AI privacy documentation

## Work Requiring Coordination

- Security Engineer for privacy and secrets
- Integrations Worker for provider boundaries
- Backend Developer for data extraction contracts
- QA for offline and fallback behavior

## Validation Responsibilities

- Confirm no private data is sent externally without user-visible risk.
- Confirm local AI fallback remains clear.
- Confirm missing model/runtime state does not break application startup.

## Expected Tests

- AI settings tests
- Runtime diagnostics tests
- External-provider disabled/fallback tests
- Privacy and configuration review

## Handoff Requirements

Document provider behavior, local runtime expectations, privacy implications, and fallback outcomes.

