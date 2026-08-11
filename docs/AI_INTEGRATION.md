# PFMIS AI Integration

PFMIS supports local AI processing first. External AI providers are optional and must be treated as privacy-sensitive.

## Local AI

The bundled local runtime should:

- bind only to `127.0.0.1`
- choose an available local port
- use a random runtime API key
- stop on logout and application shutdown
- avoid logging the API key
- fail safely when the executable or model is unavailable

The application should clearly show when local AI is being used.

## External AI

Before sending financial data to an external provider, the UI should show a privacy warning and give the user a redaction option.

Redaction should cover:

- account numbers
- names
- notes
- personally identifiable information
- references that identify private financial activity

External API keys must be stored in the operating system credential manager or protected through platform-specific secure storage. Plaintext configuration files are not acceptable.

## Starter-Pack Definitions

Agent and extension descriptors should have one authoritative naming convention and should be stored under application resources when they are loaded at runtime.

The target validation model should include:

- agent name
- version
- description
- system instructions
- supported tasks
- allowed tools
- prohibited actions
- required data permissions
- privacy classification
- provider compatibility
- enabled state

## Current Limitations

- Starter-pack naming reconciliation is still pending.
- A complete descriptor schema is still pending.
- External-provider secure key storage is not complete.
